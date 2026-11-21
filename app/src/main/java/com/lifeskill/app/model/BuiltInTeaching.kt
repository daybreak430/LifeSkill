package com.lifeskill.app.model

internal data class BuiltInTeachingLesson(
    val key: String, val installMarker: Int, val name: String, val shortName: String,
    val goal: String, val icon: EntryIcon, val introduction: String, val blocks: List<TeachingBlock>,
) {
    val id get() = "lifeskill:teaching:$key"
    val turnId get() = "lifeskill:example:$key"
}

internal object BuiltInTeaching {
    const val COURSE_ID = "lifeskill:teaching-showcase"
    const val INSTALL_MARKER = 1_000
    const val COURSE_NAME = "交互教学体验"

    private val timeline = TeachingTimeline("timeline", "从统一到灭亡", listOf(
        TimelineEvent("unify", "前221年", "统一六国", "秦建立统一国家，治理范围与行政任务随之变化。"),
        TimelineEvent("succession", "前210年", "秦始皇去世", "继承危机与权力斗争使中央政治局势动荡。它是重要转折，但不是秦亡的唯一原因。"),
        TimelineEvent("uprising", "前209年", "陈胜吴广起义", "大规模反秦战争开始。比较相邻事件，区分长期矛盾与直接触发因素。"),
        TimelineEvent("surrender", "前207年", "子婴投降", "刘邦进入关中，子婴投降，秦朝统治结束。楚汉战争属于随后的阶段。"),
    ))
    private val flow = TeachingFlow("flow", "需求怎样进入开发", """
        flowchart TD
        A[收集问题] --> B[澄清目标]
        B --> C{证据充分}
        C -->|是| D[排期]
        C -->|否| E[回访用户]
        D --> F[开发验证]
        E --> C
    """.trimIndent(), linkedMapOf(
        "A" to "先记录谁在什么场景遇到什么困难，不急着把问题写成具体功能。",
        "B" to "确定希望改善的结果和判断标准，例如减少一次重复录入。",
        "C" to "访谈、使用数据或小实验，是否支持这个问题与解法？选择不同分支继续探索。",
        "D" to "证据支持后，结合收益、成本与依赖确定交付范围。",
        "E" to "补充用户观察或小实验，得到新证据后回到判断。这不等于永久否决需求。",
        "F" to "实现后继续验证结果是否改善，而不只检查功能有没有上线。",
    ))
    private val comparison = TeachingComparison("comparison", "需求量变化与需求变化", listOf("需求量变化", "需求变化"), listOf(
        ComparisonDimension("cause", "变化原因", listOf("商品自身价格改变，例如奶茶从6元降到4元。", "收入、偏好等条件改变，例如更多人开始偏爱奶茶。")),
        ComparisonDimension("graph", "图上表现", listOf("沿同一条需求曲线移动，曲线本身不变。", "需求曲线整体移动，相同价格下的需求量改变。")),
        ComparisonDimension("condition", "保持不变的条件", listOf("收入、偏好等其他条件保持不变。", "比较相同价格，但收入、偏好等条件已改变。")),
    ))
    private val demand = TeachingChart("demand", "价格与需求量", "需求量（杯）", "价格（元）",
        mode = TeachingChartMode.LINEAR_DEMAND,
        prediction = TeachingQuestion("其他条件不变，奶茶从6元降到4元，需求量会怎样变化？", listOf("增加", "减少"), 0,
            "在这个教学模型中，6元对应60杯，4元对应80杯。价格下降，需求量增加。", "只改变商品自身价格，沿给定需求曲线观察。"), quantityUnit = "杯")
    private val quiz = TeachingQuiz("quiz", "随堂练习", TeachingQuestion(
        "问题与目标已明确，但支持一个需求的证据不足，下一步更合适的是？",
        listOf("直接交给开发", "补充用户验证", "立即永久否决"), 1,
        "先补充验证，再重新判断是否排期。证据不足不等于没有价值，也不能直接当作值得开发。",
        "想一想：此时最需要减少的是哪一类不确定性？"))

    // 新交互在此追加独立课时并分配新的安装标记；不要改用列表下标，避免未来排序变化复活用户删除的课时。
    val lessons: List<BuiltInTeachingLesson> = listOf(
        BuiltInTeachingLesson("timeline", 1_001, "时间轴：看清事件先后", "时间轴", "点击时间节点，比较事件的先后与关联。", EntryIcon.BOOK,
            "点开事件节点，看看政治危机与社会反抗如何相继出现。时间先后帮助梳理线索，解释秦亡还需结合多方面原因。", listOf(timeline)),
        BuiltInTeachingLesson("flow", 1_002, "流程图：探索判断与分支", "流程图", "点击流程节点，沿不同条件探索下一步。", EntryIcon.PRACTICAL,
            "这是通用产品研发示例。点开节点查看说明，再沿「是／否」分支探索；证据不足时会回到用户验证。", listOf(flow)),
        BuiltInTeachingLesson("comparison", 1_003, "对比卡：切换维度辨异同", "对比卡", "切换比较维度，辨别容易混淆的概念。", EntryIcon.BOOK,
            "先比较变化原因，再看图上表现。它们只差一个「量」字，含义却不同。", listOf(comparison)),
        BuiltInTeachingLesson("chart", 1_004, "交互图表：先预测再验证", "交互图表", "先作预测，再调整价格参数，观察图形和数量。", EntryIcon.CHART,
            "先保持收入、偏好等条件不变，只调整价格。这里使用简化的线性需求教学模型，不代表现实销量预测。", listOf(demand)),
        BuiltInTeachingLesson("quiz", 1_005, "随堂答题：从反馈中理解", "随堂答题", "提交选择，查看线索并重答，理解随堂反馈与正式课后练习的区别。", EntryIcon.TARGET,
            "先独立选一个答案。答错会给线索，调整答案后可以重新提交。这道随堂题不计入课时完成。", listOf(quiz)),
        BuiltInTeachingLesson("mixed", 1_006, "混合讲解：一条回复多种表达", "混合讲解", "在一条教学回复中体验图表、文字、对比与随堂问题的组合。", EntryIcon.EXPLORE,
            "下面用一组教学示例数据演示：先观察每月借阅量，再比较数据与解释，最后检验理解。", listOf(
                TeachingChart("borrowing", "图书角借阅量", "月份", "借阅量（次）", listOf(
                    TeachingPoint("一月", 24.0), TeachingPoint("二月", 40.0), TeachingPoint("三月", 32.0), TeachingPoint("四月", 52.0))),
                TeachingText("bridge", "从观察到解释", "**四月借阅最多**是数据直接支持的观察。至于为什么增多，还需要了解活动、开放时间等条件。"),
                TeachingComparison("evidence", "观察与解释", listOf("数据观察", "原因解释"), listOf(
                    ComparisonDimension("basis", "依据", listOf("直接读取图中的数值。", "需要额外证据，不能只凭趋势断定。")))),
                TeachingQuiz("check", "随堂练习", TeachingQuestion("仅凭图中数据，哪项结论最有依据？",
                    listOf("四月借阅量最高", "四月一定举办了读书节", "每个月都持续增长"), 0,
                    "四月为52次，高于其他月份。数据没有告诉我们增长原因，且三月低于二月。", "只选择能从图中直接读出的信息。")),
            )),
    )

    fun lesson(id: String?): BuiltInTeachingLesson? = lessons.find { it.id == id }
}
