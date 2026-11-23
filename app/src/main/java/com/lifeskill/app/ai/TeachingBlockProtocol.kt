package com.lifeskill.app.ai

import com.lifeskill.app.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object TeachingBlockProtocol {
    private val identifier = Regex("[A-Za-z0-9_-]{1,40}")

    // 单个组件坏数据只降级该组件，reply 仍可阅读；数量、文本、引用与有限数值均在进入 UI 前校验。
    fun decode(array: JSONArray?): List<TeachingBlock> {
        // 整组组件的上限随小游戏代码预算一起放宽：一个 16000 字符的脚本加少量其他组件仍在界内。
        if (array == null || array.length() !in 1..16 || array.toString().length > 72_000) return emptyList()
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
                    "compare" -> {                        val columns = json.array("columns", 2..3).strings(60)
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
                        val style = if (mode == TeachingChartMode.LINEAR_DEMAND) TeachingChartStyle.LINE else
                            TeachingChartStyle.entries.firstOrNull { it.name.equals(json.optString("style"), true) }
                                ?: TeachingChartStyle.LINE
                        val points = if (mode == TeachingChartMode.DATA) json.array("points", 2..12).objects().map { point ->
                            val value = (point.get("value") as Number).toDouble()
                            require(value.isFinite() && value in -1_000_000.0..1_000_000.0)
                            val horizontal = if (point.has("x")) {
                                (point.get("x") as Number).toDouble().also {
                                    require(it.isFinite() && it in -1_000_000.0..1_000_000.0)
                                }
                            } else null
                            // 散点必须有自变量 x；其他样式忽略 x，横轴仍按顺序排列。
                            require(style != TeachingChartStyle.SCATTER || horizontal != null)
                            TeachingPoint(point.text("label", 40), value, horizontal, point.optBoolean("total", false))
                        }.also { list ->
                            if (style == TeachingChartStyle.WATERFALL) {
                                // 瀑布图首尾必须是合计，中间至少一个增减量，否则累计基线无从计算。
                                require(list.size in 3..10)
                                require(list.first().total && list.last().total)
                                require(list.any { !it.total })
                            }
                        } else emptyList()
                        val unit = if (json.has("quantityUnit")) json.text("quantityUnit", 12) else "单位"
                        // 固定模板的轴含义由应用保证；缺少数量单位时使用中性标签，不能把券、件等误标成杯。
                        TeachingChart(id, title,
                            if (mode == TeachingChartMode.LINEAR_DEMAND) "需求量（$unit）" else json.text("xLabel", 40),
                            if (mode == TeachingChartMode.LINEAR_DEMAND) "价格（元）" else json.text("yLabel", 40), points, mode,
                            json.optJSONObject("prediction")?.question(), unit, style)
                    }
                    "matrix" -> {
                        val rowLabels = json.array("rows", 2..6).strings(12)
                        val columnLabels = json.array("columns", 2..5).strings(10)
                        val grid = json.getJSONArray("values")
                        require(grid.length() == rowLabels.size)
                        val values = List(grid.length()) { rowIndex ->
                            val line = grid.getJSONArray(rowIndex)
                            require(line.length() == columnLabels.size)
                            List(line.length()) { columnIndex ->
                                val score = (line.get(columnIndex) as Number).toDouble()
                                require(score.isFinite() && score in 0.0..5.0)
                                score
                            }
                        }
                        TeachingMatrix(id, title, rowLabels, columnLabels, values, json.caption())
                    }
                    "range" -> {
                        val minimum = json.number("min", -1_000_000.0..1_000_000.0)
                        val maximum = json.number("max", -1_000_000.0..1_000_000.0)
                        require(maximum > minimum)
                        val low = json.number("low", minimum..maximum)
                        val high = json.number("high", low..maximum)
                        val current = if (json.has("value")) json.number("value", minimum..maximum) else null
                        TeachingRange(
                            id, title, json.text("label", 24), minimum, maximum, low, high, current,
                            json.optString("unit").trim().take(6), json.caption(),
                        )
                    }
                    "funnel" -> {
                        val mode = when (json.text("mode", 16)) {
                            "funnel" -> FunnelMode.FUNNEL
                            "pyramid" -> FunnelMode.PYRAMID
                            else -> error("不支持的层级图模式")
                        }
                        val stages = json.array("stages", 2..5).objects().map { stage ->
                            FunnelStage(
                                stage.text("label", 10),
                                stage.optString("value").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 8) },
                                stage.number("ratio", 5.0..100.0).toInt(),
                            )
                        }
                        val steps = stages.map(FunnelStage::ratio).zipWithNext()
                        // 漏斗逐级收窄、金字塔逐级放宽，方向反了就不是同一张图。
                        if (mode == FunnelMode.FUNNEL) require(steps.all { (up, down) -> up >= down })
                        else require(steps.all { (up, down) -> up <= down })
                        TeachingFunnel(id, title, mode, stages, json.caption())
                    }
                    "formula" -> TeachingFormula(id, title, json.array("lines", 1..6).strings(200), json.caption())
                    "quadrant" -> {
                        val quadrants = json.array("quadrants", 4..4).strings(8)
                        val points = json.array("points", 1..8).objects().map { point ->
                            QuadrantPoint(
                                point.text("id", 24), point.text("label", 12),
                                point.number("x", 0.0..100.0), point.number("y", 0.0..100.0),
                                point.optString("detail").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 40) },
                            )
                        }
                        require(points.map(QuadrantPoint::id).toSet().size == points.size)
                        TeachingQuadrant(id, title, json.text("xLabel", 16), json.text("yLabel", 16), quadrants, points, json.caption())
                    }
                    "figure" -> {
                        val rows = json.array("rows", 1..6).objects().map { row ->
                            val relation = row.optString("arrow").trim()
                            if (relation.isNotEmpty()) {
                                require(relation.length <= 60 && '\u0000' !in relation)
                                FigureRow.Relation(relation)
                            } else {
                                val cols = row.optInt("cols", 0)
                                require(cols in 1..3)
                                FigureRow.Nodes(row.array("items", cols..cols).objects().map { item ->
                                    FigureItem(
                                        title = item.text("title", 20),
                                        value = item.optString("value").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 8) },
                                        note = item.optString("note").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 40) },
                                        items = item.optJSONArray("items")?.let { list ->
                                            require(list.length() in 1..2)
                                            List(list.length()) { index ->
                                                (list.get(index) as String).trim().also { require(it.length in 1..30) }
                                            }
                                        } ?: emptyList(),
                                        tone = item.optString("tone").trim().takeIf(String::isNotEmpty)?.let { name ->
                                            FigureTone.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                                                ?: error("不支持的语气色")
                                        },
                                    )
                                })
                            }
                        }
                        TeachingFigure(id, title, rows,
                            json.optString("caption").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 120) })
                    }
                    "game" -> {
                        val code = json.get("code") as? String ?: error("小游戏代码必须是文本")
                        require(validGameCode(code))
                        TeachingGame(id, title, json.text("goal", 240), json.text("instructions", 160), code)
                    }
                    "experiment" -> {
                        val model = ExperimentModel.from(json.optString("model").ifBlank { "lever" })
                            ?: error("未知的实验模型")
                        val initial = json.getJSONObject("initial").experimentValues(model)
                        TeachingExperiment(id, title, json.text("prompt", 600), model, initial)
                    }
                    "quiz" -> TeachingQuiz(id, title, json.getJSONObject("question").question())
                    "flip" -> TeachingFlipCard(id, title, json.text("prompt", 600), json.text("answer", 1_200),
                        json.optString("hint").trim().take(400), json.hintList())
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
                        .put("points", JSONArray().apply { block.points.forEach { point ->
                            put(JSONObject().put("label", point.label).put("value", point.value).apply {
                                point.x?.let { put("x", it) }
                                if (point.total) put("total", true)
                            })
                        } })
                    block.prediction?.let { put("prediction", it.json()) }
                }
                is TeachingQuiz -> put("type", "quiz").put("question", block.question.json())
                is TeachingFlipCard -> put("type", "flip").put("prompt", block.prompt).put("answer", block.answer)
                    .put("hint", block.hintLevels.firstOrNull().orEmpty())
                    .apply { if (block.hintLevels.size > 1) put("hints", JSONArray(block.hintLevels)) }
                is TeachingSorting -> {
                    put("type", "sort").put("prompt", block.prompt).put("items", JSONArray().apply {
                        block.items.forEach { put(JSONObject().put("id", it.id).put("text", it.text)) }
                    }).put("correctOrder", JSONArray(block.correctOrder)).put("hint", block.hint)
                        .put("explanation", block.explanation)
                }
                is TeachingQuadrant -> {
                    put("type", "quadrant").put("xLabel", block.xLabel).put("yLabel", block.yLabel)
                        .put("quadrants", JSONArray(block.quadrants))
                        .put("points", JSONArray().apply {
                            block.points.forEach { point ->
                                put(JSONObject().put("id", point.id).put("label", point.label)
                                    .put("x", point.x).put("y", point.y).apply {
                                        point.detail?.let { put("detail", it) }
                                    })
                            }
                        })
                    block.caption?.let { put("caption", it) }
                }
                is TeachingMatrix -> {
                    put("type", "matrix").put("rows", JSONArray(block.rows)).put("columns", JSONArray(block.columns))
                        .put("values", JSONArray().apply {
                            block.values.forEach { line -> put(JSONArray(line)) }
                        })
                    block.caption?.let { put("caption", it) }
                }
                is TeachingRange -> {
                    put("type", "range").put("label", block.label).put("min", block.minimum).put("max", block.maximum)
                        .put("low", block.low).put("high", block.high)
                    block.value?.let { put("value", it) }
                    if (block.unit.isNotEmpty()) put("unit", block.unit)
                    block.caption?.let { put("caption", it) }
                }
                is TeachingFunnel -> {
                    put("type", "funnel").put("mode", block.mode.name.lowercase())
                        .put("stages", JSONArray().apply {
                            block.stages.forEach { stage ->
                                put(JSONObject().put("label", stage.label).put("ratio", stage.ratio).apply {
                                    stage.value?.let { put("value", it) }
                                })
                            }
                        })
                    block.caption?.let { put("caption", it) }
                }
                is TeachingFormula -> {
                    put("type", "formula").put("lines", JSONArray(block.lines))
                    block.caption?.let { put("caption", it) }
                }
                is TeachingFigure -> {
                    put("type", "figure").put("rows", JSONArray().apply {
                        block.rows.forEach { row ->
                            when (row) {
                                is FigureRow.Nodes -> put(JSONObject().put("cols", row.items.size).put("items", JSONArray().apply {
                                    row.items.forEach { item ->
                                        put(JSONObject().put("title", item.title).apply {
                                            item.value?.let { put("value", it) }
                                            item.note?.let { put("note", it) }
                                            if (item.items.isNotEmpty()) put("items", JSONArray(item.items))
                                            item.tone?.let { put("tone", it.name.lowercase()) }
                                        })
                                    }
                                }))
                                is FigureRow.Relation -> put(JSONObject().put("arrow", row.text))
                            }
                        }
                    })
                    block.caption?.let { put("caption", it) }
                }
                is TeachingGame -> {
                    put("type", "game").put("goal", block.goal).put("instructions", block.instructions).put("code", block.code)
                }
                is TeachingExperiment -> {
                    put("type", "experiment").put("model", block.model.id).put("prompt", block.prompt)
                        .put("initial", JSONObject(block.initial.values))
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
            .put("placements", JSONObject(state.placements)).put("step", state.step)
            .put("experiment", state.experiment?.json()).put("gameOpened", state.gameOpened)
            .put("hintLevel", state.hintLevel)
            .put("positions", JSONObject(state.positions))) }
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
            val positions = if (state.isNull("positions")) emptyMap() else state.getJSONObject("positions").let { objectValue ->
                require(objectValue.length() <= 8)
                objectValue.keys().asSequence().associateWith { key ->
                    require(key.length in 1..40)
                    val array = objectValue.getJSONArray(key)
                    require(array.length() == 2)
                    List(2) { index ->
                        (array.get(index) as Number).toDouble().also { require(it in 0.0..100.0) }
                    }
                }
            }
            TeachingState(
                selection = if (state.isNull("selection")) null else state.optString("selection"),
                experiment = state.optJSONObject("experiment")?.experimentValues()
                    ?: state.optJSONObject("lever")?.let { legacy ->
                        // 0.15.33 之前只保存 lever 字段，读取时映射成同一模型的参数表。
                        ExperimentValues(ExperimentModel.LEVER, legacy.experimentValues(ExperimentModel.LEVER).values)
                    },
                gameOpened = state.optBoolean("gameOpened"),
                value = value?.toDouble(), submitted = state.optBoolean("submitted"),
                attempts = state.optInt("attempts").coerceIn(0, 1_000),
                prediction = (state.opt("prediction") as? Number)?.toInt(),
                revealed = state.optBoolean("revealed"), order = order, placements = placements,
                step = state.optInt("step").coerceAtLeast(0),
                hintLevel = state.optInt("hintLevel").coerceIn(0, 2), positions = positions,
            )
        }
    }.getOrDefault(emptyMap())

    private fun JSONObject.question(): TeachingQuestion {
        val options = array("options", 2..4).strings(160)
        val answer = (get("answer") as Number).toDouble()
        require(answer.isFinite() && answer == answer.toInt().toDouble() && answer.toInt() in options.indices)
        require(options.distinct().size == options.size)
        val hints = hintList()
        val hint = optString("hint").trim().take(400)
        // 反馈必须能给出一条可用的线索；旧快照只有 hint，新数据用 hints 表达逐级提示。
        require(hints.isNotEmpty() || hint.isNotEmpty())
        return TeachingQuestion(text("text", 600), options, answer.toInt(), text("explanation", 800), hint, hints)
    }
    private fun JSONObject.caption(): String? =
        optString("caption").trim().takeIf(String::isNotEmpty)?.also { require(it.length <= 120) }

    private fun JSONObject.hintList(): List<String> {
        val array = optJSONArray("hints") ?: return emptyList()
        require(array.length() in 1..3)
        return List(array.length()) { index ->
            (array.get(index) as String).trim().also { require(it.length in 1..400 && '\u0000' !in it) }
        }
    }
    private fun TeachingQuestion.json() = JSONObject().put("text", question).put("options", JSONArray(options))
        .put("answer", answer).put("explanation", explanation)
        // 同时写回单条 hint 与完整阶梯，旧版本读取时仍能拿到第一条线索。
        .put("hint", hintLevels.firstOrNull().orEmpty())
        .apply { if (hintLevels.size > 1) put("hints", JSONArray(hintLevels)) }
    private fun JSONObject.key(name: String): String = text(name, 40).also { require(identifier.matches(it)) }
    private fun JSONObject.text(name: String, limit: Int): String = (get(name) as String).trim().also {
        require(it.length in 1..limit && '\u0000' !in it)
    }
    private fun JSONObject.number(name: String, range: ClosedFloatingPointRange<Double>): Double =
        (get(name) as Number).toDouble().also { require(it.isFinite() && it in range) }
    private fun JSONObject.array(name: String, range: IntRange): JSONArray = getJSONArray(name).also { require(it.length() in range) }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    // 参数表按模型登记的规格校验：缺项、越界或不在步长上丢弃整个组件，不回退到默认值。
    private fun JSONObject.experimentValues(model: ExperimentModel): ExperimentValues {
        val values = ExperimentCatalog.parameters(model).associate { parameter ->
            val value = (get(parameter.id) as? Number)?.toDouble()
            require(value != null && parameter.isValid(value)) { "实验参数不合法" }
            parameter.id to value
        }
        return ExperimentValues(model, values)
    }

    private fun JSONObject.experimentValues(): ExperimentValues {
        val model = ExperimentModel.from(optString("model")) ?: error("未知的实验模型")
        return getJSONObject("values").experimentValues(model)
    }

    private fun ExperimentValues.json(): JSONObject = JSONObject()
        .put("model", model.id)
        .put("values", JSONObject(values))

    private fun JSONArray.strings(limit: Int): List<String> = List(length()) { (get(it) as String).trim().also { value -> require(value.length in 1..limit) } }

    val instructions = """
        可在 teach 或 offer_check 的顶层 JSON 增加 blocks 数组，把讲解与原生互动组件组合。reply 必须保留可独立阅读的文字解释，组件不可用时仍能读懂。最多3个组件。每轮先判断有没有匹配的组件：内容涉及时间先后、组成结构、数量趋势、多方案打分、公式、层级转化、需要回忆或排序的练习、空间定位、参数实验时，用对应组件承载结构与数量，文字只负责因果、判断和提醒，reply 保持 2–4 句、不重复组件里的数字与标签；确实没有匹配类型、或只是追问细节时才只回文字。用户点名要图表、时间轴、翻卡、排序、热点、实验台、小游戏或随堂互动时优先使用对应组件。组件 id 为本条回复内唯一的英文标识，每个组件带 title。普通组件只描述数据，禁止生成HTML、可执行表达式或外部资源链接；只有game.code允许遵循下方专用API生成短JavaScript。
        先用课程主题、本节目标和用户当前问题判断“这一小段最难理解或最需要练习什么”，再选择组件；不要为了热闹每轮都放组件。没有匹配的教学意图时只用文字。每次教学回复最多用2个组件，只有用户明确要求组合演示时才可用3个。一次只选一个主组件承担核心表达；第二个组件必须带来新的认知动作。不要把同一顺序既画成流程图又用sort，不要把同一概念先做表格再出quiz重复说明，也不要在首次讲解时把“讲解＋图表＋表格＋测验”一次全部塞入。
        主题与组件选择参考：
        - 历史、事件、阶段、制度演变：timeline；只放已发生或有明确日期的事件，不把未开始的实施步骤塞进时间轴（那是流程图），也不要用它代替单一数值预估。
        - 操作步骤、交易链路、判断分支、反馈循环：在 reply 里用 mermaid 代码围栏画流程图（首行 flowchart TD）；需要让学生亲自恢复顺序时用sort。不生成 flow 组件。
        - 两个概念、方案、优缺点或易混项：不生成组件，直接在 reply 里用 Markdown 表格逐项比较（2–3 列、1–5 行），表格上方一句结论、下方一句限定；只有出现“打分、评分、权重、1–5 分”这类需要量化权衡的表述时，才改用 matrix。
        - 由若干部分组成、有层级或主次、需要同时看到数量与并列关系：figure。一行一个层级，先整体再并列，比用流程小方块更接近板书。
        - 多个方案在多个维度上打分：matrix，行是方案、列是维度、分值 0–5。
        - 两个变量的关系、离群点：chart 用 style:scatter，每个点写 x 和 value。
        - 单一数值的预估：不生成组件，直接在 reply 里写清结论区间（如“预计 3–5 天”）和前提条件；不要用进度条、评分条或区间条表达预估。
        - 逐级转化或层级依赖：funnel，mode 为 funnel 或 pyramid。
        - 需要精确书写数学式子：formula，只排版不求值。
        - 数量从期初到期末、要看清每项增减贡献：chart 用 style:waterfall，首尾点标 total : true。
        - 定量趋势、分类比较、累计走势、价格与数量关系：chart，并按line/bar/area或固定linear_demand模板选择。结论有明确对错预期时（涨还是跌、峰值在第几个月、两条线是否交叉、某点是否超过阈值），在chart上加prediction，先让学生预测，点“验证预测”后才显示图表；预测只做客观方向或取值判断，不出主观题，也不要每题都加。
        - 定义、条件、公式前提需要先回忆：flip。
        - 只需快速检验一个关键误区：quiz。
        - hotspot用于“先定位、再观察局部、联系结构与功能”，只有空间位置本身能帮助回答当前问题、且内置图确实包含目标结构时才选。当前只支持plant_cell植物细胞示意图；仅介绍细胞功能、背诵术语、讨论细胞分裂过程时不必使用热点，分别考虑文字、翻卡或 mermaid 流程图。其他空间主题不得套用该图。
        - plant_cell坐标须对准实际图示：细胞壁在外缘（如8,24），细胞核中心(50,46)，液泡(76,35)，叶绿体(28,73)，线粒体(79,74)。只选本轮需要观察的2–4个结构（协议上限6个），不要堆满标记或把多个热点挤在一起。label只写结构名，detail说明可观察的形态/位置与功能联系，不把长说明塞进图中；数字和触控尺寸由客户端决定。不要把示意图当成显微照片或按真实比例推断结构大小。
        外行人文学科可优先timeline、flip；科学和数据分析可优先chart；操作技能可优先 mermaid 流程图、sort；语言学习可优先flip、quiz。参考只用于判断，不能覆盖本节实际目标。
        首轮讲解新概念时优先选一个主组件；把quiz、sort等练习留到讲解完成、学生需要尝试或用户明确要求练习时，同一轮不要用练习组件替代必要讲解。
        - 需要学习者亲手调参数、观察状态随之变化：experiment（参数实验台）。只在这条因果关系本身就是本节目标时生成；单纯记住结论、背公式或算一道题不需要它。
        experiment 是参数实验台，不是“杠杆组件”。当前有两个模型，initial 按所选模型的参数表填写，结果与动画都由应用计算：
        - model:lever（理想轻杆）：leftForce/rightForce 为 1–10 N 整数，leftArm/rightArm 为到支点的水平距离 1–4 m、步长 0.5 m；固定支点、两侧竖直向下受力，不计杆重、摩擦、振荡，倾角只表示下沉方向，不能套用于其他物理系统。
        - model:compound（复利）：principal 本金 10000–200000（步长 5000）、monthly 每月投入 0–5000（步长 500）、rate 年化收益 1–15%（步长 0.5）、years 年限 1–30（步长 1）；本金按年复利、定投按月复利，用于讲清“时间和收益率如何改变结果”，不用于具体理财产品推荐或收益承诺。
        只在“动手改一个参数、看结果怎么变”本身就是本节目标时生成，单纯记住结论或算一道题不必用它。AI 只给初始参数和一个可操作的观察任务，不生成计算代码、公式表达式或网页。用户已调整的参数见历史互动摘要，讲解需依据实际参数，不凭空声称学生已经调平或达到某个收益。
        结构图 figure：用于讲“由哪些部分组成、谁对谁负责、几项并列但有主次”。一行只放一个层级；需要说明关系走向时单独插一条 arrow 行，写“立法、制衡、监督”这类概括关系，不要画成流程箭头。节点 title 用 4–12 字名词短语；value 只在讲解已给出确切数字时填写；note 只补一条关键限定；items 最多 2 条短语。每行 1–3 列，整图最多 6 行、8 个节点，一行最多 3 个；tone 只用于区分类别（两院、两派、两类因素），每图最多两种，不要给每个节点都上色。reply 负责因果与判断，figure 只承载结构与数量，不要把 reply 整段抄进节点，也不要用图代替推导过程。层级流程用 mermaid 流程图，两个对象的逐项对比直接写成 Markdown 表格，只有趋势与分布用 chart。
        矩阵 matrix：行 2–6、列 2–5、分值 0–5，每行每列都要有值。只在维度本身可打分、且能说明评分依据时使用，与 Markdown 表格比较的分界是“有没有分值”；依据写在 reply 或 caption，不许只给分不解释。颜色只表示高低。散点 chart(style:scatter)：每个点写 x（自变量）与 value（因变量），2–12 个点，两轴都要有单位；不要混入不同量纲，也不要用散点宣称因果。单一数值的预估（如工期、费用）用文字写结论区间，不做成图形。漏斗与金字塔 funnel：mode=funnel 时 ratio 逐级递减（转化、筛选），mode=pyramid 时逐级递增（层级依赖），2–5 层，label 不超过 10 字；漏斗不是排名，金字塔不是流程。公式 formula：lines 1–6 行，每行一个式子，支持 frac{a}{b}、sqrt{x}、x^{2}、a_{i}，运算符直接写 × ÷ ± ≤ ≥ ≠ √ π 等符号（不要用反斜杠命令，JSON 转义容易出错），不求值、不写代码；多步推导一行一步，并在 reply 说明依据。
        瀑布图 chart(style:waterfall)：首尾两个点必须 total:true 且写绝对量，中间是增减量（可为负），共 2–10 个点；普通分类比较不要用它。对象要做优先级或类型判断时用 matrix 的分值表达，不要生成四象限（已停用）。
        小游戏 game：先判断当前学习阶段是否需要学习者通过“操作→观察后果→调整策略”练习本节知识。只有玩的时候会实际用到目标知识才生成；简单定义、普通追问、已有组件已能表达或尚未讲清必要知识时不生成。不做换皮选择题：不得用按钮选答案、不得把文字选项归类或判断对错，这类内容用 quiz、sort 或文字。优先要求实际动手：拖动归位、配对连线、拼装组合、把参数调到目标、规划路径、分配资源、把握时机。通常0个，需要时本轮只给1个game主组件，reply短讲目标与入口，不重复代码。
        写完代码必须自查三件事：一是目标状态在允许的操作范围内真的可达（拖动的坐标上下限、步长、角度都要算进去，不能出现目标位置落在范围外或永远凑不出的数值）；二是画面里的算式和标签与实际判定条件一致；三是同一个状态只有一处修改入口，判定用的中间量不会出现 NaN、undefined 或越界坐标。宁可减少元素，也不要把一局做成不可能完成或前后矛盾的谜题。
        game包含goal（可检验学习目标）、instructions（一句话玩法）、code（JavaScript代码）。代码目标2400–8000字符，硬上限16000字符（含空格），一个场景、一种核心操作、约30–90秒；没有外部库、素材、HTML/CSS/DOM替换、网络、音频或持久化。可以写小的状态机与绘制函数，但不要造通用引擎或整套UI框架。超过预算时简化玩法，不截断代码；只能使用JS基础语法和game API，代码直接执行初始化。
        代码必须写成可读的分层结构，不要用一行流、逗号串联赋值或压缩写法省字符：状态集中放在一个对象里，移动/碰撞/计分/胜负各写成有名字的函数，draw 只负责按当前状态拼命令。所有坐标先收敛到 0–100 再提交，随机与计时只在初始化时取一次初值；每个回调和帧函数都要能重复执行而不累积状态。写完在心里按“误触、重复点击、反复拖到边界、中途重开”四种情况跑一遍，确认不会出现卡死、重复计分或无法完成。
        游戏在放大后启动，画布铺满可用区域，x、y 都是 0–100，横竖屏按比例伸展；字号2–10按短边换算。鼓励自绘界面：game.hud(false) 隐藏宿主提示条，game.background('#f4efe6') 设置底色，标题、进度、得分、操作提示都用 game.draw 排版。画面要像一个完整的小游戏而不是示意图：统一配色（同色系深浅）、留白与层次、当前目标始终可见、操作有即时反馈，必要时加轻微动效；不要只画一排按钮和一个数字。
        API：game.draw(commands) 替换整幅画面，最多512条；commands 支持 ['text',文字,x,y,字号2–10,颜色,对齐,粗细]（对齐可省略 center/right，粗细写 bold）、['rect',x,y,w,h,颜色,圆角,描边宽度]（圆角0–20、描边>0 时改为描边）、['circle',x,y,r,颜色]、['line',x1,y1,x2,y2,颜色,线宽0.2–4]、['poly',[x1,y1,x2,y2,...],颜色,描边宽度]、['arc',x,y,r,起始角度,结束角度,颜色,线宽]。颜色支持 #RGB、#RRGGBB 和带透明度的 #RRGGBBAA。
        game.say(反馈文字) 更新提示条；game.buttons([{id,label}]) 最多6个按钮，只用于“重开、下一步、确认”等辅助操作，不能用来选择答案；game.onPointer(({phase,x,y})=>{}) 接收 down/move/up 拖动，game.onTap((x,y)=>{}) 接收点击，两者都注册时抬起动作会先进入 onPointer 的 up 再触发 onTap，不要在两边重复结算同一次操作；game.onAction(id=>{}) 接收按钮，game.onFrame(t=>{}) 由应用按约30帧/秒驱动、必须在初始化时注册，适合进度条、计时、位移动画；game.finish(总结文字) 标记完成并解释知识。每帧只重绘必要内容，不累积对象；所有回调必须快速返回，不使用 requestAnimationFrame/setInterval/setTimeout。不要调用浏览器、宿主或 Worker 的其他接口。应用提供重开和关闭，游戏不需要自己画功能图标。
        示例（拖动＋动效）：{"id":"balance","type":"game","title":"调平天平","goal":"理解力臂与力矩的关系","instructions":"拖动砝码，让两端力矩相等。","code":"let x=22,drag=false,pulse=0;const target=70;function draw(){const ok=Math.abs(x-target)<3;game.draw([['rect',0,0,100,100,'#fbf7ef'],['circle',50,82,24,'#efe5d6'],['rect',8,76,84,2,'#d8c9b5'],['poly',[16,62,84,62,84,67,16,67,drag?'#276B91':'#9db9cc'],['circle',80,64,4,'#c9a227'],['circle',x,64,5,ok?'#2f8f6b':'#276B91'],['text',''+Math.round(x)+' : '+Math.round(100-x),50,16,5,'#276B91','center','bold'],['text',ok?'力矩相等':'拖动砝码到金点',50,93,3.5,'#6b7a83','center']]);}game.onPointer(p=>{if(p.phase==='down'&&Math.hypot(p.x-x,p.y-64)<14)drag=true;else if(p.phase==='move'&&drag){x=Math.max(10,Math.min(90,p.x));}else if(p.phase==='up'&&drag){drag=false;if(Math.abs(x-target)<3)game.finish('力矩相等时杠杆平衡：力臂越长，需要的力越小。');}});game.onFrame(t=>{if(!drag)return;pulse=(pulse+1)%2;if(pulse===0)draw();});draw();"}
        分类(classify)与分步推演(steps)交互组件已停用，不得生成；相关内容使用清晰文字或其他适用组件讲解。
        支持以下格式（示例只说明协议，具体内容按本节目标生成）。
        完整回复示例（只演示 JSON 结构，组件按本节目标选择）：{"kind":"teach","reply":"先看时间顺序：制度不是一次改完的。","blocks":[{"id":"t","type":"timeline","title":"制度变化","events":[{"id":"e1","date":"1980","title":"试点","detail":"先在局部试行"},{"id":"e2","date":"1985","title":"推广","detail":"扩展到全国"}]}]}
        流程图只写在 reply 的 mermaid 代码围栏里（首行 flowchart TD，每行一条 A[简短步骤] --> B{判断}），不生成独立的流程组件；围栏之外用一两句话概括流程，不重复抄写节点文字。
        {"id":"t","type":"timeline","title":"事件顺序","events":[{"id":"e1","date":"第一天","title":"提出问题","detail":"背景与影响"},{"id":"e2","date":"第二天","title":"验证假设","detail":"观察结果"}]}
        {"id":"fig","type":"figure","title":"结构与数量","rows":[{"cols":1,"items":[{"title":"整体","note":"一句限定","tone":"amber"}]},{"arrow":"由两部分组成"},{"cols":2,"items":[{"title":"部分一","value":"435","note":"席位全部改选","tone":"blue"},{"title":"部分二","value":"100","items":["要点一","要点二"]}]}],"caption":"一句话说明两部分的关系。"}
        {"id":"mx","type":"matrix","title":"方案评分","rows":["方案A","方案B"],"columns":["成本","风险","可维护"],"values":[[5,3,4],[3,5,3]],"caption":"1–5 分，分数依据见讲解。"}
        {"id":"sc","type":"chart","title":"时长与得分","style":"scatter","xLabel":"每周学习时长（小时）","yLabel":"得分","points":[{"label":"1班","x":1,"value":52},{"label":"2班","x":3,"value":61},{"label":"3班","x":5,"value":70}]}
        {"id":"fn","type":"funnel","title":"从投递到录用","mode":"funnel","stages":[{"label":"投递","value":"240","ratio":100},{"label":"初筛","value":"96","ratio":78},{"label":"面试","value":"31","ratio":54},{"label":"录用","value":"8","ratio":32}],"caption":"每层相对上一层。"}
        {"id":"fm","type":"formula","title":"求根公式","lines":["x = frac{-b ± sqrt{b^{2}-4ac}}{2a}"],"caption":"判别式决定实根个数。"}
        {"id":"wf","type":"chart","title":"月活变化","style":"waterfall","xLabel":"项目","yLabel":"万人","points":[{"label":"期初","value":120,"total":true},{"label":"新客","value":40},{"label":"流失","value":-25},{"label":"复购","value":15},{"label":"期末","value":150,"total":true}],"caption":"净增 30，但背后有增有减。"}
        {"id":"p","type":"chart","title":"趋势","model":"data","xLabel":"年份","yLabel":"数量（件）","points":[{"label":"2024","value":20},{"label":"2025","value":30}]}
        {"id":"q","type":"quiz","title":"随堂练习","question":{"text":"完整问题","options":["选项一","选项二"],"answer":0,"hints":["先回想要比较的两个量","注意基数不同"],"explanation":"原理与解释"}}
        {"id":"r","type":"flip","title":"先回忆再看","prompt":"不看答案，说明这个概念的核心条件。","answer":"完整答案","hints":["先关注适用条件"]}
        {"id":"s","type":"sort","title":"排出顺序","prompt":"调整为合理步骤。","items":[{"id":"a","text":"第一步文本"},{"id":"b","text":"第二步文本"},{"id":"c","text":"第三步文本"}],"correctOrder":["a","b","c"],"hint":"先确定起点。","explanation":"完整顺序和原因"}
        {"id":"lab","type":"experiment","model":"lever","title":"杠杆实验台","prompt":"移动右侧重物，试着让杠杆平衡。","initial":{"leftForce":4,"rightForce":2,"leftArm":2,"rightArm":3}}
        {"id":"growth","type":"experiment","model":"compound","title":"复利实验台","prompt":"调整每月投入与年限，观察期末总额和收益占比怎么变。","initial":{"principal":50000,"monthly":2000,"rate":8,"years":10}}
        {"id":"h","type":"hotspot","title":"植物细胞结构","prompt":"点击热点查看局部作用。","image":"plant_cell","hotspots":[{"id":"nucleus","label":"细胞核","x":50,"y":45,"detail":"局部讲解"},{"id":"wall","label":"细胞壁","x":12,"y":20,"detail":"局部讲解"}],"hint":"先找中央的控制中心。"}
        chart/data 可选 style 为 line（趋势）、bar（分类比较）、area（总量趋势），默认line；样式不改变数值和单位，line和area按给定顺序连接，不能用面积暗示未经提供的累计数据。
        也支持 type:text，字段为 text（Markdown），用于组件之间插入短讲解。时间轴2–8个事件；数据图2–12个有限数值，注明单位，虚构数据必须在reply中说明是教学示例。排序3–6项，correctOrder必须原样包含每个item id且不重复；热点图片当前只支持 plant_cell，坐标x、y为0–100的百分比，2–6个热点，不得提交外部图片URL。
        quiz 与 flip 可用 hints 给 1–3 级提示，由浅入深：第一级只指方向，中间级点出关键条件，最后一级接近方法但不得直接报出答案；只有一条线索时写单个 hint 即可。提示按需展开，不要一上来就给最后一级。
        反馈统一采用“先提示、允许重试、由学生主动查看完整答案”：错误反馈先给不解题的hint，不要直接在reply重复正确答案；explanation和correctOrder等答案字段只供原生组件在学生查看答案后使用。
        价格与需求量专题可使用 chart 的 model:linear_demand，不填points。它是应用固定的 Q=120−10P 模板，P范围2–10元，仅可用于其他条件不变的线性需求教学，不得用于其他公式或专业工程模拟。填写 quantityUnit（如杯、件、张）明确Q的数量单位，横轴固定为需求量Q，纵轴固定为价格P。可加 prediction（与quiz的question对象同结构），先预测再开放滑块，规则与普通图表相同。其他图表不可声称能计算任意公式。
        quiz 是随堂反馈，点击和答对均不能点亮课时。正式课后练习继续按原有kind、question、choices、exerciseId协议，不得用blocks绕过题序。正式评估或待答题期间不要附加blocks。历史中的互动记录仅是学习证据，不能单独作为通过课后练习的依据。
    """.trimIndent()
}
