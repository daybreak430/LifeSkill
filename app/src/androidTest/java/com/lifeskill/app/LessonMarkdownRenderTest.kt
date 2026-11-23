package com.lifeskill.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.lifeskill.app.ui.LessonMarkdown
import com.lifeskill.app.ui.LifeSkillTheme
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test

// 独立渲染一条真实结构的讲解回复，验证等式行、兜底小标题与列表改动后的排版。
class LessonMarkdownRenderTest {
    @get:Rule
    val ui = createComposeRule()

    private val reply = """
        欢迎来到本节课 🎯 我们只做两件事：设置一个优惠，再算清它把单笔订单利润削掉多少。
        先讲一个最常被忽略的底层公式：
        - **订单利润 = 实收金额 - 商品成本 - 各项费用**
        - **实收金额 = 商品标价 - 优惠金额**
        所以优惠不是「少赚一点感觉」，它是一条直接从利润里扣走的钱。
        举个具体例子：商品标价 200 元，成本 100 元，运费+平台佣金合计 20 元。没有优惠时利润 = 200-100-20 = **80 元**。
        现在挂一张「满 150 减 30」的券，顾客刚好用上，实收变成 170，利润就变成 170-100-20 = **50 元**。
        - 优惠 30 元，利润从 80 掉到 50，跌幅 **37.5%**
        - 相当于 30 元瞬间蒸发，需要多卖 **60%**
    """.trimIndent()

    @Test fun rendersEquationBlocksSubheadsAndTightLists() {
        ui.setContent {
            // 使用真实主题，截图里的底色与强调色才和线上一致。
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    LessonMarkdown(reply)
                }
            }
        }
        // 等式行独立排版：ASCII 减号与等号被规范化，原文案不再原样出现。
        ui.onNodeWithText("订单利润 = 实收金额 − 商品成本 − 各项费用").assertIsDisplayed()
        ui.onNodeWithText("实收金额 = 商品标价 − 优惠金额").assertIsDisplayed()
        // 以冒号结尾的短段被识别为小标题，文字保持不变。
        ui.onNodeWithText("先讲一个最常被忽略的底层公式：").assertIsDisplayed()
        // 直接截取 Compose 根节点，避免系统截图抢在首帧绘制之前。
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("lesson-markdown-render.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }
}
