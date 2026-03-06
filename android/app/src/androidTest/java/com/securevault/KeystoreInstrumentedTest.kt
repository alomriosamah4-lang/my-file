package com.securevault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreInstrumentedTest {

    @Test
    fun wrapAndUnwrapMasterKey_roundtrip() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // generate master key via native crypto when available, otherwise produce random
        val masterKey = try {
            NativeCrypto.generateMasterKey()
        } catch (t: Throwable) {
            // fallback to 32 random bytes
            ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        }

        val vaultId = "test-vault"
        val wrapped = KeystoreManager.wrapMasterKey(appContext, vaultId, masterKey)
        // ensure wrapped isn't empty
        Assert.assertTrue(wrapped.isNotEmpty())

        val unwrapped = KeystoreManager.unwrapMasterKey(appContext, vaultId, wrapped)
        Assert.assertArrayEquals(masterKey, unwrapped)

        // zero masterKey and unwrapped
        for (i in masterKey.indices) masterKey[i] = 0
        for (i in unwrapped.indices) unwrapped[i] = 0
    }
}
