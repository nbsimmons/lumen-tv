package app.lumen.tv.data.session

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.util.Calendar
import javax.security.auth.x500.X500Principal
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureCipher(private val context: Context) {
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) keystoreKey() else wrappedKey()

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun keystoreKey(): SecretKey =
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
                generateKey()
            }

    @Suppress("DEPRECATION")
    private fun wrappedKey(): SecretKey {
        val file = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)
        val privateKey = keyStore.getKey(WRAP_ALIAS, null) as? PrivateKey
        if (privateKey != null && file.exists()) {
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply { init(Cipher.UNWRAP_MODE, privateKey) }
            return cipher.unwrap(file.readBytes(), "AES", Cipher.SECRET_KEY) as SecretKey
        }
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, WRAP_KEY_YEARS) }
        val pair = KeyPairGenerator.getInstance("RSA", KEYSTORE).run {
            initialize(
                KeyPairGeneratorSpec.Builder(context)
                    .setAlias(WRAP_ALIAS)
                    .setSubject(X500Principal("CN=$WRAP_ALIAS"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.time)
                    .setEndDate(end.time)
                    .build(),
            )
            generateKeyPair()
        }
        val aes = SecretKeySpec(ByteArray(AES_BYTES).also(SecureRandom()::nextBytes), "AES")
        val wrapper = Cipher.getInstance(WRAP_TRANSFORMATION).apply { init(Cipher.WRAP_MODE, pair.public) }
        file.writeBytes(wrapper.wrap(aes))
        return aes
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "lumen_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val WRAP_ALIAS = "lumen_session_wrap"
        const val WRAP_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        const val WRAPPED_KEY_FILE = "session.key"
        const val WRAP_KEY_YEARS = 30
        const val AES_BYTES = 32
    }
}
