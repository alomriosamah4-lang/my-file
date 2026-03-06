package com.securevault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.spec.RSAKeyGenParameterSpec
import javax.crypto.Cipher
import java.security.KeyStore
import javax.crypto.KeyGenerator

object KeystoreManager {

    private const val KEY_ALIAS_PREFIX = "sv_master_"
    private const val DB_KEY_ALIAS = "sv_db_key"
    private const val PREFS_NAME = "sv_keystore_prefs"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ensureKeystoreKey(context: Context, alias: String, userAuthRequired: Boolean = false) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            // On API >= 23 we can create AES keys inside AndroidKeyStore and use AES/GCM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val specBuilder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationValidityDurationSeconds(-1)
                if (userAuthRequired) {
                    // require auth for each use
                    specBuilder.setUserAuthenticationRequired(true)
                    specBuilder.setUserAuthenticationValidityDurationSeconds(0)
                    specBuilder.setInvalidatedByBiometricEnrollment(true)
                }
                keyGenerator.init(specBuilder.build())
                keyGenerator.generateKey()
            } else {
                // Fallback for older devices: generate an RSA keypair and use it to wrap/unwrap symmetric keys
                val kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
                // 2048-bit RSA key is sufficient to wrap small symmetric keys
                val spec = RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)
                kpg.initialize(spec)
                kpg.generateKeyPair()
            }
        }
    }

    fun wrapWithAlias(context: Context, alias: String, data: ByteArray): ByteArray {
        ensureKeystoreKey(context, alias)
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(alias, null)
        return when (entry) {
            is KeyStore.SecretKeyEntry -> {
                val secretKey = entry.secretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(data)
                val out = ByteArray(1 + iv.size + cipherText.size)
                // prefix with version/type byte = 1 (AES/GCM)
                out[0] = 1
                System.arraycopy(iv, 0, out, 1, iv.size)
                System.arraycopy(cipherText, 0, out, 1 + iv.size, cipherText.size)
                // zero sensitive plaintext as soon as possible
                data.fill(0)
                cipherText.fill(0)
                out
            }
            is KeyStore.PrivateKeyEntry -> {
                // RSA wrapping fallback for older devices
                val pub = entry.certificate.publicKey
                val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
                cipher.init(Cipher.ENCRYPT_MODE, pub)
                val cipherText = cipher.doFinal(data)
                val out = ByteArray(1 + cipherText.size)
                out[0] = 2 // type 2 = RSA wrapped
                System.arraycopy(cipherText, 0, out, 1, cipherText.size)
                data.fill(0)
                cipherText.fill(0)
                out
            }
            else -> throw IllegalStateException("Unsupported keystore entry for alias $alias")
        }
    }

    fun unwrapWithAlias(context: Context, alias: String, wrapped: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (wrapped.isEmpty()) throw IllegalArgumentException("invalid wrapped key")
        val typ = wrapped[0].toInt()
        when (typ) {
            1 -> {
                // AES/GCM
                val ivLen = 12
                if (wrapped.size <= 1 + ivLen) throw IllegalArgumentException("invalid wrapped key")
                val iv = wrapped.copyOfRange(1, 1 + ivLen)
                val cipherText = wrapped.copyOfRange(1 + ivLen, wrapped.size)
                val entry = keyStore.getEntry(alias, null)
                if (entry !is KeyStore.SecretKeyEntry) throw IllegalStateException("Expected SecretKeyEntry for AES unwrap")
                val secretKey = entry.secretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val out = cipher.doFinal(cipherText)
                cipherText.fill(0)
                out
            }
            2 -> {
                // RSA unwrap
                val cipherText = wrapped.copyOfRange(1, wrapped.size)
                val entry = keyStore.getEntry(alias, null)
                if (entry !is KeyStore.PrivateKeyEntry) throw IllegalStateException("Expected PrivateKeyEntry for RSA unwrap")
                val priv = entry.privateKey
                val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
                cipher.init(Cipher.DECRYPT_MODE, priv)
                val out = cipher.doFinal(cipherText)
                cipherText.fill(0)
                out
            }
            else -> throw IllegalArgumentException("unknown wrapped key type")
        }
    }

    // Vault-specific wrapping
    fun wrapMasterKey(context: Context, vaultId: String, masterKey: ByteArray, userAuthRequired: Boolean = false): ByteArray {
        val alias = KEY_ALIAS_PREFIX + vaultId
        ensureKeystoreKey(context, alias, userAuthRequired)
        return wrapWithAlias(context, alias, masterKey)
    }

    fun unwrapMasterKey(context: Context, vaultId: String, wrapped: ByteArray): ByteArray {
        val alias = KEY_ALIAS_PREFIX + vaultId
        return unwrapWithAlias(context, alias, wrapped)
    }

    // DB passphrase management: wrapped under a dedicated alias
    fun getOrCreateDbPassphrase(context: Context): ByteArray {
        val stored = prefs(context).getString("db_wrapped", null)
        if (stored != null) {
            val wrapped = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
            return unwrapWithAlias(context, DB_KEY_ALIAS, wrapped)
        }
        // generate a strong random passphrase
        val pass = NativeCrypto.generateMasterKey() // 32 bytes random
        val wrapped = wrapWithAlias(context, DB_KEY_ALIAS, pass)
        val b64 = android.util.Base64.encodeToString(wrapped, android.util.Base64.NO_WRAP)
        prefs(context).edit().putString("db_wrapped", b64).apply()
        return pass
    }
