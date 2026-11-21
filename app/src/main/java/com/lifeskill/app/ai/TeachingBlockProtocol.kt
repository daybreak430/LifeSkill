package com.lifeskill.app.ai

import com.lifeskill.app.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object TeachingBlockProtocol {
    private val identifier = Regex("[A-Za-z0-9_-]{1,40}")

    // 单个组件坏数据只降级该组件，reply 仍可阅读；数量、文本、引用与有限数值均在进入 UI 前校验。
    fun decode(array: JSONArray?): List<TeachingBlock> {
        if (array == null || array.length() !in 1..16 || array.toString().length > 48_000) return emptyList()
        val seen = mutableSetOf<String>()
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                val id = json.key("id")
                require(seen.add(id))
                val title = json.text("title", 80)
                when (json.text("type", 24)) {
                    "text" -> TeachingText(id, title, json.text("text", 2_000))
                    "timeline" -> TeachingTimeline(id, title, json.array("events", 2..8).objects().map { event ->
                        TimelineEvent(event.key("id"), event.text("date", 40), event.text("title", 80), event.text("detail", 600))
                    }.also { require(it.map(TimelineEvent::id).distinct().size == it.size) })
                    "flow" -> {
                        val diagram = json.text("diagram", 2_000)
                        val flow = checkNotNull(LessonFlow.parse(diagram, mermaidFence = true))
                        require(flow.labels.size <= 8)
                        val details = json.getJSONObject("details")
                        require(details.keys().asSequence().toSet() == flow.labels.keys)
                        TeachingFlow(id, title, diagram, flow.labels.keys.associateWith { details.text(it, 600) })
                    }
                    "compare" -> {
                        val columns = json.array("columns", 2..3).strings(60)
                        require(columns.distinct().size == columns.size)
                        val dimensions = json.array("dimensions", 1..5).objects().map { dimension ->
                            ComparisonDimension(dimension.key("id"), dimension.text("title", 60),
                                dimension.array("values", columns.size..columns.size).strings(400))
                        }
                        require(dimensions.map(ComparisonDimension::id).distinct().size == dimensions.size)
                        TeachingComparison(id, title, columns, dimensions,
                            ComparisonPalette.entries.firstOrNull { it.name.equals(json.optString("palette"), true) } ?: ComparisonPalette.BLUE_AMBER)
                    }
                    "chart" -> {
                        val mode = when (json.optString("model", "data")) {
                            "data" -> TeachingChartMode.DATA
                            "linear_demand" -> TeachingChartMode.LINEAR_DEMAND
                            else -> error("不支持的计算模板")
                        }
                        val points = if (mode == TeachingChartMode.DATA) json.array("points", 2..12).objects().map { point ->
                            val value = (point.get("value") as Number).toDouble()
                            require(value.isFinite() && value in -1_000_000.0..1_000_000.0)
                            TeachingPoint(point.text("label", 40), value)
                        } else emptyList()
                        val unit = if (json.has("quantityUnit")) json.text("quantityUnit", 12) else "单位"
                        // 固定模板的轴含义由应用保证；缺少数量单位时使用中性标签，不能把券、件等误标成杯。
                        TeachingChart(id, title,
                            if (mode == TeachingChartMode.LINEAR_DEMAND) "需求量（$unit）" else json.text("xLabel", 40),
                            if (mode == TeachingChartMode.LINEAR_DEMAND) "价格（元）" else json.text("yLabel", 40), points, mode,
                            json.optJSONObject("prediction")?.question(), unit,
                            if (mode == TeachingChartMode.LINEAR_DEMAND) TeachingChartStyle.LINE else
                                TeachingChartStyle.entries.firstOrNull { it.name.equals(json.optString("style"), true) } ?: TeachingChartStyle.LINE)
                    }
                    "quiz" -> TeachingQuiz(id, title, json.getJSONObject("question").question())
                    "flip" -> TeachingFlipCard(id, title, json.text("prompt", 600), json.text("answer", 1_200),
                        json.text("hint", 400))
                    "sort" -> {
                        val items = json.array("items", 3..6).objects().map { item ->
                            TeachingSortItem(item.key("id"), item.text("text", 300))
                        }
                        val itemIds = items.map(TeachingSortItem::id)
                        require(itemIds.distinct().size == itemIds.size)
                        val correctOrder = json.array("correctOrder", items.size..items.size).strings(40)
                        require(correctOrder.distinct().size == correctOrder.size && correctOrder.toSet() == itemIds.toSet())
                        TeachingSorting(id, title, json.text("prompt", 600), items, correctOrder,
                            json.text("hint", 400), json.text("explanation", 1_200))
                    }
                    "classify" -> {
                        val categories = json.array("categories", 2..3).objects().map { category ->
                            ClassificationCategory(category.key("id"), category.text("title", 60))
                        }
                        require(categories.map(ClassificationCategory::id).distinct().size == categories.size)
                        val categoryIds = categories.map(ClassificationCategory::id).toSet()
                        val items = json.array("items", 3..6).objects().map { item ->
                            ClassificationItem(item.key("id"), item.text("text", 300), item.key("category"),
                                item.text("explanation", 400))
                        }
                        require(items.map(ClassificationItem::id).distinct().size == items.size)
                        require(items.all { it.categoryId in categoryIds })
                        TeachingClassification(id, title, json.text("prompt", 600), categories, items,
                            json.text("hint", 400), json.text("explanation", 1_200))
                    }
                    "hotspot" -> {
                        val image = when (json.text("image", 32)) {
                            "plant_cell" -> TeachingHotspotImage.PLANT_CELL
                            else -> error("不支持的热点图片")
                        }
                        val hotspots = json.array("hotspots", 2..6).objects().map { hotspot ->
                            ImageHotspot(hotspot.key("id"), hotspot.text("label", 60),
                                hotspot.number("x", 0.0..100.0), hotspot.number("y", 0.0..100.0),
                                hotspot.text("detail", 600))
                        }
                        require(hotspots.map(ImageHotspot::id).distinct().size == hotspots.size)
                        TeachingHotspot(id, title, json.text("prompt", 600), image, hotspots,
                            json.text("hint", 400))
                    }
                    "steps" -> {
                        val steps = json.array("steps", 2..5).objects().map { step ->
                            TeachingTraceStep(step.key("id"), step.text("title", 80),
                                step.getJSONObject("checkpoint").question(), step.text("result", 800))
                        }
                        require(steps.map(TeachingTraceStep::id).distinct().size == steps.size)
                        TeachingStepTrace(id, title, json.text("introduction", 600), steps,
                            json.text("summary", 1_000))
                    }
                    else -> null
                }
            }.getOrNull()
        }
    }

    fun encode(blocks: List<TeachingBlock>): JSONArray = JSONArray().apply {
        blocks.forEach { block -> put(JSONObject().put("id", block.id).put("title", block.title).apply {
            when (block) {
                is TeachingText -> put("type", "text").put("text", block.text)
                is TeachingTimeline -> put("type", "timeline").put("events", JSONArray().apply {
                    block.events.forEach { put(JSONObject().put("id", it.id).put("date", it.date).put("title", it.title).put("detail", it.detail)) }
                })
                is TeachingFlow -> put("type", "flow").put("diagram", block.diagram).put("details", JSONObject(block.details))
                is TeachingComparison -> put("type", "compare").put("columns", JSONArray(block.columns)).put("palette", block.palette.name.lowercase())
                    .put("dimensions", JSONArray().apply { block.dimensions.forEach {
                        put(JSONObject().put("id", it.id).put("title", it.title).put("values", JSONArray(it.values)))
                    } })
                is TeachingChart -> {
                    put("type", "chart").put("model", if (block.mode == TeachingChartMode.DATA) "data" else "linear_demand")
                        .put("xLabel", block.xLabel).put("yLabel", block.yLabel)
                        .put("quantityUnit", block.quantityUnit).put("style", block.style.name.lowercase())
                        .put("points", JSONArray().apply { block.points.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
                    block.prediction?.let { put("prediction", it.json()) }
                }
                is TeachingQuiz -> put("type", "quiz").put("question", block.question.json())
                is TeachingFlipCard -> put("type", "flip").put("prompt", block.prompt).put("answer", block.answer)
                    .put("hint", block.hint)
                is TeachingSorting -> {
                    put("type", "sort").put("prompt", block.prompt).put("items", JSONArray().apply {
                        block.items.forEach { put(JSONObject().put("id", it.id).put("text", it.text)) }
                    }).put("correctOrder", JSONArray(block.correctOrder)).put("hint", block.hint)
                        .put("explanation", block.explanation)
                }
                is TeachingClassification -> {
                    put("type", "classify").put("prompt", block.prompt).put("categories", JSONArray().apply {
                        block.categories.forEach { put(JSONObject().put("id", it.id).put("title", it.title)) }
                    }).put("items", JSONArray().apply {
                        block.items.forEach {
                            put(JSONObject().put("id", it.id).put("text", it.text).put("category", it.categoryId)
                                .put("explanation", it.explanation))
                        }
                    }).put("hint", block.hint).put("explanation", block.explanation)
                }
                is TeachingHotspot -> {
                    put("type", "hotspot").put("prompt", block.prompt).put("image", block.image.name.lowercase())
                        .put("hotspots", JSONArray().apply {
                            block.hotspots.forEach {
                                put(JSONObject().put("id", it.id).put("label", it.label)
                                    .put("x", it.horizontalPercent).put("y", it.verticalPercent).put("detail", it.detail))
                            }
                        }).put("hint", block.hint)
                }
                is TeachingStepTrace -> {
                    put("type", "steps").put("introduction", block.introduction).put("steps", JSONArray().apply {
                        block.steps.forEach {
                            put(JSONObject().put("id", it.id).put("title", it.title)
                                .put("checkpoint", it.checkpoint.json()).put("result", it.result))
                        }
                    }).put("summary", block.summary)
                }
            }
        }) }
    }

    fun encodeStates(states: Map<String, TeachingState>): String = JSONObject().apply {
        states.forEach { (id, state) -> put(id, JSONObject().put("selection", state.selection).put("value", state.value)
            .put("submitted", state.submitted).put("attempts", state.attempts).put("prediction", state.prediction)
            .put("revealed", state.revealed).put("order", JSONArray(state.order))
            .put("placements", JSONObject(state.placements)).put("step", state.step)) }
    }.toString()

    fun decodeStates(raw: String?): Map<String, TeachingState> = runCatching {
        if (raw == null || raw.length > 48_000) return emptyMap()
        val json = JSONObject(raw)
        require(json.length() <= 16)
        json.keys().asSequence().associateWith { id ->
            val state = json.getJSONObject(id)
            val value = state.opt("value") as? Number
            require(value == null || value.toDouble().isFinite())
            val order = if (state.isNull("order")) emptyList() else state.getJSONArray("order").let { array ->
                require(array.length() <= 8)
                List(array.length()) { index -> array.getString(index) }.also { values ->
                    require(values.all { it.length in 1..40 })
                }
            }
            val placements = if (state.isNull("placements")) emptyMap() else state.getJSONObject("placements").let { objectValue ->
                require(objectValue.length() <= 8)
                objectValue.keys().asSequence().associateWith { key ->
                    require(key.length in 1..40)
                    objectValue.getString(key).also { require(it.length in 1..40) }
                }
            }
            TeachingState(
                selection = if (state.isNull("selection")) null else state.optString("selection"),
                value = value?.toDouble(), submitted = state.optBoolean("submitted"),
                attempts = state.optInt("attempts").coerceIn(0, 1_000),
                prediction = (state.opt("prediction") as? Number)?.toInt(),
                revealed = state.optBoolean("revealed"), order = order, placements = placements,
                step = state.optInt("step").coerceAtLeast(0),
            )
        }
    }.getOrDefault(emptyMap())

    private fun JSONObject.question(): TeachingQuestion {
        val options = array("options", 2..4).strings(160)
        val answer = (get("answer") as Number).toDouble()
        require(answer.isFinite() && answer == answer.toInt().toDouble() && answer.toInt() in options.indices)
        require(options.distinct().size == options.size)
        return TeachingQuestion(text("text", 600), options, answer.toInt(), text("explanation", 800), text("hint", 400))
    }
    private fun TeachingQuestion.json() = JSONObject().put("text", question).put("options", JSONArray(options))
        .put("answer", answer).put("explanation", explanation).put("hint", hint)
    private fun JSONObject.key(name: String): String = text(name, 40).also { require(identifier.matches(it)) }
    private fun JSONObject.text(name: String, limit: Int): String = (get(name) as String).trim().also {
        require(it.length in 1..limit && '\u0000' !in it)
    }
    private fun JSONObject.number(name: String, range: ClosedFloatingPointRange<Double>): Double =
        (get(name) as Number).toDouble().also { require(it.isFinite() && it in range) }
    private fun JSONObject.array(name: String, range: IntRange): JSONArray = getJSONArray(name).also { require(it.length() in range) }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(limit: Int): List<String> = List(length()) { (get(it) as String).trim().also { value -> require(value.length in 1..limit) } }

    val instructions = """
        可在 teach 或 offer_check 的顶层 JSON 增加 blocks 数组，把讲解与原生互动组件组合。reply 必须保留可独立阅读的文字解释，组件不可用时仍能读懂。最多3个组件，仅在有助理解时使用，普通追问可只回文字。用户要求图表、时间轴、比较、流程、翻卡、排序、分类、图片热点、分步推演或随堂互动时优先使用对应组件。组件 id 为本条回复内唯一的英文标识，每个组件带 title。组件只描述数据，禁止生成 HTML、JavaScript、可执行表达式或外部资源链接。
        先用课程主题、本节目标和用户当前问题判断“这一小段最难理解或最需要练习什么”，再选择组件；不要为了热闹每轮都放组件。没有匹配的教学意图时只用文字。每次教学回复最多用2个组件，只有用户明确要求组合演示时才可用3个。一次只选一个主组件承担核心表达；第二个组件必须带来新的认知动作。不要把同一顺序同时做成flow和sort，不要把同一概念做成compare和quiz后再重复说明，也不要在首次讲解时把“讲解＋图表＋对比＋测验”一次全部塞入。
        主题与组件选择参考：
        - 历史、事件、阶段、制度演变：timeline；需要比较原因、影响或易混概念时加compare。
        - 操作步骤、交易链路、判断分支、反馈循环：flow；需要让学生亲自恢复顺序时用sort。
        - 两个概念、方案、优缺点或易混项：compare。
        - 定量趋势、分类比较、累计走势、价格与数量关系：chart，并按line/bar/area或固定linear_demand模板选择。
        - 定义、条件、公式前提需要先回忆：flip。
        - 证据、责任、类别、观察与解释容易混淆：classify。
        - 需要连续计算、因果链、基数变化或“下一步会发生什么”：steps。
        - 只需快速检验一个关键误区：quiz。
        - 只有教学内容确实关于植物细胞结构时才使用hotspot/plant_cell；此时优先以hotspot作为主组件，不要改用无关热点图。其他空间主题不得套用该图。
        外行人文学科可优先timeline、compare、flip；科学和数据分析可优先chart、classify、steps；操作技能可优先flow、sort；语言学习可优先flip、compare、quiz。参考只用于判断，不能覆盖本节实际目标。
        首轮讲解新概念时优先选一个主组件；把quiz、sort、classify等练习留到讲解完成、学生需要尝试或用户明确要求练习时，同一轮不要用练习组件替代必要讲解。
        支持以下格式（示例只说明协议，具体内容按本节目标生成）：
        使用 flow 组件时，reply 只保留文字概括，不再重复绘制同一流程；组件的 diagram 字段就是 Mermaid。没有 flow 组件时，文字中的流程仍沿用原来的 Mermaid 代码围栏规则。
        {"id":"t","type":"timeline","title":"事件顺序","events":[{"id":"e1","date":"第一天","title":"提出问题","detail":"背景与影响"},{"id":"e2","date":"第二天","title":"验证假设","detail":"观察结果"}]}
        {"id":"f","type":"flow","title":"操作流程","diagram":"flowchart TD\nA[核对] --> B{一致}\nB -->|是| C[归档]\nB -->|否| D[复核]\nD --> A","details":{"A":"核对依据","B":"判断条件","C":"完成后的产物","D":"如何复核"}}
        {"id":"c","type":"compare","title":"对比","columns":["概念一","概念二"],"dimensions":[{"id":"cause","title":"变化原因","values":["第一项的原因","第二项的原因"]}]}
        {"id":"p","type":"chart","title":"趋势","model":"data","xLabel":"年份","yLabel":"数量（件）","points":[{"label":"2024","value":20},{"label":"2025","value":30}]}
        {"id":"q","type":"quiz","title":"随堂练习","question":{"text":"完整问题","options":["选项一","选项二"],"answer":0,"hint":"不直接揭示答案的线索","explanation":"原理与解释"}}
        {"id":"r","type":"flip","title":"先回忆再看","prompt":"不看答案，说明这个概念的核心条件。","answer":"完整答案","hint":"先关注适用条件。"}
        {"id":"s","type":"sort","title":"排出顺序","prompt":"调整为合理步骤。","items":[{"id":"a","text":"第一步文本"},{"id":"b","text":"第二步文本"},{"id":"c","text":"第三步文本"}],"correctOrder":["a","b","c"],"hint":"先确定起点。","explanation":"完整顺序和原因"}
        {"id":"k","type":"classify","title":"归入类别","prompt":"为每项选择类别。","categories":[{"id":"observe","title":"观察"},{"id":"explain","title":"解释"}],"items":[{"id":"i1","text":"可直接读到的变化","category":"observe","explanation":"为何属于该类别"},{"id":"i2","text":"一种原因假设","category":"explain","explanation":"为何属于该类别"},{"id":"i3","text":"另一个可直接观察的结果","category":"observe","explanation":"为何属于该类别"}],"hint":"先看内容是否可直接观察到。","explanation":"完整分类依据"}
        {"id":"h","type":"hotspot","title":"植物细胞结构","prompt":"点击热点查看局部作用。","image":"plant_cell","hotspots":[{"id":"nucleus","label":"细胞核","x":50,"y":45,"detail":"局部讲解"},{"id":"wall","label":"细胞壁","x":12,"y":20,"detail":"局部讲解"}],"hint":"先找中央的控制中心。"}
        {"id":"z","type":"steps","title":"分步推演","introduction":"先预测每一步结果。","steps":[{"id":"s1","title":"第一次变化","checkpoint":{"text":"这一步会怎样？","options":["增加","减少"],"answer":1,"hint":"只比较相邻两个数。","explanation":"该步的解释"},"result":"该步展开后的结果"},{"id":"s2","title":"第二次变化","checkpoint":{"text":"相对哪个基数？","options":["原数","中间结果"],"answer":1,"hint":"看变化从哪个数开始。","explanation":"该步的解释"},"result":"第二步展开后的结果"}],"summary":"完整结论"}
        compare 可选 palette 为 blue_amber、violet_teal、rose_sage，按对比主题选择。chart/data 可选 style 为 line（趋势）、bar（分类比较）、area（总量趋势），默认line；样式不改变数值和单位，line和area按给定顺序连接，不能用面积暗示未经提供的累计数据。
        也支持 type:text，字段为 text（Markdown），用于组件之间插入短讲解。时间轴2–8个事件；流程最多8个节点，每个节点的id必须在details中有解释；对比2–3列、1–5个维度，每个values与columns等长；数据图2–12个有限数值，注明单位，虚构数据必须在reply中说明是教学示例。排序3–6项，correctOrder必须原样包含每个item id且不重复；分类2–3类、3–6项，每项必须指向一个类别并提供独立解释；分步推演2–5步，每步都有一个先预测后展开的checkpoint。热点图片当前只支持 plant_cell，坐标x、y为0–100的百分比，2–6个热点，不得提交外部图片URL。
        反馈统一采用“先提示、允许重试、由学生主动查看完整答案”：错误反馈先给不解题的hint，不要直接在reply重复正确答案；explanation和correctOrder等答案字段只供原生组件在学生查看答案后使用。
        价格与需求量专题可使用 chart 的 model:linear_demand，不填points。它是应用固定的 Q=120−10P 模板，P范围2–10元，仅可用于其他条件不变的线性需求教学，不得用于其他公式或专业工程模拟。填写 quantityUnit（如杯、件、张）明确Q的数量单位，横轴固定为需求量Q，纵轴固定为价格P。可加 prediction（与quiz的question对象同结构），先预测再开放滑块。其他图表不可声称能计算任意公式。
        quiz 是随堂反馈，点击和答对均不能点亮课时。正式课后练习继续按原有kind、question、choices、exerciseId协议，不得用blocks绕过题序。正式评估或待答题期间不要附加blocks。历史中的互动记录仅是学习证据，不能单独作为通过课后练习的依据。
    """.trimIndent()
}
