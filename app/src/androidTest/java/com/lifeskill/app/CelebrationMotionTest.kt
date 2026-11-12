package com.lifeskill.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.ui.CELEBRATION_DURATION_MS
import com.lifeskill.app.ui.CelebrationContent
import com.lifeskill.app.ui.LifeSkillTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CelebrationMotionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val cues = mutableListOf<Pair<Long, HapticFeedbackType>>()
    private var showing by mutableStateOf(true)

    @Test fun hapticsFollowTheBadgeAndEachFirework() {
        showCelebration()
        ui.mainClock.advanceTimeBy(CELEBRATION_DURATION_MS + 250L)
        ui.onNodeWithTag("celebration-fireworks").assertDoesNotExist()
        val recorded = ui.runOnIdle { cues.toList() }
        assertEquals(
            listOf(HapticFeedbackType.Confirm) + List(4) { HapticFeedbackType.SegmentFrequentTick } +
                HapticFeedbackType.SegmentTick,
            recorded.map { it.second },
        )
        val intervals = recorded.zipWithNext { previous, next -> next.first - previous.first }
        val expected = listOf(209L, 300L, 407L, 386L, 523L)
        assertTrue("触感间隔应与徽章和烟花的动画帧对齐：$intervals", intervals.zip(expected).all {
            (actual, target) -> abs(actual - target) <= 17
        })
    }

    @Test fun closingStopsLaterHapticsAndRestoringDoesNotReplayThem() {
        showCelebration()
        ui.mainClock.advanceTimeBy(160L)
        assertEquals(listOf(HapticFeedbackType.Confirm), ui.runOnIdle { cues.map { it.second } })
        ui.onNodeWithContentDescription("关闭庆祝").assertIsDisplayed().performTouchInput { click() }
        ui.mainClock.advanceTimeBy(CELEBRATION_DURATION_MS + 250L)
        ui.onNodeWithTag("skill-celebration").assertDoesNotExist()
        assertEquals(1, ui.runOnIdle { cues.size })

        ui.runOnIdle { showing = true }
        ui.mainClock.advanceTimeBy(CELEBRATION_DURATION_MS + 250L)
        ui.onNodeWithTag("skill-celebration").assertIsDisplayed()
        ui.onNodeWithTag("celebration-fireworks").assertDoesNotExist()
        assertEquals(1, ui.runOnIdle { cues.size })
    }

    private fun showCelebration() {
        ui.mainClock.autoAdvance = false
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                cues += ui.mainClock.currentTime to hapticFeedbackType
            }
        }
        val skill = Entry("celebration-test", null, EntryKind.SKILL, "测验通过")
        ui.runOnUiThread {
            ui.activity.setContent {
                LifeSkillTheme(dark = true) {
                    val stateHolder = rememberSaveableStateHolder()
                    if (showing) {
                        stateHolder.SaveableStateProvider(skill.id) {
                            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                                CelebrationContent(skill.id, skill) { showing = false }
                            }
                        }
                    }
                }
            }
        }
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithTag("skill-celebration").assertIsDisplayed()
    }
}
