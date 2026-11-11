package com.lifeskill.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.R
import com.lifeskill.app.model.AvatarPreset
import com.lifeskill.app.model.ProfileAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileEditScreen(app: AppViewModel, model: PersonalViewModel, active: Boolean) {
    val state by model.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.choosePhoto(context.contentResolver, uri)
    }
    val close = { if (model.discard()) app.back() }
    LaunchedEffect(active) { if (active) model.beginEdit() }
    BackHandler(enabled = active, onBack = close)
    Column(Modifier.fillMaxSize().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        PageHeader("编辑资料", close, Modifier.widthIn(max = 640.dp))
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val profile = state.profile
            if (profile == null) {
                if (state.error == null) Loading() else {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Action("重试", Glyph.RESTART, model::beginEdit)
                }
            } else {
                ProfileAvatarImage(profile.avatar, 90.dp)
                Action("从相册选择", Glyph.CAMERA, { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.busy)
                PresetAvatars(profile.avatar, !state.busy, model::changeAvatar)
                NicknameField(profile.nickname.orEmpty(), model::changeNickname, !state.busy)
                AssessmentStatusText(AssessmentStatus(error = state.error))
                Action("保存资料", Glyph.CHECK, {
                    keyboard?.hide()
                    model.save(app::back)
                }, Modifier.fillMaxWidth(), primary = true, enabled = !state.busy)
            }
        }
    }
}

@Composable
private fun PresetAvatars(avatar: ProfileAvatar, enabled: Boolean, change: (ProfileAvatar) -> Unit) {
    Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("内置头像", style = MaterialTheme.typography.titleSmall)
        AvatarPreset.entries.chunked(4).forEach { presets ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    val choice = ProfileAvatar.Preset(preset)
                    val selected = avatar == choice
                    Surface(
                        Modifier.size(54.dp).clip(CircleShape).selectable(selected, enabled = enabled, role = Role.RadioButton) {
                            change(choice)
                        }.semantics { contentDescription = "${preset.label}头像" },
                        shape = CircleShape, color = if (selected) GoldGround else Panel,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else Line),
                    ) {
                        IllustratedAvatar(preset, Modifier.fillMaxSize().padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun NicknameField(value: String, change: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(value, change, modifier.fillMaxWidth(), label = { Text("昵称") }, enabled = enabled,
        singleLine = true, shape = ControlShape, colors = fieldColors())
}

@Composable
internal fun ProfileAvatarImage(avatar: ProfileAvatar, diameter: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(diameter).semantics { contentDescription = "头像" },
        contentAlignment = Alignment.Center) {
        when (avatar) {
            is ProfileAvatar.Preset -> IllustratedAvatar(avatar.value, Modifier.fillMaxSize())
            is ProfileAvatar.Photo -> {
                val bitmap by produceState<ImageBitmap?>(null, avatar) {
                    value = withContext(Dispatchers.Default) {
                        val bytes = Base64.decode(avatar.jpeg, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                bitmap?.let {
                    Image(it, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } ?: IllustratedAvatar(AvatarPreset.default, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun IllustratedAvatar(preset: AvatarPreset, modifier: Modifier) {
    val resource = when (preset) {
        AvatarPreset.BOB -> R.drawable.avatar_bob
        AvatarPreset.GLASSES -> R.drawable.avatar_glasses
        AvatarPreset.LAVENDER -> R.drawable.avatar_lavender
        AvatarPreset.SILVER -> R.drawable.avatar_silver
        AvatarPreset.CURLS -> R.drawable.avatar_curls
        AvatarPreset.SCARF -> R.drawable.avatar_scarf
        AvatarPreset.CAT -> R.drawable.avatar_cat
        AvatarPreset.DOG -> R.drawable.avatar_dog
        AvatarPreset.FOX -> R.drawable.avatar_fox
        AvatarPreset.RABBIT -> R.drawable.avatar_rabbit
        AvatarPreset.ALIEN -> R.drawable.avatar_alien
        AvatarPreset.ROBOT -> R.drawable.avatar_robot
    }
    Image(painterResource(resource), contentDescription = null, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
}
