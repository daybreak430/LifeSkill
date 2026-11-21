package com.lifeskill.app

import com.lifeskill.app.model.*

// 模拟升级前已保存的合法快照，独立于当前演示目录，防止测试随着目录删除一起失去覆盖。
internal fun retiredTeachingBlocks(): List<TeachingBlock> = listOf(
    TeachingClassification("classification", "旧分类", "旧分类题目",
        listOf(ClassificationCategory("a", "甲"), ClassificationCategory("b", "乙")),
        listOf(
            ClassificationItem("one", "第一条", "a", "解释"),
            ClassificationItem("two", "第二条", "b", "解释"),
            ClassificationItem("three", "第三条", "a", "解释"),
        ), "提示", "完整解释"),
    TeachingStepTrace("trace", "旧推演", "旧推演题目", listOf(
        TeachingTraceStep("first", "第一步", TeachingQuestion("问题一", listOf("甲", "乙"), 0, "解释", "提示"), "结果一"),
        TeachingTraceStep("second", "第二步", TeachingQuestion("问题二", listOf("甲", "乙"), 1, "解释", "提示"), "结果二"),
    ), "总结"),
)
