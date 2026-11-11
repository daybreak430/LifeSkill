package com.lifeskill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeskill.app.data.CertificateRepository
import com.lifeskill.app.data.ProfileRepository
import com.lifeskill.app.data.TreeAssessmentRepository
import com.lifeskill.app.model.EXAM_ANSWER_LIMIT
import com.lifeskill.app.model.ExamAnswer
import com.lifeskill.app.model.ExamAttempt
import com.lifeskill.app.model.CertificateState
import com.lifeskill.app.model.PersonalProfile
import com.lifeskill.app.model.TreeChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class AssessmentStatus(val busy: Boolean = false, val error: String? = null)
internal data class ExamDraft(val attemptId: String, val answers: List<ExamAnswer>)
internal data class ExamCertificateContext(val state: CertificateState, val profile: PersonalProfile)

internal class TreeAssessmentViewModel(
    private val repository: TreeAssessmentRepository,
    private val treeId: String,
    private val certificates: CertificateRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private val _examStatus = MutableStateFlow(AssessmentStatus())
    val examStatus = _examStatus.asStateFlow()
    private val _examDraft = MutableStateFlow<ExamDraft?>(null)
    val examDraft = _examDraft.asStateFlow()
    val attempts = repository.watchAttempts(treeId)
        .catch { error ->
            if (error is Exception) _examStatus.value = AssessmentStatus(error = readable(error, "无法读取验收考试。"))
            else throw error
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val certificate = combine(certificates.watchCertificate(treeId), profiles.profile, ::ExamCertificateContext)
        .catch { error ->
            if (error is Exception) _examStatus.value = AssessmentStatus(error = readable(error, "无法读取结业证书。"))
            else throw error
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val challengeOperations = TreeChallenge.entries.associateWith { MutableStateFlow(AssessmentStatus()) }
    private val challengeSessions = TreeChallenge.entries.associateWith { kind ->
        repository.watchChallenge(treeId, kind)
            .catch { error ->
                if (error is Exception) challengeOperations.getValue(kind).value =
                    AssessmentStatus(error = readable(error, "无法读取挑战。"))
                else throw error
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
    private val _challengeDrafts = MutableStateFlow<Map<TreeChallenge, String>>(emptyMap())
    val challengeDrafts = _challengeDrafts.asStateFlow()

    fun challenge(kind: TreeChallenge) = challengeSessions.getValue(kind)
    fun challengeStatus(kind: TreeChallenge) = challengeOperations.getValue(kind).asStateFlow()

    fun startExam() = run(_examStatus) { repository.startExam(treeId) }

    fun updateAnswer(attempt: ExamAttempt, index: Int, answer: ExamAnswer) {
        if (_examStatus.value.busy) return
        val draft = answersFor(attempt).toMutableList()
        draft[index] = answer.copy(text = answer.text.take(EXAM_ANSWER_LIMIT))
        _examDraft.value = ExamDraft(attempt.id, draft)
    }

    fun submitExam(attempt: ExamAttempt) = run(_examStatus) {
        repository.submitExam(treeId, attempt.id, answersFor(attempt))
    }

    fun collectCertificate(attemptId: String, nickname: String?) = run(_examStatus) {
        certificates.issue(treeId, attemptId, nickname)
    }

    fun destroyCertificate() = run(_examStatus) { certificates.destroy(treeId) }

    fun startChallenge(kind: TreeChallenge) = run(challengeOperations.getValue(kind)) {
        repository.startChallenge(treeId, kind)
    }

    fun updateChallengeDraft(kind: TreeChallenge, text: String) {
        if (!challengeOperations.getValue(kind).value.busy) {
            _challengeDrafts.value = _challengeDrafts.value + (kind to text.take(EXAM_ANSWER_LIMIT))
        }
    }

    fun submitChallenge(kind: TreeChallenge) = run(challengeOperations.getValue(kind)) {
        val answer = _challengeDrafts.value[kind].orEmpty()
        repository.answerChallenge(treeId, kind, answer)
        _challengeDrafts.value = _challengeDrafts.value - kind
    }

    private fun answersFor(attempt: ExamAttempt) =
        _examDraft.value?.takeIf { it.attemptId == attempt.id }?.answers ?: attempt.answers

    private fun run(status: MutableStateFlow<AssessmentStatus>, action: suspend () -> Unit) {
        if (status.value.busy) return
        status.value = AssessmentStatus(busy = true)
        viewModelScope.launch {
            try {
                action()
                status.value = AssessmentStatus()
            } catch (error: Exception) {
                status.value = AssessmentStatus(error = readable(error, "本次操作失败，请重试。"))
            }
        }
    }
}
