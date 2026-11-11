package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.TreeAssessmentProtocol
import com.lifeskill.app.model.AvatarPreset
import com.lifeskill.app.model.CertificateState
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.CertificateTheme
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.EXAM_PASS_SCORE
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.validatedNickname
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.OffsetDateTime

internal class CertificateRepository(
    private val database: LifeSkillDatabase,
    private val library: LibraryRepository,
    private val profiles: ProfileRepository,
    private val secret: SecretBox,
) {
    private val dao = database.personal()
    private val assessments = database.assessments()
    val certificates = dao.watchCertificates()
        .map { rows -> rows.mapNotNull { it.state().issued }.sortedBy { it.isSample } }
        .flowOn(Dispatchers.IO)

    fun watchCertificate(treeId: String) = dao.watchCertificate(treeId)
        .map { it?.state() ?: CertificateState() }
        .flowOn(Dispatchers.IO)

    suspend fun prepareArchive() = database.withTransaction { prepareArchiveInTransaction() }

    suspend fun setHidden(treeId: String, hidden: Boolean) = database.withTransaction {
        val row = dao.certificate(treeId) ?: throw RuleViolation("这张证书已不存在。")
        val payload = row.payload()
        if (payload.optBoolean("destroyed")) throw RuleViolation("这张证书已销毁。")
        if (payload.optBoolean("hidden") != hidden) savePayload(row, payload.put("hidden", hidden))
    }

    suspend fun destroy(treeId: String) = database.withTransaction {
        val row = dao.certificate(treeId) ?: throw RuleViolation("这张证书已不存在。")
        // 覆盖签发快照，仅保留销毁标记，防止重考后重新签发或启动时重新放入示例。
        if (!row.payload().optBoolean("destroyed")) savePayload(row, JSONObject().put("destroyed", true))
    }

    suspend fun issue(treeId: String, attemptId: String, enteredNickname: String? = null) = database.withTransaction {
        prepareArchiveInTransaction()
        val attempt = assessments.attempt(treeId, attemptId) ?: throw RuleViolation("本次考试已不存在。")
        if ((attempt.score ?: 0) < EXAM_PASS_SCORE) throw RuleViolation("通过验收考试后可领取结业证书。")
        dao.certificate(treeId)?.let { existing ->
            if (existing.payload().optBoolean("destroyed")) throw RuleViolation("这张证书已销毁。")
            return@withTransaction
        }
        val tree = library.entry(treeId) ?: throw RuleViolation("技能树已不存在。")
        val profile = profiles.current()
        val recipient = if (enteredNickname == null) profile.nickname ?: throw RuleViolation("请填写证书署名。")
            else checkNotNull(validatedNickname(enteredNickname, required = true))
        if (recipient != profile.nickname) profiles.save(profile.copy(nickname = recipient))
        dao.insertCertificate(CollectedCertificate(treeId, tree.name, CertificateStyle.forTree(tree), recipient,
            profile.avatar, collectedAt = OffsetDateTime.now()).encode())
    }

    private suspend fun prepareArchiveInTransaction() {
        importLegacyInTransaction()
        backfillAvatarsInTransaction()
        if (dao.certificate(SAMPLE_CERTIFICATE_ID) == null) {
            dao.insertCertificate(CollectedCertificate(
                SAMPLE_CERTIFICATE_ID, "学习方法入门", CertificateStyle(CertificateTheme.BUSINESS, EntryIcon.BOOK, 0),
                "示例学员", ProfileAvatar.Preset(AvatarPreset.CAT), collectedAt = OffsetDateTime.now(), isSample = true,
            ).encode())
        } else {
            dao.certificate(SAMPLE_CERTIFICATE_ID)?.let { sample ->
                val payload = sample.payload()
                // 已安装版本的示例证书可能没有日期，补入后保持为固定的展示日期。
                if (!payload.optBoolean("destroyed") && payload.optString("collectedAt").isEmpty()) {
                    savePayload(sample, payload.put("collectedAt", OffsetDateTime.now().toString()))
                }
            }
        }
    }

    private suspend fun backfillAvatarsInTransaction() {
        val bootstrap = database.skills()
        if (bootstrap.initialized(CERTIFICATE_RECIPIENTS_ID)) return
        val rows = dao.certificates()
        if (rows.isNotEmpty()) {
            val profile = profiles.current()
            // 旧档案缺少头像。用升级时的本地资料补齐，已签发的昵称与入库顺序保持不变。
            for (row in rows) {
                val payload = row.payload()
                if (!payload.optBoolean("destroyed") && !payload.has("avatar")) {
                    payload.put("avatar", JSONObject().putAvatar(profile.avatar))
                    if (payload.optString("recipient").isBlank()) payload.put("recipient", profile.displayName)
                    savePayload(row, payload)
                }
            }
        }
        bootstrap.insertBootstrap(BootstrapRow(CERTIFICATE_RECIPIENTS_ID))
    }

    private suspend fun importLegacyInTransaction() {
        val bootstrap = database.skills()
        if (bootstrap.initialized(CERTIFICATE_ARCHIVE_ID)) return
        // 旧版只在试卷里记录领取状态。按试卷顺序去重，优先保留已经冻结的样式。
        val receipts = linkedMapOf<String, CertificateStyle?>()
        for (row in assessments.passedAttempts(EXAM_PASS_SCORE)) {
            val attempt = TreeAssessmentProtocol.decodeAttempt(row.id, secret.open(row.content, "${row.id}:exam"))
            if (attempt.certificateCollected && receipts[row.treeId] == null) receipts[row.treeId] = attempt.certificateStyle
        }
        if (receipts.isNotEmpty()) {
            val profile = profiles.current()
            for ((treeId, savedStyle) in receipts) {
                val tree = checkNotNull(library.entry(treeId))
                val style = savedStyle ?: CertificateStyle.forTree(tree)
                dao.insertCertificate(CollectedCertificate(treeId, tree.name, style, profile.displayName, profile.avatar).encode())
            }
        }
        bootstrap.insertBootstrap(BootstrapRow(CERTIFICATE_ARCHIVE_ID))
    }

    private fun CollectedCertificate.encode(): CollectedCertificateRow {
        val json = JSONObject().put("name", treeName).put("style", style.id).put("recipient", recipient)
            .put("avatar", JSONObject().putAvatar(avatar))
            .put("collectedAt", collectedAt?.toString())
        return CollectedCertificateRow(treeId = treeId, content = secret.seal(json.toString(), "$treeId:certificate"))
    }

    private fun CollectedCertificateRow.state(): CertificateState {
        val json = payload()
        if (json.optBoolean("destroyed")) return CertificateState(destroyed = true)
        return CertificateState(issued = CollectedCertificate(treeId, json.getString("name"),
            checkNotNull(CertificateStyle.fromId(json.getString("style"))),
            json.getString("recipient"), json.getJSONObject("avatar").readAvatar(), json.optBoolean("hidden"),
            collectedAt = json.optString("collectedAt").takeIf(String::isNotEmpty)?.let(OffsetDateTime::parse),
            isSample = treeId == SAMPLE_CERTIFICATE_ID,
        ))
    }

    private fun CollectedCertificateRow.payload() = JSONObject(secret.open(content, "$treeId:certificate"))

    private suspend fun savePayload(row: CollectedCertificateRow, payload: JSONObject) =
        dao.updateCertificate(row.copy(content = secret.seal(payload.toString(), "${row.treeId}:certificate")))

    private companion object {
        const val CERTIFICATE_ARCHIVE_ID = 2
        const val CERTIFICATE_RECIPIENTS_ID = 3
        const val SAMPLE_CERTIFICATE_ID = "lifeskill:certificate-sample"
    }
}
