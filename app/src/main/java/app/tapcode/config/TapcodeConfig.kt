package app.tapcode.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TapcodeConfig(
    val carrier: String,
    val version: Int,
    val optionPattern: String = """^\s*(\d{1,3}|[#*])\s*(?:[.):\-\s]\s*)(\S.*)$""",
    val chargeKeywords: List<String> = listOf("N", "\u20A6", "charge", "buy", "subscribe", "renew"),
    val dialogPackages: List<String> = listOf("com.android.phone"),
    val buttonSelectors: ButtonSelectors = ButtonSelectors(),
    val quickAnswers: Map<String, QuickAnswer> = emptyMap(),
    val paths: Map<String, Path> = emptyMap()
) {
    @Serializable
    data class ButtonSelectors(
        val send: Selector = Selector(viewIds = listOf("android:id/button1"), labels = listOf("send", "reply", "ok")),
        val cancel: Selector = Selector(viewIds = listOf("android:id/button2"), labels = listOf("cancel", "dismiss", "close"))
    )

    @Serializable
    data class Selector(val viewIds: List<String> = emptyList(), val labels: List<String> = emptyList())

    @Serializable
    data class QuickAnswer(val code: String, val mode: String = "single")

    @Serializable
    data class Path(val code: String, val steps: List<Step> = emptyList())

    @Serializable
    data class Step(val expect: String, val reply: String)

    @SerialName("perOem")
    val perOem: Map<String, OemOverride> = emptyMap()

    @Serializable
    data class OemOverride(
        val dialogPackages: List<String>? = null,
        val buttonSelectors: ButtonSelectors? = null
    )

    fun forManufacturer(manufacturer: String): TapcodeConfig {
        val o = perOem[manufacturer.lowercase()] ?: return this
        return copy(
            dialogPackages = o.dialogPackages ?: dialogPackages,
            buttonSelectors = o.buttonSelectors ?: buttonSelectors
        )
    }

    fun mentionsCharge(text: String): Boolean = chargeKeywords.any { kw ->
        if (kw == "₦") {
            text.contains('₦')
        } else if (kw.equals("N", ignoreCase = true)) {
            // Bare "N" only counts as naira when followed by an amount
            Regex("""\bN\s?[\d.,]+""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        } else {
            text.contains(kw, ignoreCase = true)
        }
    }
}