package com.securevault

object NativeCrypto {
    init {
        // load native library named "crypto_bridge"
        try {
            System.loadLibrary("crypto_bridge")
            nativeLoaded = true
        } catch (t: Throwable) {
            // library may be absent in debug; callers will see isNativeAvailable() == false
            nativeLoaded = false
        }
    }
    private var nativeLoaded: Boolean = false

    private fun ensureLoaded() {
        if (!nativeLoaded) throw UnsatisfiedLinkError("Native crypto library not loaded")
    }

    // Private native bindings
    private external fun _jniInit()
    private external fun _generateSalt(): ByteArray
    private external fun _deriveKey(password: ByteArray, salt: ByteArray): ByteArray
    private external fun _generateMasterKey(): ByteArray
    private external fun _wrapMasterKey(masterKey: ByteArray): ByteArray
    private external fun _unwrapMasterKey(wrapped: ByteArray): ByteArray
    private external fun _encryptChunk(masterKey: ByteArray, chunk: ByteArray, sequence: Int): ByteArray
    private external fun _decryptChunk(masterKey: ByteArray, chunk: ByteArray, sequence: Int): ByteArray
    private external fun _selfTest(): Boolean

    // Public safe wrappers
    fun jniInit() {
        if (!nativeLoaded) return
        try {
            _jniInit()
        } catch (t: Throwable) {
            // swallow native init errors and keep nativeLoaded flag
            nativeLoaded = false
            throw t
        }
    }

    fun generateSalt(): ByteArray {
        ensureLoaded()
        return _generateSalt()
    }

    fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        ensureLoaded()
        // copy password to avoid accidental retention if caller mutates later
        val pwCopy = password.copyOf()
        try {
            return _deriveKey(pwCopy, salt)
        } finally {
            pwCopy.fill(0)
        }
    }

    fun generateMasterKey(): ByteArray {
        ensureLoaded()
        return _generateMasterKey()
    }

    fun wrapMasterKey(masterKey: ByteArray): ByteArray {
        ensureLoaded()
        val mkCopy = masterKey.copyOf()
        try {
            return _wrapMasterKey(mkCopy)
        } finally {
            mkCopy.fill(0)
        }
    }

    fun unwrapMasterKey(wrapped: ByteArray): ByteArray {
        ensureLoaded()
        return _unwrapMasterKey(wrapped)
    }

    fun encryptChunk(masterKey: ByteArray, chunk: ByteArray, sequence: Int): ByteArray {
        ensureLoaded()
        return _encryptChunk(masterKey, chunk, sequence)
    }

    fun decryptChunk(masterKey: ByteArray, chunk: ByteArray, sequence: Int): ByteArray {
        ensureLoaded()
        return _decryptChunk(masterKey, chunk, sequence)
    }

    fun selfTest(): Boolean {
        if (!nativeLoaded) return false
        return try {
            _selfTest()
        } catch (t: Throwable) {
            false
        }
    }

    fun isNativeAvailable(): Boolean = nativeLoaded

    // Session-based APIs to avoid keeping master key in Java heap
    private external fun _createSession(masterKey: ByteArray): Long
    private external fun _destroySession(handle: Long)
    private external fun _encryptWithSession(handle: Long, chunk: ByteArray, sequence: Int): ByteArray
    private external fun _decryptWithSession(handle: Long, chunk: ByteArray, sequence: Int): ByteArray

    fun createSession(masterKey: ByteArray): Long {
        ensureLoaded()
        return _createSession(masterKey)
    }

    fun destroySession(handle: Long) {
        ensureLoaded()
        _destroySession(handle)
    }

    fun encryptWithSession(handle: Long, chunk: ByteArray, sequence: Int): ByteArray {
        ensureLoaded()
        return _encryptWithSession(handle, chunk, sequence)
    }

    fun decryptWithSession(handle: Long, chunk: ByteArray, sequence: Int): ByteArray {
        ensureLoaded()
        return _decryptWithSession(handle, chunk, sequence)
    }
}
