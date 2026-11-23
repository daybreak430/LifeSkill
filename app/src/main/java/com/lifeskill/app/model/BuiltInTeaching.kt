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
    // 停用组件的旧安装标记仍属于演示课，主动恢复时一并清理；标记不得分配给新组件。
    // 1_002 是已停用的流程图组件，1_003 与 1_019 是已停用的对比卡示例。
    val installMarkerIds: List<Int> get() = listOf(INSTALL_MARKER, 1_002, 1_003, 1_007, 1_008, 1_013, 1_015, 1_019) +
        lessons.flatMap { lesson -> listOf(lesson.installMarker) + lesson.items.map { it.installMarker } }

    private val timeline = TeachingTimeline("timeline", "从统一到灭亡", listOf(
        TimelineEvent("unify", "前221年", "统一六国", "秦建立统一国家，治理范围与行政任务随之变化。"),
        TimelineEvent("succession", "前210年", "秦始皇去世", "继承危机与权力斗争使中央政治局势动荡。它是重要转折，但不是秦亡的唯一原因。"),
        TimelineEvent("uprising", "前209年", "陈胜吴广起义", "大规模反秦战争开始。比较相邻事件，区分长期矛盾与直接触发因素。"),
        TimelineEvent("surrender", "前207年", "子婴投降", "刘邦进入关中，子婴投降，秦朝统治结束。楚汉战争属于随后的阶段。"),
    ))
    private val comparisonViolet = TeachingComparison("methods", "阅读与动手练习", listOf("阅读理解", "动手练习"), listOf(
        ComparisonDimension("task", "主要任务", listOf("建立概念，理解原理与术语。", "把概念用在具体任务中，发现实际困难。")),
        ComparisonDimension("check", "检验方式", listOf("用自己的话复述核心观点。", "独立完成任务，并解释为什么这样做。")),
    ), ComparisonPalette.VIOLET_TEAL)
    private val comparisonRose = TeachingComparison("travel", "步行与骑行", listOf("步行", "骑行"), listOf(
        ComparisonDimension("scene", "适用场景", listOf("距离较短，不需要额外设备。", "距离稍长，有安全的骑行条件。")),
        ComparisonDimension("limit", "需要考虑", listOf("步行时间与天气。", "停车条件、道路安全和设备状态。")),
    ), ComparisonPalette.ROSE_SAGE)

    // 结构图示例：一个整体、一句关系、三个并列依据，演示“一行一层”的板书式表达。
    private val studyScatter = TeachingChart(
        "study-scatter", "学习时长与得分", "每周时长（小时）", "得分",
        listOf(
            TeachingPoint("1班", 52.0, 1.0),
            TeachingPoint("2班", 58.0, 2.0),
            TeachingPoint("3班", 61.0, 3.0),
            TeachingPoint("4班", 68.0, 4.0),
            TeachingPoint("5班", 74.0, 6.0),
        ), style = TeachingChartStyle.SCATTER,
    )

    private val monthlyWaterfall = TeachingChart(
        "monthly-waterfall", "月活变化", "项目", "万人",
        listOf(
            TeachingPoint("期初", 120.0, total = true),
            TeachingPoint("新客", 40.0),
            TeachingPoint("流失", -25.0),
            TeachingPoint("复购", 15.0),
            TeachingPoint("期末", 150.0, total = true),
        ), style = TeachingChartStyle.WATERFALL,
    )
    // 四种新图形各给一个最小示例，演示“同一份数据换一种图形”而不是堆装饰。
    private val toolMatrix = TeachingMatrix(
        "tool-matrix", "三种方案怎么选",
        listOf("自建", "采购", "外包"), listOf("成本", "风险", "可维护"),
        listOf(listOf(5.0, 3.0, 4.0), listOf(3.0, 5.0, 3.0), listOf(2.0, 2.0, 5.0)),
        "1–5 分；分数只帮你比较，不替代判断。",
    )
    private val deliveryRange = TeachingRange(
        "delivery-range", "预计多久能上线",
        "预计 3–5 天", 0.0, 10.0, 3.0, 5.0, 4.2, "天",
        "区间来自三个同类需求的历史耗时。",
    )
    private val quadratic = TeachingFormula(
        "quadratic", "求根公式",
        listOf("x = frac{-b ± sqrt{b^{2}-4ac}}{2a}"),
        "判别式大于 0 时有两个实根。",
    )
    private val hiringFunnel = TeachingFunnel(
        "hiring-funnel", "从投递到录用", FunnelMode.FUNNEL,
        listOf(
            FunnelStage("投递", "240", 100),
            FunnelStage("初筛", "96", 78),
            FunnelStage("面试", "31", 54),
            FunnelStage("录用", "8", 32),
        ),
        "每层都相对上一层，不能跨层比较。",
    )

    private val writingFigure = TeachingFigure(
        "writing-structure", "写作的三层结构",
        listOf(
            FigureRow.Nodes(listOf(FigureItem("核心主张", note = "一句话说清要证明什么", tone = FigureTone.AMBER))),
            FigureRow.Relation("由三类依据支撑"),
            FigureRow.Nodes(listOf(
                FigureItem("数据", note = "可核对的数字", tone = FigureTone.BLUE),
                FigureItem("案例", note = "具体发生过的事", tone = FigureTone.BLUE),
                FigureItem("反例", note = "说明边界在哪里", tone = FigureTone.BLUE),
            )),
        ),
        caption = "三类依据都指向同一主张时，论证才站得住。",
    )

    private val demand = TeachingChart("demand", "价格与需求量", "需求量（杯）", "价格（元）",
        mode = TeachingChartMode.LINEAR_DEMAND,
        prediction = TeachingQuestion("其他条件不变，奶茶从6元降到4元，需求量会怎样变化？", listOf("增加", "减少"), 0,
            "在这个教学模型中，6元对应60杯，4元对应80杯。价格下降，需求量增加。", "只改变商品自身价格，沿给定需求曲线观察。"),
        quantityUnit = "杯")
    // 只用于辨认并清理未经修改的旧配色演示，不再安装为当前示例。
    internal val retiredComparisonDemos get() = listOf(comparisonViolet, comparisonRose)

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
        "想一想：此时最需要减少的是哪一类不确定性？",
        listOf(
            "想一想：此时最需要减少的是哪一类不确定性？",
            "直接交给开发，或者立即否决，都会跳过同一个环节。",
            "先降低判断错误的风险，再决定要不要投入开发。",
        )))
    private val flip = TeachingFlipCard("flip", "先回忆，再翻面",
        "不看答案，说出为什么商品价格下降通常会使需求量增加。",
        "其他条件不变时，价格下降会让更多消费者愿意且能够购买，因此需求曲线上的对应点向右移动。",
        "先区分商品自身价格与收入、偏好等其他条件。",
        listOf(
            "先区分商品自身价格与收入、偏好等其他条件。",
            "再想：价格下降时，愿意买的人和买得起的人分别会怎么变？",
        ))
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
    private val borrowing = TeachingChart("borrowing", "图书角借阅量", "月份", "借阅量（次）", listOf(
        TeachingPoint("一月", 24.0), TeachingPoint("二月", 40.0), TeachingPoint("三月", 32.0), TeachingPoint("四月", 52.0)
    ))
    private val bridge = TeachingText("bridge", "从观察到解释",
        "**四月借阅最多**是数据直接支持的观察。至于为什么增多，还需要了解活动、开放时间等条件。")
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
        BuiltInTeachingLesson("structure", 2_001, "结构与过程", "结构", "组合时间轴与结构图，理解事件顺序和组成关系。",
            EntryIcon.BOOK, "同一会话中组合时间线与结构图。先切换关注点，再比较表达方式。",
            listOf(
                BuiltInTeachingItem(1_001, timeline),
                BuiltInTeachingItem(1_023, writingFigure),
                BuiltInTeachingItem(1_027, hiringFunnel),
            )),
        BuiltInTeachingLesson("data", 2_002, "数据图表", "图表", "在同一会话中预测、比较并观察折线、柱状和面积图。",
            EntryIcon.CHART, "同一会话展示需求预测、趋势、分类比较和总量面积。切换表达时核对数值、单位与坐标。",
            listOf(
                BuiltInTeachingItem(1_004, demand),
                BuiltInTeachingItem(1_016, line),
                BuiltInTeachingItem(1_009, bar),
                BuiltInTeachingItem(1_010, area),
                BuiltInTeachingItem(1_024, toolMatrix),
                BuiltInTeachingItem(1_025, deliveryRange),
                BuiltInTeachingItem(1_026, quadratic),
                BuiltInTeachingItem(1_028, studyScatter),
                BuiltInTeachingItem(1_029, monthlyWaterfall),
            )),
        BuiltInTeachingLesson("practice", 2_003, "互动练习", "练习", "在一条会话中体验答题、翻卡、排序和小游戏。",
            EntryIcon.TARGET, "统一遵循先提示、允许重试、主动查看完整答案。选择任意组件开始练习。",
            listOf(
                BuiltInTeachingItem(1_005, quiz),
                BuiltInTeachingItem(1_011, flip),
                BuiltInTeachingItem(1_012, sorting),
                BuiltInTeachingItem(1_022, TeachingGame("workload-game", "分配研究工时", "在有限工时下权衡访谈、测试与分析。",
                    "把 6 块工时拖到三个任务，正好满足各自需求。",
                    """const NEED=[2,3,1],NAME=['访谈','测试','分析'],TONE=['#c9764a','#3f7fa6','#6a9a63'];let a=[0,0,0],used=[0,0,0,0,0,0],drag=-1,gx=0,gy=0,glow=0,done=false;const cardX=i=>4+i*32,chipX=i=>12+i*13;function slot(p){for(let i=0;i<3;i++){if(p.x>cardX(i)&&p.x<cardX(i)+28&&p.y>32&&p.y<62)return i;}return -1;}function check(){if(a[0]===NEED[0]&&a[1]===NEED[1]&&a[2]===NEED[2]){done=true;draw();game.finish('6 块工时刚好覆盖三项研究：先保证访谈拿到一手证据，再做可用性测试，最后留 1 小时分析。');}}function draw(){const n=a[0]+a[1]+a[2],c=[['rect',0,0,100,100,'#f5efe4'],['text','研究工时分配',50,9,5,'#2a4d5e','center','bold'],['rect',20,17,60,5,'#e2d6c4',2.5,0],['rect',20,17,60*n/6,5,'#3f8f6b',2.5,0],['text','已分配 '+n+'/6 小时',50,25,3,'#6b7a83','center']];for(let i=0;i<3;i++){const x=cardX(i),need=NEED[i],full=a[i]===need;c.push(['rect',x,32,28,30,'#fffdf8',4,0]);c.push(['rect',x,32,28,4,TONE[i],4,0]);c.push(['text',NAME[i],x+14,41,3.6,'#33525f','center','bold']);c.push(['text','需 '+need+' 小时',x+14,47.5,2.8,'#8a9aa3','center']);for(let k=0;k<need;k++)c.push(['circle',x+14+(k-(need-1)/2)*7,55,2.7,a[i]>k?'#3f8f6b':'#dde5e8']);c.push(['text',a[i]+'/'+need,x+14,59.5,2.8,full?'#2f8f6b':'#7a8b93','center','bold']);}for(let i=0;i<6;i++){if(used[i]||i===drag)continue;const y=79+Math.sin(glow+i)*0.25;c.push(['rect',chipX(i)-4.5,y-3,9,6,'#8fb7c9',2,0]);c.push(['text','1h',chipX(i),y,2.4,'#ffffff','center','bold']);}if(drag>=0){c.push(['rect',gx-5,gy-3.5,10,7,'#276B91',2.5,0]);c.push(['text','1h',gx,gy,2.6,'#ffffff','center','bold']);}c.push(['text',done?'分配完成：预算正好覆盖三项研究':'拖动蓝色工时块到任务卡',50,91,3.2,done?'#2f8f6b':'#6b7a83','center','bold']);game.draw(c);}game.onPointer(p=>{if(p.phase==='down'&&drag<0){let best=-1,bd=9;for(let i=0;i<6;i++){if(used[i])continue;const d=Math.hypot(p.x-chipX(i),p.y-79);if(d<bd){bd=d;best=i;}}if(best>=0){drag=best;gx=p.x;gy=p.y;draw();}}else if(p.phase==='move'&&drag>=0){gx=p.x;gy=p.y;draw();}else if(p.phase==='up'&&drag>=0){const s=slot(p);if(s>=0&&a[s]<NEED[s]){a[s]++;used[drag]=1;}else if(s>=0){game.say(NAME[s]+'已经够了，换一个任务');}drag=-1;draw();check();}});game.onFrame(t=>{if(drag>=0)glow=(glow+0.2)%6.283;else glow=(glow+0.05)%6.283;});draw();""")),
            )),
        BuiltInTeachingLesson("visual", 2_004, "图像与综合", "综合", "组合图片热点、数据观察、文字解释、对比、判断题与杠杆实验。",
            EntryIcon.LEAF, "先探索植物细胞热点，再回到数据观察与解释，比较不同媒介如何承担讲解。",
            listOf(
                BuiltInTeachingItem(1_014, hotspot),
                BuiltInTeachingItem(1_017, borrowing),
                BuiltInTeachingItem(1_018, bridge),
                BuiltInTeachingItem(1_020, mixedQuiz),
                BuiltInTeachingItem(1_021, TeachingExperiment("lever", "杠杆实验台",
                    "移动两侧重物或调整力，试着让杠杆平衡。")),
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
