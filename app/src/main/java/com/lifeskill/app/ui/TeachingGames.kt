package com.lifeskill.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.*
import android.view.ViewGroup
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lifeskill.app.model.TeachingGame
import org.json.JSONObject
import java.io.ByteArrayInputStream

@Composable
internal fun TeachingGamePreview(block: TeachingGame, enabled: Boolean, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(block.goal, color = Ink, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("game-open-${block.id}")) {
            Mark(Glyph.EXPAND, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
            Text("放大游玩", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun TeachingGamePlayer(block: TeachingGame, epoch: Int, modifier: Modifier = Modifier) {
    key(block.id, epoch) {
        var failed by remember { mutableStateOf(false) }
        var view by remember { mutableStateOf<TeachingGameWebView?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, view) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) view?.pauseGame()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        if (failed) Box(modifier.padding(24.dp)) {
            Text("小游戏暂时无法运行，可重新开始或关闭后继续学习。", color = Ink)
        } else AndroidView<android.view.View>(
            factory = { context ->
                // 部分设备可能停用了系统WebView，初始化失败只能影响小游戏，不能中断整个学习页。
                try { TeachingGameWebView(context, block) { failed = true }.also { view = it } }
                catch (_: Exception) { failed = true; android.widget.FrameLayout(context) }
            },
            modifier = modifier.testTag("game-webview"),
            onRelease = { (it as? TeachingGameWebView)?.disposeGame() },
        )
    }
}

// 页面只承载受限绘图API，生成代码只在Worker中执行；不向网页添加任何JavaScript宿主接口。
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
internal class TeachingGameWebView(context: Context, block: TeachingGame, onFailure: () -> Unit) : WebView(context) {
    private var disposed = false

    init {
        contentDescription = "小游戏互动区域"
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            domStorageEnabled = false
            databaseEnabled = false
            blockNetworkLoads = true
            blockNetworkImage = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                disposeGame()
                onFailure()
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel(); return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel(); return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                result.cancel(); return true
            }
        }
        setDownloadListener { _, _, _, _, _ -> }
        if (Build.VERSION.SDK_INT >= 29) setWebViewRenderProcessClient(object : WebViewRenderProcessClient() {
            override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) = Unit
            override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                renderer?.terminate()
                onFailure()
            }
        })
        // Base64内容不含HTML定界符，代码中的引号或闭合script标签不会逃逸到受信页面。
        val payload = JSONObject().put("code", block.code).put("instructions", block.instructions).toString()
        val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val document = context.assets.open("teaching/game-runtime.html").bufferedReader().use { it.readText() }
            .replace("__PAYLOAD__", encoded)
        loadDataWithBaseURL("https://lifeskill-game.invalid/", document, "text/html", "UTF-8", null)
    }

    fun pauseGame() {
        if (!disposed) evaluateJavascript("window.pauseGame && window.pauseGame()", null)
    }

    fun disposeGame() {
        if (disposed) return
        disposed = true
        (parent as? ViewGroup)?.removeView(this)
        removeAllViews()
        destroy()
    }
}
