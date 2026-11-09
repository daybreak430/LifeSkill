package com.lifeskill.app.ai

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader

internal object AiJson {
    fun objectOrNull(text: String): JSONObject? = runCatching {
        JsonReader(StringReader(text)).use { reader ->
            val value = readValue(reader, 0) as? JSONObject
            value?.takeIf { reader.peek() == JsonToken.END_DOCUMENT }
        }
    }.getOrNull()

    // 模型响应不可信。限制嵌套深度并拒绝重复键，防止崩溃或把含糊字段当成写入指令。
    private fun readValue(reader: JsonReader, depth: Int): Any {
        require(depth <= 16)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!has(name))
                    put(name, readValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) put(readValue(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().toBigDecimal()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            else -> error("JSON 值不完整。")
        }
    }
}
