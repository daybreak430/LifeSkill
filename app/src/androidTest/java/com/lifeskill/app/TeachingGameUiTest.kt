package com.lifeskill.app

import android.annotation.TargetApi
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.lifeskill.app.model.*
import com.lifeskill.app.ui.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
@TargetApi(29)
class TeachingGameUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val sample get() = BuiltInTeaching.lessonByKey("practice")!!.blocks.filterIsInstance<TeachingGame>().single()

    @Test fun gameOnlyRunsAfterExpansionAndSupportsRealTouchRestartAndBack() {
        show(sample)
        ui.waitUntil(8_000) { view() == null }
        ui.onNodeWithTag("game-open-${sample.id}").performClick()
        awaitState("ready")
        val native = checkNotNull(view())
        ui.runOnUiThread {
            assertTrue(native.settings.blockNetworkLoads)
            assertFalse(native.settings.allowFileAccess)
            assertFalse(native.settings.allowContentAccess)
        }
        tapButton(0)
        awaitMessage("1/4")
        captureTeachingScreenshot("teaching-game.png")
        tapButton(1)
        awaitMessage("3/4")
        tapButton(0)
        awaitState("finished")
        awaitMessage("4/4")
        ui.onNodeWithContentDescription("重新开始小游戏").performClick()
        awaitState("ready")
        awaitMessage("0/4")
        back()
        ui.onNodeWithTag("game-open-${sample.id}").assertIsDisplayed()
        ui.waitUntil(8_000) { view() == null }
    }

    @Test fun brokenAndRunawayCodeRemainClosableAndDoNotHangNativeUi() {
        listOf("throw new Error('坏代码');", "while(true){}").forEach { code ->
            show(sample.copy(code = code))
            ui.onNodeWithTag("game-open-${sample.id}").performClick()
            awaitState("failed")
            ui.onNodeWithContentDescription("关闭互动图示").assertIsDisplayed().performClick()
            ui.waitUntil(8_000) { view() == null }
        }
    }

    @Test fun workerCannotFetchResourcesOrInjectHtmlIntoTheHost() {
        val code = "game.say('</script><img src=https://example.com>');fetch('https://example.com').then(()=>game.say('NETWORK_ALLOWED')).catch(()=>game.say('NETWORK_BLOCKED'));"
        show(sample.copy(code = code))
        ui.onNodeWithTag("game-open-${sample.id}").performClick()
        awaitState("ready")
        awaitMessage("NETWORK_BLOCKED")
        assertEquals("0", evaluate("document.querySelectorAll('img').length"))
        val native = checkNotNull(view())
        ui.runOnUiThread { assertFalse(native.settings.domStorageEnabled) }
        back()
    }

    @Test fun actualModelGeneratedGameRunsAndCompletesWithTouch() {
        val raw = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("teaching-game-model-sample.json").bufferedReader().use { it.readText() }
        val game = com.lifeskill.app.ai.AiProtocol.tutor(raw).blocks.single() as TeachingGame
        assertTrue(game.code.length <= MAX_GAME_CODE_LENGTH)
        show(game)
        ui.onNodeWithTag("game-open-${game.id}").performClick()
        awaitState("ready")
        tapButton(1)
        awaitMessage("2/4")
        tapButton(1)
        awaitState("finished")
        awaitMessage("4/4")
        back()
    }

    private fun show(game: TeachingGame) {
        ui.runOnUiThread { ui.activity.setContent { LifeSkillTheme(false) {
            TeachingContent(Turn("game-ui", Speaker.ASSISTANT, TurnKind.MESSAGE, "说明", teachingBlocks = listOf(game)), true) { _, _ -> }
        } } }
    }
    private fun view(): TeachingGameWebView? {
        fun find(node: View): TeachingGameWebView? {
            if (node is TeachingGameWebView && node.isAttachedToWindow) return node
            if (node is ViewGroup) for (index in 0 until node.childCount) find(node.getChildAt(index))?.let { return it }
            return null
        }
        return ui.runOnUiThread { WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find) }
    }
    private fun evaluate(script: String): String {
        val result = AtomicReference<String?>(null)
        val native = checkNotNull(view())
        ui.runOnUiThread { native.evaluateJavascript(script) { result.set(it) } }
        ui.waitUntil(8_000) { result.get() != null }
        return result.get()!!
    }
    private fun awaitState(expected: String) = ui.waitUntil(8_000) {
        view() != null && evaluate("document.body && document.body.dataset.state") == "\"$expected\""
    }
    private fun awaitMessage(expected: String) = ui.waitUntil(8_000) {
        evaluate("document.querySelector('#message').textContent").contains(expected)
    }
    private fun tapButton(index: Int) {
        val rectangle = JSONArray(evaluate("(()=>{const r=document.querySelectorAll('button')[$index].getBoundingClientRect();return [(r.left+r.right)/2,(r.top+r.bottom)/2,innerWidth];})()"))
        val native = checkNotNull(view())
        val ratio = ui.runOnUiThread { native.width } / rectangle.getDouble(2)
        ui.onNodeWithTag("game-webview").performTouchInput {
            click(Offset((rectangle.getDouble(0)*ratio).toFloat(), (rectangle.getDouble(1)*ratio).toFloat()))
        }
    }
    private fun back() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.waitForIdle()
    }
}
