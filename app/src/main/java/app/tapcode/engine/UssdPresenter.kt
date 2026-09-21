package app.tapcode.engine

/**
 * Presentation layer: turns raw USSD text into app-friendly content.
 * Users see labels, amounts and results — never the dialed code; the raw
 * operator text stays available behind a details affordance for verification.
 */
object UssdPresenter {

    data class Friendly(
        val heading: String?,
        val amount: String?,
        val body: String
    )

    private val amountRegex = Regex("""(?:NGN|N|₦)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val dataRegex = Regex("""([\d.]+)\s?(GB|MB|KB)\b""", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("""\b0\d{10}\b""")

    /** Parses raw USSD text into friendly presentation parts. */
    fun present(raw: String): Friendly {
        val text = raw.lowercase()
        val failed = listOf("insufficient", "failed", "invalid", "error", "denied", "declined", "not enough")
            .any { text.contains(it) }
        val heading = when {
            failed -> "Didn't work"
            dataRegex.containsMatchIn(raw) -> "Data balance"
            text.contains("balance") -> "Your balance"
            text.contains("your number") || phoneRegex.containsMatchIn(raw) -> "Your number"
            text.contains("thank you") || text.contains("successful") || text.contains("completed") -> "Done"
            else -> null
        }
        return Friendly(
            heading = heading,
            amount = amount(raw),
            body = body(raw)
        )
    }

    /** First naira amount in the text, normalised to ₦, or null. */
    fun amount(raw: String): String? =
        amountRegex.find(raw)?.let { "₦${it.groupValues[1]}" }

    /** Raw text tidied for display: collapsed blank-line runs, no trailing junk. */
    fun body(raw: String): String =
        raw.trim().replace(Regex("""\n{3,}"""), "\n\n")
}