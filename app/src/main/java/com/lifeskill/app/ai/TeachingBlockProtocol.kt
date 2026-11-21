package com.lifeskill.app.ai

import com.lifeskill.app.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object TeachingBlockProtocol {
    private val identifier = Regex("[A-Za-z0-9_-]{1,40}")

    // 单个组件坏数据只降级该组件，reply 仍可阅读；数量、文本、引用与有限数值均在进入 UI 前校验。
    fun decode(array: JSONArray?): List<TeachingBlock> {
        if (array == null || array.length() !in 1..4 || array.toString().length > 18_000) return emptyList()
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
                        TeachingComparison(id, title, columns, dimensions)
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
                            json.optJSONObject("prediction")?.question(), unit)
                    }
                    "quiz" -> TeachingQuiz(id, title, json.getJSONObject("question").question())
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
                is TeachingComparison -> put("type", "compare").put("columns", JSONArray(block.columns))
                    .put("dimensions", JSONArray().apply { block.dimensions.forEach {
                        put(JSONObject().put("id", it.id).put("title", it.title).put("values", JSONArray(it.values)))
                    } })
                is TeachingChart -> {
                    put("type", "chart").put("model", if (block.mode == TeachingChartMode.DATA) "data" else "linear_demand")
                        .put("xLabel", block.xLabel).put("yLabel", block.yLabel)
                        .put("quantityUnit", block.quantityUnit)
                        .put("points", JSONArray().apply { block.points.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
                    block.prediction?.let { put("prediction", it.json()) }
                }
                is TeachingQuiz -> put("type", "quiz").put("question", block.question.json())
            }
        }) }
    }

    fun encodeStates(states: Map<String, TeachingState>): String = JSONObject().apply {
        states.forEach { (id, state) -> put(id, JSONObject().put("selection", state.selection).put("value", state.value)
            .put("submitted", state.submitted).put("attempts", state.attempts).put("prediction", state.prediction).put("revealed", state.revealed)) }
    }.toString()

    fun decodeStates(raw: String?): Map<String, TeachingState> = runCatching {
        if (raw == null || raw.length > 8_000) return emptyMap()
        val json = JSONObject(raw)
        require(json.length() <= 4)
        json.keys().asSequence().associateWith { id ->
            val state = json.getJSONObject(id)
            val value = state.opt("value") as? Number
            require(value == null || value.toDouble().isFinite())
            TeachingState(state.optString("selection").takeIf { !state.isNull("selection") }, value?.toDouble(),
                state.optBoolean("submitted"), state.optInt("attempts").coerceIn(0, 1_000),
                (state.opt("prediction") as? Number)?.toInt(), state.optBoolean("revealed"))
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
    private fun JSONObject.array(name: String, range: IntRange): JSONArray = getJSONArray(name).also { require(it.length() in range) }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(limit: Int): List<String> = List(length()) { (get(it) as String).trim().also { value -> require(value.length in 1..limit) } }

    val instructions = """
        可在 teach 或 offer_check 的顶层 JSON 增加 blocks 数组，把讲解与原生互动组件组合。reply 必须保留可独立阅读的文字解释，组件不可用时仍能读懂。最多3个组件，仅在有助理解时使用，普通追问可只回文字。用户要求图表、时间轴、比较、流程或随堂互动时优先使用对应组件。组件 id 为本条回复内唯一的英文标识，每个组件带 title。组件只描述数据，禁止生成 HTML、JavaScript、可执行表达式或外部资源链接。
        支持以下格式（示例只说明协议，具体内容按本节目标生成）：
        使用 flow 组件时，reply 只保留文字概括，不再重复绘制同一流程；组件的 diagram 字段就是 Mermaid。没有 flow 组件时，文字中的流程仍沿用原来的 Mermaid 代码围栏规则。
        {"id":"t","type":"timeline","title":"事件顺序","events":[{"id":"e1","date":"第一天","title":"提出问题","detail":"背景与影响"},{"id":"e2","date":"第二天","title":"验证假设","detail":"观察结果"}]}
        {"id":"f","type":"flow","title":"操作流程","diagram":"flowchart TD\nA[核对] --> B{一致}\nB -->|是| C[归档]\nB -->|否| D[复核]\nD --> A","details":{"A":"核对依据","B":"判断条件","C":"完成后的产物","D":"如何复核"}}
        {"id":"c","type":"compare","title":"对比","columns":["概念一","概念二"],"dimensions":[{"id":"cause","title":"变化原因","values":["第一项的原因","第二项的原因"]}]}
        {"id":"p","type":"chart","title":"趋势","model":"data","xLabel":"年份","yLabel":"数量（件）","points":[{"label":"2024","value":20},{"label":"2025","value":30}]}
        {"id":"q","type":"quiz","title":"随堂练习","question":{"text":"完整问题","options":["选项一","选项二"],"answer":0,"hint":"不直接揭示答案的线索","explanation":"原理与解释"}}
        也支持 type:text，字段为 text（Markdown），用于组件之间插入短讲解。时间轴2–8个事件；流程最多8个节点，每个节点的id必须在details中有解释；对比2–3列、1–5个维度，每个values与columns等长；数据图2–12个有限数值，注明单位，虚构数据必须在reply中说明是教学示例。
        价格与需求量专题可使用 chart 的 model:linear_demand，不填points。它是应用固定的 Q=120−10P 模板，P范围2–10元，仅可用于其他条件不变的线性需求教学，不得用于其他公式或专业工程模拟。填写 quantityUnit（如杯、件、张）明确Q的数量单位，横轴固定为需求量Q，纵轴固定为价格P。可加 prediction（与quiz的question对象同结构），先预测再开放滑块。其他图表不可声称能计算任意公式。
        quiz 是随堂反馈，点击和答对均不能点亮课时。正式课后练习继续按原有kind、question、choices、exerciseId协议，不得用blocks绕过题序。正式评估或待答题期间不要附加blocks。历史中的互动记录仅是学习证据，不能单独作为通过课后练习的依据。
    """.trimIndent()
}
