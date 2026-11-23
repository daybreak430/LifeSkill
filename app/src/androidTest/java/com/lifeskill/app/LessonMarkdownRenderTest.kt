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
import androidx.compose.foundation.layout.width
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

    private val structured = """
        ### 优惠券的三层代价

        先看结论：折扣伤的不是售价，是利润的斜率。

        1. **第一层：单笔利润变薄。** 标价 200 元、成本 120 元时利润率 40%；发一张满 150 减 30 的券，利润率掉到 25%。
        2. **第二层：销量必须补位。** 利润率降 15 个点，要多卖 60% 的单量才补得回来。
          - 用券门槛越高，补位压力越小
          - 券面额越大，补位压力越大
        3. **第三层：习惯被改写。** 顾客学会等券，原价订单被自己吃掉。

        > ✅ 区分：折扣率是售价的变化，利润率是成本的相对值，两者不能直接换算。

        要不要我用你店里的客单价算一遍？
    """.trimIndent()

    @Test fun wideFormulaShrinksOrSplitsInsteadOfScrolling() {
        // 真实案例：支付金额 = 访客数 × 转化率 × 客单价，在 300dp 内容宽度里放不下。
        val block = com.lifeskill.app.model.TeachingFormula(
            "store-profit", "店铺经营的核心公式",
            listOf("支付金额 = 访客数 × 转化率 × 客单价"),
            "拆三块，就能定位是流量、转化还是金额的问题。",
        )
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.width(330.dp).padding(15.dp), color = Color(0xFFFCFDFD)) {
                    com.lifeskill.app.ui.TeachingFormulaContent(block)
                }
            }
        }
        ui.onNodeWithText("支付金额", substring = true).assertIsDisplayed()
        ui.onNodeWithText("客单价", substring = true).assertIsDisplayed()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("lesson-formula-wide.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }

    @Test fun rendersMarkdownTableAsCard() {
        val markdown = """
            | 维度 | 满减券 | 折扣券 |
            | --- | --- | --- |
            | 计价 | 满 150 减 30，额度固定 | 全场 8 折，随客单价放大 |
            | 顾客感受 | 直减 30 元，感知明确 | “8 折”更直观 |
            | 利润 | 客单价越高越省 | 低价单也被打折 |
        """.trimIndent()
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    LessonMarkdown(markdown)
                }
            }
        }
        ui.onNodeWithText("满减券").assertIsDisplayed()
        ui.onNodeWithText("低价单也被打折").assertIsDisplayed()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("lesson-markdown-table.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }

    @Test fun rendersMermaidFenceEvenWithoutLanguageMarker() {
        // 回归：模型偶尔漏写 mermaid 语言标记，带箭头的围栏内容曾被当成字符图或代码块。
        val markdown = """
            先把这条因果链对上号：

            ```
            flowchart TD
            A[库存不准] --> B{卖超}
            B -->|是| C[无法发货]
            C --> D[订单被取消]
            ```
        """.trimIndent()
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    LessonMarkdown(markdown)
                }
            }
        }
        ui.onNodeWithText("库存不准").assertIsDisplayed()
        ui.onNodeWithText("卖超").assertIsDisplayed()
        ui.onNodeWithText("flowchart TD", substring = true).assertDoesNotExist()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("lesson-markdown-flow.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }

    @Test fun rendersHeadingsQuotesAndNestedLists() {
        ui.setContent {
            LifeSkillTheme(dark = false) {
                Surface(Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFFFCFDFD)) {
                    LessonMarkdown(structured)
                }
            }
        }
        ui.onNodeWithText("优惠券的三层代价").assertIsDisplayed()
        ui.onNodeWithText("用券门槛越高，补位压力越小").assertIsDisplayed()
        ui.onNodeWithText("✅ 区分：折扣率是售价的变化，利润率是成本的相对值，两者不能直接换算。").assertIsDisplayed()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("lesson-markdown-structured.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
    }

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
