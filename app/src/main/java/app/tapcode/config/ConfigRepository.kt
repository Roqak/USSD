package app.tapcode.config

import android.content.Context
import kotlinx.serialization.json.Json

class ConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: TapcodeConfig? = null

    fun load(): TapcodeConfig {
        cached?.let { return it }
        val cfg = loadRemote() ?: loadBundled()
        cached = cfg
        return cfg
    }

    fun invalidate() {
        cached = null
    }

    /** MP-02: cached remote config, rolled back to bundled default on failure. */
    private fun loadRemote(): TapcodeConfig? {
        val file = context.getFileStreamPath(REMOTE_FILE)
        val sigFile = context.getFileStreamPath(SIG_FILE)
        if (!file.isFile || !sigFile.isFile) return null
        return try {
            val bytes = file.readBytes()
            val sig = sigFile.readBytes()
            if (!verifySignature(bytes, sig)) return null
            json.decodeFromString<TapcodeConfig>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBundled(): TapcodeConfig = try {
        val text = context.assets.open(BUNDLED_FILE).bufferedReader().use { it.readText() }
        json.decodeFromString<TapcodeConfig>(text)
    } catch (_: Exception) {
        DEFAULT
    }

    private val verifier by lazy { ConfigVerifier(PUBLIC_KEY_PEM) }

    fun verifySignature(payload: ByteArray, signature: ByteArray): Boolean =
        verifier.verify(payload, signature)

    fun installRemote(payload: ByteArray, signature: ByteArray): Boolean {
        if (!verifySignature(payload, signature)) return false
        return try {
            context.openFileOutput(REMOTE_FILE, Context.MODE_PRIVATE).use { it.write(payload) }
            context.openFileOutput(SIG_FILE, Context.MODE_PRIVATE).use { it.write(signature) }
            invalidate()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val REMOTE_FILE = "config.remote.json"
        private const val SIG_FILE = "config.remote.sig"
        private const val BUNDLED_FILE = "default-config.json"

        /**
         * Release signing key for remote configs. Replace with the production
         * public key before shipping; the value below is a dev-only placeholder.
         */
        val PUBLIC_KEY_PEM: String = """
            MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKfCUnHsWPTOLdVYbVa4cUaFRlW9
            5b6Pum7XhE8q1Q0i1P4nS8Z2aF0bQe5yMvXrJ7kLd3TgHcWvNsAoCuEkCAwEAAQ==
        """.trimIndent().replace("\n", "")

        val DEFAULT: TapcodeConfig = TapcodeConfig(
            carrier = "mtn-ng",
            version = 1,
            quickAnswers = mapOf(
                "airtimeBalance" to TapcodeConfig.QuickAnswer("*310#", "single"),
                "dataBalance" to TapcodeConfig.QuickAnswer("*323#", "single"),
                "myNumber" to TapcodeConfig.QuickAnswer("*123*1*1#", "single")
            ),
            paths = mapOf(
                "selfService" to TapcodeConfig.Path("*123#")
            )
        )
    }
}