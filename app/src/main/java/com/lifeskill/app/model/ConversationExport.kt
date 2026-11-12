package com.lifeskill.app.model

internal data class ConversationExport(val fileName: String, val markdown: String)

internal data class LessonTranscript(val name: String, val turns: List<Turn>)

private val invalidFileNameCharacters = Regex("""[<>:"/\\|?*\p{Cntrl}]""")

internal fun conversationExport(entry: Entry, transcripts: List<LessonTranscript>): ConversationExport {
    val fileName = entry.name.replace(invalidFileNameCharacters, "_").trim().trimStart('.').ifBlank { "学习" }
    val markdown = buildString {
        appendLine("# ${entry.name.markdownInline()}")
        for (lesson in transcripts) {
            if (entry.kind == EntryKind.TREE) {
                appendLine()
                appendLine("## ${lesson.name.markdownInline()}")
            }
            for (turn in lesson.turns) {
                appendLine()
                val label = when {
                    turn.kind == TurnKind.RESTART -> "我 · 新一轮学习"
                    turn.kind == TurnKind.EXERCISE -> "AI 老师 · 课后练习 · 第 ${turn.exerciseNumber ?: 1} 题"
                    turn.speaker == Speaker.USER -> "我"
                    else -> "AI 老师"
                }
                appendLine("**$label**")
                if (turn.text.isNotEmpty() || turn.choices.isNotEmpty()) {
                    appendLine()
                    // 用引用保留每条消息的 Markdown 结构，消息中的标题不会混入课时分节。
                    if (turn.text.isNotEmpty()) {
                        turn.text.lineSequence().forEach { line ->
                            appendLine(if (line.isEmpty()) ">" else "> $line")
                        }
                    }
                    if (turn.text.isNotEmpty() && turn.choices.isNotEmpty()) appendLine(">")
                    turn.choices.forEachIndexed { index, choice ->
                        appendLine("> - ${choiceAnswer(index, choice).markdownInline()}")
                    }
                }
            }
        }
    }
    return ConversationExport("$fileName-对话.md", markdown)
}

private fun String.markdownInline(): String = buildString {
    for (character in this@markdownInline) {
        if (character in "\\`*_[]<>&#~") append('\\')
        append(character)
    }
}
