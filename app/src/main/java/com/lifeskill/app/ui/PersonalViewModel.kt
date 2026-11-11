package com.lifeskill.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeskill.app.AppContainer
import com.lifeskill.app.data.readAvatarPhoto
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.PersonalProfile
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.validatedNickname
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PersonalData(val profile: PersonalProfile, val certificates: List<CollectedCertificate>)

internal data class ProfileEditorState(
    val profile: PersonalProfile? = null, val busy: Boolean = false, val error: String? = null,
)

internal class PersonalViewModel(private val container: AppContainer) : ViewModel() {
    val personalData = combine(container.profiles.profile, container.certificates.certificates, ::PersonalData)
        .map { Result.success(it) }
        .catch { emit(Result.failure(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editor = MutableStateFlow(ProfileEditorState())
    val editor = _editor.asStateFlow()
    private val _certificateStatus = MutableStateFlow(AssessmentStatus())
    val certificateStatus = _certificateStatus.asStateFlow()

    fun setCertificateHidden(treeId: String, hidden: Boolean) = updateCertificate("无法更新证书的展示状态，请重试。") {
        container.certificates.setHidden(treeId, hidden)
    }

    fun destroyCertificate(treeId: String) = updateCertificate("无法删除证书，请重试。") {
        container.certificates.destroy(treeId)
    }

    private fun updateCertificate(failureMessage: String, operation: suspend () -> Unit) {
        if (_certificateStatus.value.busy) return
        _certificateStatus.value = AssessmentStatus(busy = true)
        viewModelScope.launch {
            try {
                operation()
                _certificateStatus.value = AssessmentStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _certificateStatus.value = AssessmentStatus(error = readable(error, failureMessage))
            }
        }
    }

    fun clearCertificateStatus() {
        if (!_certificateStatus.value.busy) _certificateStatus.value = AssessmentStatus()
    }

    fun beginEdit() {
        if (_editor.value.profile != null) return
        edit { current -> current.copy(profile = withContext(Dispatchers.IO) { container.profiles.current() }) }
    }

    fun changeNickname(value: String) = update { it.copy(nickname = value) }

    fun changeAvatar(value: ProfileAvatar) = update { it.copy(avatar = value) }

    fun choosePhoto(resolver: ContentResolver, uri: Uri) = edit { current ->
        val profile = checkNotNull(current.profile)
        current.copy(profile = profile.copy(avatar = readAvatarPhoto(resolver, uri)))
    }

    fun save(onSaved: () -> Unit) = edit { current ->
        val profile = checkNotNull(current.profile)
        val nickname = validatedNickname(profile.nickname.orEmpty())
        withContext(Dispatchers.IO) { container.profiles.save(profile.copy(nickname = nickname)) }
        onSaved()
        ProfileEditorState()
    }

    fun discard(): Boolean {
        if (_editor.value.busy) return false
        _editor.value = ProfileEditorState()
        return true
    }

    private fun update(transform: (PersonalProfile) -> PersonalProfile) {
        val current = _editor.value
        if (current.busy || current.profile == null) return
        _editor.value = current.copy(profile = transform(current.profile), error = null)
    }

    private fun edit(operation: suspend (ProfileEditorState) -> ProfileEditorState) {
        val current = _editor.value
        if (current.busy) return
        _editor.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                _editor.value = operation(current).copy(busy = false, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _editor.value = current.copy(error = readable(error, "无法更新个人资料，请重试。"))
            }
        }
    }
}
