package app.tapcode.config

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MP-02: remote configs must be signature-verified before use. */
class ConfigVerifierTest {

    private fun sign(payload: ByteArray, privateKey: java.security.PrivateKey): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test
    fun `valid signature accepted`() {
        val kp = keyPair()
        val verifier = ConfigVerifier(Base64.getEncoder().encodeToString(kp.public.encoded))
        val payload = """{"carrier":"mtn-ng","version":2}""".toByteArray()
        assertTrue(verifier.verify(payload, sign(payload, kp.private)))
    }

    @Test
    fun `tampered payload rejected`() {
        val kp = keyPair()
        val verifier = ConfigVerifier(Base64.getEncoder().encodeToString(kp.public.encoded))
        val sig = sign("""{"carrier":"mtn-ng","version":2}""".toByteArray(), kp.private)
        val tampered = """{"carrier":"mtn-ng","version":3}""".toByteArray()
        assertFalse(verifier.verify(tampered, sig))
    }

    @Test
    fun `wrong key rejected`() {
        val kp = keyPair()
        val other = keyPair()
        val verifier = ConfigVerifier(Base64.getEncoder().encodeToString(other.public.encoded))
        val payload = "anything".toByteArray()
        assertFalse(verifier.verify(payload, sign(payload, kp.private)))
    }

    @Test
    fun `bundled default config parses`() {
        val cfg = ConfigRepository.DEFAULT
        assertTrue(cfg.carrier == "mtn-ng")
        assertTrue(cfg.quickAnswers.containsKey("airtimeBalance"))
    }
}