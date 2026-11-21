package com.lifeskill.app.model

internal data class BuiltInTeachingItem(val installMarker: Int, val block: TeachingBlock)

internal data class BuiltInTeachingLesson(
    val key: String, val installMarker: Int, val name: String, val shortName: String,
    val goal: String, val icon: EntryIcon, val introduction: String, val items: List<BuiltInTeachingItem>,
) {
    val id get() = "lifeskill:teaching:$key"
    val turnId get() = "lifeskill:example:$key"
    val blocks get() = items.map(BuiltInTeachingItem::block)
}

internal data class LegacyTeachingDemo(
    val key: String, val categoryKey: String, val name: String, val goal: String,
) {
    val id get() = "lifeskill:teaching:$key"
    val turnId get() = "lifeskill:example:$key"
}

internal object BuiltInTeaching {
    const val COURSE_ID = "lifeskill:teaching-showcase"
    const val INSTALL_MARKER = 1_000
    const val PREFERRED_PARENT_ID = "preset-4"
    const val FALLBACK_PARENT_ID = "lifeskill:showcase-direction"
    const val COURSE_NAME = "交互教学体验"
    val installMarkerIds: List<Int> get() = listOf(INSTALL_MARKER) +
        lessons.flatMap { lesson -> listOf(lesson.installMarker) + lesson.items.map { it.installMarker } }

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
    private val comparisonViolet = TeachingComparison("methods", "阅读与动手练习", listOf("阅读理解", "动手练习"), listOf(
        ComparisonDimension("task", "主要任务", listOf("建立概念，理解原理与术语。", "把概念用在具体任务中，发现实际困难。")),
        ComparisonDimension("check", "检验方式", listOf("用自己的话复述核心观点。", "独立完成任务，并解释为什么这样做。")),
    ), ComparisonPalette.VIOLET_TEAL)
    private val comparisonRose = TeachingComparison("travel", "步行与骑行", listOf("步行", "骑行"), listOf(
        ComparisonDimension("scene", "适用场景", listOf("距离较短，不需要额外设备。", "距离稍长，有安全的骑行条件。")),
        ComparisonDimension("limit", "需要考虑", listOf("步行时间与天气。", "停车条件、道路安全和设备状态。")),
    ), ComparisonPalette.ROSE_SAGE)

    private val demand = TeachingChart("demand", "价格与需求量", "需求量（杯）", "价格（元）",
        mode = TeachingChartMode.LINEAR_DEMAND,
        prediction = TeachingQuestion("其他条件不变，奶茶从6元降到4元，需求量会怎样变化？", listOf("增加", "减少"), 0,
            "在这个教学模型中，6元对应60杯，4元对应80杯。价格下降，需求量增加。", "只改变商品自身价格，沿给定需求曲线观察。"),
        quantityUnit = "杯")
    private val line = TeachingChart("trend", "阅读趋势：先预测再观察", "月份", "借阅量（次）", listOf(
        TeachingPoint("一月", 20.0), TeachingPoint("二月", 34.0), TeachingPoint("三月", 29.0), TeachingPoint("四月", 46.0)
    ), style = TeachingChartStyle.LINE)
    private val bar = TeachingChart("categories", "各类图书借阅量", "类别", "借阅量（次）", listOf(
        TeachingPoint("历史", 42.0), TeachingPoint("科学", 68.0), TeachingPoint("文学", 53.0), TeachingPoint("艺术", 31.0)
    ), style = TeachingChartStyle.BAR)
    private val area = TeachingChart("attendance", "每月活动参与人数", "月份", "人数（人）", listOf(
        TeachingPoint("一月", 20.0), TeachingPoint("二月", 35.0), TeachingPoint("三月", 28.0), TeachingPoint("四月", 48.0)
    ), style = TeachingChartStyle.AREA)

    private val quiz = TeachingQuiz("quiz", "随堂练习", TeachingQuestion(
        "问题与目标已明确，但支持一个需求的证据不足，下一步更合适的是？",
        listOf("直接交给开发", "补充用户验证", "立即永久否决"), 1,
        "先补充验证，再重新判断是否排期。证据不足不等于没有价值，也不能直接当作值得开发。",
        "想一想：此时最需要减少的是哪一类不确定性？"))
    private val flip = TeachingFlipCard("flip", "先回忆，再翻面",
        "不看答案，说出为什么商品价格下降通常会使需求量增加。",
        "其他条件不变时，价格下降会让更多消费者愿意且能够购买，因此需求曲线上的对应点向右移动。",
        "先区分商品自身价格与收入、偏好等其他条件。")
    private val sorting = TeachingSorting("sorting", "把用户研究排成步骤",
        "拖动条目，或使用上下移动按钮调整顺序。",
        listOf(
            TeachingSortItem("validate", "用访谈或小实验验证需求"),
            TeachingSortItem("observe", "记录用户在什么场景遇到困难"),
            TeachingSortItem("decide", "结合证据和目标决定是否排期"),
            TeachingSortItem("goal", "把困难澄清为可衡量的目标"),
        ), listOf("observe", "goal", "validate", "decide"),
        "先形成问题，再定义结果和判断依据；验证证据之后才进入排期判断。",
        "先观察具体困难，再把问题定义成可衡量的目标，接着收集证据验证，最后结合证据决定是否开发。")
    private val classification = TeachingClassification("classification", "观察还是解释",
        "为每条陈述选择更准确的类别。",
        listOf(ClassificationCategory("observation", "数据观察"), ClassificationCategory("explanation", "原因解释")),
        listOf(
            ClassificationItem("april", "四月借阅 52 次，高于其他月份。", "observation",
                "52 次可以直接从数据中读出，不需要补充背景。"),
            ClassificationItem("festival", "读书节可能提高了四月借阅量。", "explanation",
                "借阅增加的原因需要额外证据，因此属于解释或假设。"),
            ClassificationItem("march", "三月比二月少 8 次。", "observation",
                "两个数相减即可得到差异，属于直接观察。"),
            ClassificationItem("hours", "开放时间延长导致借阅增加。", "explanation",
                "这是对变化的因果解释，需要检查开放时间与借阅量的实际关系。"),
        ), "先问自己：这句话是否能直接从已有数据中读出？",
        "可直接读取的数值和差异是观察；说明“为什么”的因果陈述是解释，需要额外证据。")
    private val trace = TeachingStepTrace("trace", "百分比变化为什么不能直接相加",
        "某商品价格先由 100 元降到 80 元，再回到 100 元。先预测每一步的变化率。",
        listOf(
            TeachingTraceStep("down", "第一次下降",
                TeachingQuestion("从 100 元降到 80 元，以原价 100 元为基数，下降了百分之几？",
                    listOf("20%", "25%"), 0,
                    "20 ÷ 100 = 20%，第一次变化的基数是 100 元。", "用变化量除以这次变化前的数。"),
                "第一次下降 20%，价格变为 80 元。"),
            TeachingTraceStep("up", "第二次上涨",
                TeachingQuestion("从 80 元涨回 100 元，以 80 元为基数，上涨了百分之几？",
                    listOf("20%", "25%"), 1,
                    "20 ÷ 80 = 25%，第二次变化的基数已经变成 80 元。", "注意这次计算从哪个数开始。"),
                "第二次上涨 25%，价格重新回到 100 元。"),
            TeachingTraceStep("combine", "能否直接相加",
                TeachingQuestion("−20% 与 +25% 能直接相加当作最终变化吗？",
                    listOf("能，合计 +5%", "不能，两次基数不同"), 1,
                    "两次百分比使用了不同基数，最终净值回到 0，不能把百分数当作同一单位直接相加。",
                    "比较两次百分比的基数是否相同。"),
                "最终价格仍是 100 元，净变化为 0；百分比变化必须结合基数和先后顺序解释。"),
        ), "价格最终没有变化。−20% 和 +25% 不能相加，因为第二次的 25% 以 80 元为基数。")

    private val borrowing = TeachingChart("borrowing", "图书角借阅量", "月份", "借阅量（次）", listOf(
        TeachingPoint("一月", 24.0), TeachingPoint("二月", 40.0), TeachingPoint("三月", 32.0), TeachingPoint("四月", 52.0)
    ))
    private val bridge = TeachingText("bridge", "从观察到解释",
        "**四月借阅最多**是数据直接支持的观察。至于为什么增多，还需要了解活动、开放时间等条件。")
    private val evidence = TeachingComparison("evidence", "观察与解释", listOf("数据观察", "原因解释"), listOf(
        ComparisonDimension("basis", "依据", listOf("直接读取图中的数值。", "需要额外证据，不能只凭趋势断定。")))
    )
    private val mixedQuiz = TeachingQuiz("check", "数据判断", TeachingQuestion("仅凭图中数据，哪项结论最有依据？",
        listOf("四月借阅量最高", "四月一定举办了读书节", "每个月都持续增长"), 0,
        "四月为52次，高于其他月份。数据没有告诉我们增长原因，且三月低于二月。", "只选择能从图中直接读出的信息。"))
    private val hotspot = TeachingHotspot("hotspot", "观察植物细胞",
        "点击图中的热点，逐处查看结构作用。",
        TeachingHotspotImage.PLANT_CELL,
        listOf(
            ImageHotspot("wall", "细胞壁", 8.0, 24.0, "细胞壁位于细胞外侧，提供支持和保护。"),
            ImageHotspot("nucleus", "细胞核", 50.0, 46.0, "细胞核含有遗传物质，参与控制细胞活动。"),
            ImageHotspot("vacuole", "液泡", 68.0, 29.0, "成熟植物细胞的液泡可储存水分和多种物质。"),
            ImageHotspot("chloroplast", "叶绿体", 28.0, 73.0, "叶绿体是植物进行光合作用的场所。"),
            ImageHotspot("mitochondria", "线粒体", 79.0, 74.0, "线粒体参与细胞的能量转换。"),
        ), "从外侧边界开始，再寻找中央的控制中心和较大的储存结构。")

    // 课时按学习任务归类。新增同类组件只向对应课时追加 item，只有新类别才增加课时节点。
    val lessons: List<BuiltInTeachingLesson> = listOf(
        BuiltInTeachingLesson("structure", 2_001, "结构与过程", "结构", "组合时间轴、流程与对比表达，理解事件顺序和概念异同。",
            EntryIcon.BOOK, "同一会话中组合时间线、分支流程和三种对比配色。先切换关注点，再比较表达方式。",
            listOf(
                BuiltInTeachingItem(1_001, timeline),
                BuiltInTeachingItem(1_002, flow),
                BuiltInTeachingItem(1_003, comparison),
                BuiltInTeachingItem(1_007, comparisonViolet),
                BuiltInTeachingItem(1_008, comparisonRose),
            )),
        BuiltInTeachingLesson("data", 2_002, "数据图表", "图表", "在同一会话中预测、比较并观察折线、柱状和面积图。",
            EntryIcon.CHART, "同一会话展示需求预测、趋势、分类比较和总量面积。切换表达时核对数值、单位与坐标。",
            listOf(
                BuiltInTeachingItem(1_004, demand),
                BuiltInTeachingItem(1_016, line),
                BuiltInTeachingItem(1_009, bar),
                BuiltInTeachingItem(1_010, area),
            )),
        BuiltInTeachingLesson("practice", 2_003, "互动练习", "练习", "在一条会话中体验答题、翻卡、排序、分类和分步推演。",
            EntryIcon.TARGET, "统一遵循先提示、允许重试、主动查看完整答案。选择任意组件开始练习。",
            listOf(
                BuiltInTeachingItem(1_005, quiz),
                BuiltInTeachingItem(1_011, flip),
                BuiltInTeachingItem(1_012, sorting),
                BuiltInTeachingItem(1_013, classification),
                BuiltInTeachingItem(1_015, trace),
            )),
        BuiltInTeachingLesson("visual", 2_004, "图像与综合", "综合", "组合图片热点、数据观察、文字解释、对比和判断题。",
            EntryIcon.LEAF, "先探索植物细胞热点，再回到数据观察与解释，比较不同媒介如何承担讲解。",
            listOf(
                BuiltInTeachingItem(1_014, hotspot),
                BuiltInTeachingItem(1_017, borrowing),
                BuiltInTeachingItem(1_018, bridge),
                BuiltInTeachingItem(1_019, evidence),
                BuiltInTeachingItem(1_020, mixedQuiz),
            )),
    )

    // 这些是分类改版前的内置课时。迁移时只合并未被用户改名、改写目标或追加对话的示例，
    // 保留用户自己的标题、目标和追问，避免为了减少节点而覆盖真实内容。
    internal val legacyDemos: List<LegacyTeachingDemo> = listOf(
        LegacyTeachingDemo("timeline", "structure", "时间轴：看清事件先后", "点击时间节点，比较事件的先后与关联。"),
        LegacyTeachingDemo("flow", "structure", "流程图：探索判断与分支", "点击流程节点，沿不同条件探索下一步。"),
        LegacyTeachingDemo("comparison", "structure", "对比卡：切换维度辨异同", "切换比较维度，辨别容易混淆的概念。"),
        LegacyTeachingDemo("comparison-violet", "structure", "对比卡：紫青双栏", "切换维度比较两种学习方法。"),
        LegacyTeachingDemo("comparison-rose", "structure", "对比卡：玫瑰与鼠尾草", "比较两种方案，识别适用条件。"),
        LegacyTeachingDemo("chart", "data", "交互图表：先预测再验证", "先作预测，再调整价格参数，观察图形和数量。"),
        LegacyTeachingDemo("chart-bar", "data", "柱状图：比较分类数据", "点选柱子，比较不同类别的数量。"),
        LegacyTeachingDemo("chart-area", "data", "面积图：观察总量趋势", "选择月份，观察总量的变化与回落。"),
        LegacyTeachingDemo("quiz", "practice", "随堂答题：从反馈中理解", "提交选择，查看线索并重答，理解随堂反馈与正式课后练习的区别。"),
        LegacyTeachingDemo("mixed", "visual", "混合讲解：一条回复多种表达", "在一条教学回复中体验图表、文字、对比与随堂问题的组合。"),
        LegacyTeachingDemo("flip-card", "practice", "知识翻卡：先回忆再看答案", "先尝试自己回答，再翻面核对核心条件。"),
        LegacyTeachingDemo("sorting", "practice", "排序练习：拖动调整步骤", "拖动条目或使用上下移动按钮，排出合理的用户研究顺序。"),
        LegacyTeachingDemo("classification", "practice", "分类练习：区分观察与解释", "把每条陈述归入数据观察或原因解释，并查看逐项依据。"),
        LegacyTeachingDemo("hotspot", "visual", "图片热点：探索植物细胞", "点击图中的热点查看局部作用，必要时放大观察。"),
        LegacyTeachingDemo("step-trace", "practice", "分步推演：百分比变化", "先预测每一步结果，再展开计算，理解百分比为什么不能直接相加。"),
    )

    fun lesson(id: String?): BuiltInTeachingLesson? = lessons.find { it.id == id }
    fun lessonByKey(key: String): BuiltInTeachingLesson? = lessons.find { it.key == key }
    fun canHostShowcase(directionId: String): Boolean =
        directionId == PREFERRED_PARENT_ID || directionId == FALLBACK_PARENT_ID
}
