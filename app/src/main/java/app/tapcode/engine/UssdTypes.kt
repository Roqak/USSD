package app.tapcode.engine

enum class ScreenKind { MENU, PROGRESS, FINAL, INPUT }

data class UssdOption(val number: String, val label: String)

data class UssdScreen(
    val raw: String,
    val kind: ScreenKind,
    val options: List<UssdOption> = emptyList(),
    val prompt: String = raw,
    val requiresText: Boolean = false,
    val mentionsCharge: Boolean = false,
    val confidence: Double = 1.0
)

data class SessionRequest(
    val code: String,
    val subscriptionId: Int? = null,
    val path: List<Step> = emptyList()
) {
    data class Step(val expect: String, val reply: String)
}

enum class SessionState {
    IDLE, DIALLING, WAITING, MENU, CONFIRMING, REPLAYING, RESULT, ERROR, CANCELLED
}

data class SessionEvent(
    val from: SessionState,
    val to: SessionState,
    val screen: UssdScreen? = null,
    val message: String? = null
)