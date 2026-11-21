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
        // 拖动两块工时给「访谈」，再用四块填满「测试」与「分析」；全部落位后本局结束。
        dragOnCanvas(0.12, 0.79, 0.18, 0.42)
        dragOnCanvas(0.24, 0.79, 0.18, 0.42)
        captureTeachingScreenshot("teaching-game.png")
        dragOnCanvas(0.36, 0.79, 0.50, 0.42)
        dragOnCanvas(0.48, 0.79, 0.50, 0.42)
        dragOnCanvas(0.60, 0.79, 0.50, 0.42)
        dragOnCanvas(0.72, 0.79, 0.82, 0.42)
        awaitState("finished")
        awaitMessage("6 块工时")
        ui.onNodeWithContentDescription("重新开始小游戏").performClick()
        awaitState("ready")
        back()
        ui.onNodeWithTag("game-open-${sample.id}").assertIsDisplayed()
        ui.waitUntil(8_000) { view() == null }
    }

    private fun dragOnCanvas(fromX: Double, fromY: Double, toX: Double, toY: Double) {
        val start = canvasOffset(fromX, fromY)
        val end = canvasOffset(toX, toY)
        ui.onNodeWithTag("game-webview").performTouchInput {
            down(start)
            moveTo(androidx.compose.ui.geometry.Offset((start.x + end.x) / 2, (start.y + end.y) / 2))
            moveTo(end)
            up()
        }
        ui.waitForIdle()
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

    @Test fun dragGestureMovesPieceAndCompletes() {
        // 拖动型小游戏：验证 pointerdown/move/up 能穿过 WebView 到达 Worker，而不是只支持点击。
        val code = "let x=20,held=false;const target=80;" +
            "game.draw([['rect',5,45,90,8,'#cfe'],['circle',target,49,4,'#c9a227'],['circle',x,49,5,'#276B91']]);" +
            "game.onPointer(p=>{if(p.phase==='down'&&Math.abs(p.x-x)<14)held=true;" +
            "else if(p.phase==='up'&&held){held=false;if(Math.abs(p.x-target)<6)game.finish('到位');else game.say('还没到');}});"
        show(sample.copy(code = code))
        ui.onNodeWithTag("game-open-${sample.id}").performClick()
        awaitState("ready")
        val start = canvasOffset(0.20, 0.49)
        val end = canvasOffset(0.80, 0.49)
        ui.onNodeWithTag("game-webview").performTouchInput {
            down(start)
            moveTo(androidx.compose.ui.geometry.Offset((start.x + end.x) / 2, (start.y + end.y) / 2))
            moveTo(end)
            up()
        }
        awaitState("finished")
        back()
    }

    @Test fun richModelSampleWithNewPrimitivesRenders() {
        // 真实 DeepSeek 生成的长脚本：用到 poly、8位透明色、描边与大量命令，验证放宽限制后仍能稳定渲染。
        val raw = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("teaching-game-rich-sample.json").bufferedReader().use { it.readText() }
        val game = com.lifeskill.app.ai.AiProtocol.tutor(raw).blocks.single() as TeachingGame
        assertTrue(game.code.length <= MAX_GAME_CODE_LENGTH)
        assertTrue(game.code.length > 2_000)
        show(game)
        ui.onNodeWithTag("game-open-${game.id}").performClick()
        awaitState("ready")
        assertTrue(evaluate("document.querySelectorAll('canvas').length").contains("1"))
        back()
    }

    @Test fun frameCallbackReceivesHostTicksAndNewPrimitivesRender() {
        // 动效依赖宿主发帧；同时验证 poly/arc/透明度颜色不会让运行器进入失败态。
        val code = "let n=0;game.background('#fbf7ef');" +
            "game.draw([['poly',[20,20,80,20,50,60,'#276B9188'],0],['arc',50,70,20,0,180,'#c9a227',1]," +
            "['text','动效',50,90,4,'#276B91','center','bold']]);" +
            "game.onFrame(t=>{n++;if(n===5)game.say('帧数 '+n);});"
        show(sample.copy(code = code))
        ui.onNodeWithTag("game-open-${sample.id}").performClick()
        awaitState("ready")
        awaitMessage("帧数 5")
        back()
    }

    private fun canvasOffset(fractionX: Double, fractionY: Double): Offset {
        val rectangle = JSONArray(
            evaluate(
                "(()=>{const r=document.querySelector('canvas').getBoundingClientRect();" +
                    "return [r.left,r.top,r.width,r.height,innerWidth];})()",
            ),
        )
        val native = checkNotNull(view())
        val ratio = ui.runOnUiThread { native.width } / rectangle.getDouble(4)
        return Offset(
            ((rectangle.getDouble(0) + rectangle.getDouble(2) * fractionX) * ratio).toFloat(),
            ((rectangle.getDouble(1) + rectangle.getDouble(3) * fractionY) * ratio).toFloat(),
        )
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
