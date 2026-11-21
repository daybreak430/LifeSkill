package com.lifeskill.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry

// 截取原生窗口和WebView的实际合成结果，便于检查遮罩、触控区域与游戏显示，不包含真实用户内容。
internal fun captureTeachingScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val screenshot = instrumentation.uiAutomation.takeScreenshot()
    instrumentation.targetContext.cacheDir.resolve(name).outputStream().use {
        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
    screenshot.recycle()
}
