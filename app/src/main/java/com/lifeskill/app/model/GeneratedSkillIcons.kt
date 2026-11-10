package com.lifeskill.app.model

internal object GeneratedSkillIcons {
    private val topics = listOf(
        EntryIcon.CODE to listOf("编程", "代码", "脚本", "算法", "python", "kotlin", "javascript"),
        EntryIcon.CHART to listOf("数据", "统计", "图表", "指标", "sql", "excel"),
        EntryIcon.SPEECH to listOf("演讲", "口语", "表达", "访谈", "沟通", "提问", "辩论"),
        EntryIcon.CAMERA to listOf("摄影", "拍摄", "镜头", "构图", "视频"),
        EntryIcon.MUSIC to listOf("音乐", "乐理", "节奏", "和弦", "乐器"),
        EntryIcon.LEAF to listOf("生态", "植物", "园艺", "自然"),
        EntryIcon.TREE to listOf("流程", "架构", "分支", "思维导图"),
        EntryIcon.PRACTICAL to listOf("清单", "核对", "步骤", "盘点", "整理", "计划"),
        EntryIcon.BOOK to listOf("阅读", "概念", "原理", "基础", "术语", "规则"),
        EntryIcon.TOOLS to listOf("工具", "配置", "操作", "部署", "动手", "实践"),
        EntryIcon.TARGET to listOf("目标", "验证", "评估", "诊断", "检验"),
        EntryIcon.WORK to listOf("职场", "岗位", "协作", "交付"),
    )
    private val general = listOf(EntryIcon.EXPLORE, EntryIcon.PRACTICAL, EntryIcon.BOOK, EntryIcon.TARGET)

    // 标题比说明更能代表技能。无法从文本区分时，优先用当前树中较少见的通用图标。
    fun fallback(name: String, goal: String, used: Map<EntryIcon, Int>): EntryIcon =
        match(name) ?: match(goal) ?: general.minBy { used[it] ?: 0 }

    private fun match(text: String): EntryIcon? {
        val normalized = text.lowercase()
        return topics.firstOrNull { (_, keywords) -> keywords.any { it in normalized } }?.first
    }
}
