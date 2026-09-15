package app.tapcode.config

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** MP-02 / section 11: signature gate for remote carrier configs. */
class ConfigVerifier(publicKeyPem: String) {

    private val publicKey = try {
        val kf = KeyFactory.getInstance("RSA")
        kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPem)))
    } catch (_: Exception) {
        null
    }

    fun verify(payload: ByteArray, signature: ByteArray): Boolean {
        val key = publicKey ?: return false
        return try {
            val s = Signature.getInstance("SHA256withRSA")
            s.initVerify(key)
            s.update(payload)
            s.verify(signature)
        } catch (_: Exception) {
            false
        }
    }
}