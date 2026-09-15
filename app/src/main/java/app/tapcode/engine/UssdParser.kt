package app.tapcode.engine

import app.tapcode.config.TapcodeConfig

data class ParseResult(
    val screen: UssdScreen,
    val confidence: Double
)

class UssdParser(private val config: TapcodeConfig) {

    private val optionRegex = Regex(config.optionPattern, setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val morePattern = Regex("""(?im)^\s*(\d{1,3}|[#*0])\s*[.):\-]?\s*(more|next|see more|load more)\b.*$""")
    private val inputPrompt = Regex("""(?i)(enter|input|type|reply|respond|select|choose|provide)[^:]*:?\s*$""")
    private val finalMarkers = Regex(
        """(?i)(thank you|successful|successfully|failed|insufficient|invalid|error|exceeded|not enough|denied|declined|completed)"""
    )

    fun parse(raw: String): ParseResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            return ParseResult(UssdScreen(raw, ScreenKind.PROGRESS, confidence = 0.3), 0.3)
        }

        val options = extractOptions(text)
        val confidence = scoreConfidence(text, options)
        val charge = config.mentionsCharge(text)

        val screen = when {
            options.isNotEmpty() ->
                UssdScreen(
                    raw = text,
                    kind = ScreenKind.MENU,
                    options = options,
                    prompt = stripOptions(text, options),
                    mentionsCharge = charge,
                    confidence = confidence
                )

            finalMarkers.containsMatchIn(text) && !inputPrompt.containsMatchIn(text) ->
                UssdScreen(text, ScreenKind.FINAL, prompt = text, mentionsCharge = charge, confidence = confidence)

            inputPrompt.containsMatchIn(text) ->
                UssdScreen(text, ScreenKind.INPUT, prompt = text, requiresText = true, mentionsCharge = charge, confidence = confidence)

            looksLikeProgress(text) ->
                UssdScreen(text, ScreenKind.PROGRESS, prompt = text, mentionsCharge = charge, confidence = confidence)

            // MP-03 fallback: unstructured text becomes raw prompt with free input
            else ->
                UssdScreen(text, ScreenKind.INPUT, prompt = text, requiresText = true, mentionsCharge = charge, confidence = confidence)
        }
        return ParseResult(screen, confidence)
    }

    fun extractOptions(text: String): List<UssdOption> {
        val found = mutableListOf<UssdOption>()
        optionRegex.findAll(text).forEach { m ->
            val number = m.groupValues[1].trim()
            val label = m.groupValues[2].trim()
            if (number.isNotEmpty() && label.isNotEmpty()) {
                found.add(UssdOption(number, label))
            }
        }
        val more = morePattern.findAll(text).map { m ->
            UssdOption(m.groupValues[1].trim(), m.groupValues[2].trim())
        }
        return (found + more).distinctBy { it.number }
    }

    fun stripOptions(text: String, options: List<UssdOption>): String {
        var result = text
        options.forEach { o ->
            result = result.replace(
                Regex("""(?m)^\s*${Regex.escape(o.number)}\s*[.):\-]?\s*${Regex.escape(o.label)}\s*$"""),
                ""
            )
        }
        return result.trim()
    }

    private fun looksLikeProgress(text: String): Boolean {
        val p = text.lowercase()
        return p.contains("processing") || p.contains("please wait") || p.endsWith("...")
    }

    private fun scoreConfidence(text: String, options: List<UssdOption>): Double {
        if (options.isEmpty()) return 0.5
        val numbered = options.count { it.number.all(Char::isDigit) }
        val ratio = numbered.toDouble() / options.size
        val lines = text.lines().count { it.isNotBlank() }
        val expected = minOf(options.size + 1, lines)
        val density = if (expected == 0) 0.0 else options.size.toDouble() / expected
        return (0.55 + 0.3 * ratio + 0.15 * density).coerceAtMost(1.0)
    }
}