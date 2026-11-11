package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.TreeAssessmentProtocol
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.EXAM_PASS_SCORE
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.validatedNickname
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

internal class CertificateRepository(
    private val database: LifeSkillDatabase,
    private val library: LibraryRepository,
    private val profiles: ProfileRepository,
    private val secret: SecretBox,
) {
    private val dao = database.personal()
    private val assessments = database.assessments()
    val certificates = dao.watchCertificates().map { rows -> rows.map { it.decode() } }.flowOn(Dispatchers.IO)

    fun watchCertificate(treeId: String) = dao.watchCertificate(treeId).map { it?.decode() }.flowOn(Dispatchers.IO)

    suspend fun importLegacy() = database.withTransaction { importLegacyInTransaction() }

    suspend fun issue(treeId: String, attemptId: String, enteredNickname: String? = null) = database.withTransaction {
        importLegacyInTransaction()
        val attempt = assessments.attempt(treeId, attemptId) ?: throw RuleViolation("本次考试已不存在。")
        if ((attempt.score ?: 0) < EXAM_PASS_SCORE) throw RuleViolation("通过验收考试后可领取结业证书。")
        if (dao.certificate(treeId) != null) return@withTransaction
        val tree = library.entry(treeId) ?: throw RuleViolation("技能树已不存在。")
        val profile = profiles.current()
        val recipient = if (enteredNickname == null) profile.nickname ?: throw RuleViolation("请填写证书署名。")
            else checkNotNull(validatedNickname(enteredNickname, required = true))
        if (recipient != profile.nickname) profiles.save(profile.copy(nickname = recipient))
        dao.insertCertificate(CollectedCertificate(treeId, tree.name, CertificateStyle.forTree(tree), recipient).encode())
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
        for ((treeId, savedStyle) in receipts) {
            val tree = checkNotNull(library.entry(treeId))
            val style = savedStyle ?: CertificateStyle.forTree(tree)
            dao.insertCertificate(CollectedCertificate(treeId, tree.name, style, recipient = null).encode())
        }
        bootstrap.insertBootstrap(BootstrapRow(CERTIFICATE_ARCHIVE_ID))
    }

    private fun CollectedCertificate.encode(): CollectedCertificateRow {
        val json = JSONObject().put("name", treeName).put("style", style.id).put("recipient", recipient)
        return CollectedCertificateRow(treeId = treeId, content = secret.seal(json.toString(), "$treeId:certificate"))
    }

    private fun CollectedCertificateRow.decode(): CollectedCertificate {
        val json = JSONObject(secret.open(content, "$treeId:certificate"))
        return CollectedCertificate(treeId, json.getString("name"),
            checkNotNull(CertificateStyle.fromId(json.getString("style"))),
            json.optString("recipient").takeIf(String::isNotEmpty))
    }

    private companion object {
        const val CERTIFICATE_ARCHIVE_ID = 2
    }
}
