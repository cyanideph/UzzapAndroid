package com.kolipri.uzzap.ui.util

import java.util.regex.Pattern

/** Display-only conversion of legacy Uzzap ASCII emoticon codes to visible emoji. */
object UzzapEmoticonRenderer {
    private val replacements = linkedMapOf(
        ":-o)" to "🤡", ":o)" to "🤡",
        ":-D" to "😄", ":D" to "😄", ":-d" to "😄", ":d" to "😄", "XD" to "😆", "xD" to "😆",
        ":-)" to "🙂", ":)" to "🙂", "(:-" to "🙂", "(:" to "🙂",
        ";-)" to "😉", ";)" to "😉", ";D" to "😉", ";d" to "😉",
        ":-(" to "😞", ":(" to "😞", ")-:" to "😞", "):" to "😞",
        ":-p" to "😛", ":p" to "😛", ":-P" to "😛", ":P" to "😛",
        ":'-(" to "😭", ":'( " to "😭", ":'(" to "😭", ";-(" to "😢", ";(" to "😢", "T_T" to "😭", "T.T" to "😭",
        ">:-(" to "😡", ">:(" to "😡", ":-@" to "😡", ":@" to "😡",
        ":-O" to "😮", ":O" to "😮", ":-o" to "😮", ":o" to "😮", "=O" to "😮", "=o" to "😮",
        ":-S" to "🤢", ":S" to "🤢", ":-s" to "🤢", ":s" to "🤢",
        ":-*" to "💋", ":*" to "💋", "<3" to "❤️",
        "[love]" to "❤️", "[heart]" to "❤️", "[kiss]" to "💋",
        "[party]" to "🎉", "[drink]" to "🍹", "[beer]" to "🍺",
        "[rose]" to "🌹", "[flower]" to "🌸", "@}->--" to "🌹",
        "\\\\o/" to "🙌", "\\o/" to "🙌"
    )

    private val pattern = Pattern.compile(
        replacements.keys.sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) }
    )

    fun render(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val matcher = pattern.matcher(text)
        val out = StringBuffer()
        while (matcher.find()) {
            val replacement = replacements[matcher.group()] ?: matcher.group()
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(out)
        return out.toString()
    }
}
