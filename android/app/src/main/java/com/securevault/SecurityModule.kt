package com.securevault

import android.app.KeyguardManager
import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.ReadableMap
import java.util.concurrent.ConcurrentHashMap

/**
 * SecurityModule: Kotlin Native Module that exposes secure vault operations to JS.
 * Core crypto operations are delegated to native (NDK) via NativeCrypto.
 * This implementation contains safe stubs and orchestration logic. Native
 * implementations must follow SECURITY_MODEL.md and side-channel mitigations.
 */
class SecurityModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    init {
        NativeCrypto.jniInit() // load native lib and initialize
        // Perform one-time migration of any legacy wrapped DB key from prefs -> encrypted DB
        KeystoreMigration.migrateDbWrappedToDb(reactApplicationContext)
    }

    override fun getName(): String = "SecurityModule"

    private val sessions = ConcurrentHashMap<String, Long>()

    @ReactMethod
    fun isDeviceSecure(promise: Promise) {
        try {
            val km = reactApplicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val secure = km.isDeviceSecure
            // TODO: add root/debug detection here
            promise.resolve(secure)
        } catch (e: Exception) {
            promise.reject("ERR_INTERNAL", "Failed to detect device security", e)
        }
    }

    @ReactMethod
    fun nativeSelfTest(promise: Promise) {
        try {
            val ok = NativeCrypto.selfTest()
            promise.resolve(ok)
        } catch (e: Exception) {
            promise.reject("ERR_SELF_TEST", "Native self-test failed", e)
        }
    }

    @ReactMethod
    fun createVault(name: String, password: String, options: ReadableMap?, promise: Promise) {
        try {
            val salt = NativeCrypto.generateSalt()
            val derived = NativeCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), salt)
            val masterKey = NativeCrypto.generateMasterKey()

            // Wrap master key using Android Keystore (per-vault alias)
            val vaultId = java.util.UUID.randomUUID().toString()
            val wrapped = KeystoreManager.wrapMasterKey(reactApplicationContext, vaultId, masterKey)

            // Ensure DB initialized: get or create DB passphrase (wrapped by keystore)
            val dbPass = KeystoreManager.getOrCreateDbPassphrase(reactApplicationContext)
            db.DBManager.init(reactApplicationContext, dbPass)

            // Persist vault metadata and wrapped key into encrypted DB
            db.DBManager.insertVault(reactApplicationContext, dbPass, vaultId, name, salt, wrapped)

            val result: WritableMap = Arguments.createMap()
            result.putString("vaultId", vaultId)
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_CREATE_VAULT", "Failed to create vault", e)
        }
    }

    @ReactMethod
    fun openVault(vaultId: String, passwordOrToken: String, promise: Promise) {
        try {
            val dbPass = KeystoreManager.getOrCreateDbPassphrase(reactApplicationContext)
            db.DBManager.init(reactApplicationContext, dbPass)
            val wrapped = db.DBManager.getWrappedKey(reactApplicationContext, dbPass, vaultId)
                ?: run {
                    promise.reject("ERR_VAULT_NOT_FOUND", "Vault not found")
                    return
                }

            // Unwrap master key via Keystore (per-vault alias)
            val masterKey = KeystoreManager.unwrapMasterKey(reactApplicationContext, vaultId, wrapped)

            // Create native session to keep key out of Java heap
            val sessionHandle = try {
                NativeCrypto.createSession(masterKey)
            } catch (t: Throwable) {
                // zeroize masterKey before failing
                for (i in masterKey.indices) masterKey[i] = 0
                promise.reject("ERR_NATIVE", "Native crypto unavailable", t)
                return
            }

            // zeroize masterKey in Java heap immediately
            for (i in masterKey.indices) masterKey[i] = 0

            // Store session handle in ephemeral session map (boxed Long)
            val sessionToken = java.util.UUID.randomUUID().toString()
            sessions[sessionToken] = sessionHandle

            val result: WritableMap = Arguments.createMap()
            result.putString("sessionToken", sessionToken)
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_OPEN_VAULT", "Failed to open vault", e)
        }
    }

    @ReactMethod
    fun closeVault(sessionToken: String, promise: Promise) {
        try {
            val handleObj = sessions.remove(sessionToken)
            if (handleObj != null) {
                val handle = handleObj
                try {
                    NativeCrypto.destroySession(handle)
                } catch (t: Throwable) {
                    // ignore
                }
            }
            promise.resolve(null as Void?)
        } catch (e: Exception) {
            promise.reject("ERR_CLOSE_VAULT", "Failed to close vault", e)
        }
    }

    @ReactMethod
    fun changePassword(vaultId: String, oldPassword: String, newPassword: String, promise: Promise) {
        try {
            val dbPass = KeystoreManager.getOrCreateDbPassphrase(reactApplicationContext)
            db.DBManager.init(reactApplicationContext, dbPass)

            val wrapped = db.DBManager.getWrappedKey(reactApplicationContext, dbPass, vaultId)
                ?: run {
                    promise.reject("ERR_VAULT_NOT_FOUND", "Vault not found")
                    return
                }

            // Unwrap master key using Keystore
            val masterKey = try {
                KeystoreManager.unwrapMasterKey(reactApplicationContext, vaultId, wrapped)
            } catch (t: Throwable) {
                promise.reject("ERR_UNWRAP_FAILED", "Failed to unwrap master key", t)
                return
            }

            try {
                // Derive new key material using native Argon2 with newPassword and existing salt
                // fetch salt from DB
                val saltCursor = db.DBManager.getVaultSalt(reactApplicationContext, dbPass, vaultId)
                if (saltCursor == null) {
                    // zeroize
                    for (i in masterKey.indices) masterKey[i] = 0
                    promise.reject("ERR_MISSING_SALT", "Vault salt missing")
                    return
                }

                val derived = try {
                    NativeCrypto.deriveKey(newPassword.toByteArray(Charsets.UTF_8), saltCursor)
                } catch (t: Throwable) {
                    for (i in masterKey.indices) masterKey[i] = 0
                    promise.reject("ERR_DERIVE_FAILED", "Failed to derive key", t)
                    return
                }

                // Re-wrap master key. Use Keystore wrapMasterKey via KeystoreManager (vault-specific alias)
                val newWrapped = KeystoreManager.wrapMasterKey(reactApplicationContext, vaultId, masterKey)

                // Store new wrapped master key in DB inside a transaction
                db.DBManager.updateWrappedKey(reactApplicationContext, dbPass, vaultId, newWrapped)

                // zeroize sensitive buffers
                for (i in masterKey.indices) masterKey[i] = 0
                for (i in derived.indices) derived[i] = 0

                promise.resolve(null as Void?)
            } catch (e: Exception) {
                for (i in masterKey.indices) masterKey[i] = 0
                promise.reject("ERR_CHANGE_PASSWORD", "Failed to change password", e)
            }
        } catch (e: Exception) {
            promise.reject("ERR_CHANGE_PASSWORD", "Failed to change password", e)
        }
    }

    @ReactMethod
    fun enableBiometric(vaultId: String, promise: Promise) {
        try {
            val dbPass = KeystoreManager.getOrCreateDbPassphrase(reactApplicationContext)
            db.DBManager.init(reactApplicationContext, dbPass)

            val wrapped = db.DBManager.getWrappedKey(reactApplicationContext, dbPass, vaultId)
                ?: run {
                    promise.reject("ERR_VAULT_NOT_FOUND", "Vault not found")
                    return
                }

            // Unwrap existing master key
            val masterKey = try {
                KeystoreManager.unwrapMasterKey(reactApplicationContext, vaultId, wrapped)
            } catch (t: Throwable) {
                promise.reject("ERR_UNWRAP_FAILED", "Failed to unwrap master key", t)
                return
            }

            try {
                // Re-wrap master key with userAuthenticationRequired=true so Keystore enforces biometric
                val newWrapped = KeystoreManager.wrapMasterKey(reactApplicationContext, vaultId, masterKey, true)

                // Persist updated wrapped key
                db.DBManager.updateWrappedKey(reactApplicationContext, dbPass, vaultId, newWrapped)

                // zeroize
                for (i in masterKey.indices) masterKey[i] = 0

                promise.resolve(true)
            } catch (e: Exception) {
                for (i in masterKey.indices) masterKey[i] = 0
                promise.reject("ERR_BIOMETRIC", "Failed to enable biometric", e)
            }
        } catch (e: Exception) {
            promise.reject("ERR_BIOMETRIC", "Failed to enable biometric", e)
        }
    }
}

