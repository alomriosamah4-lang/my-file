#include <jni.h>
#include <string>
#include <vector>

#if defined(HAVE_SODIUM)
#include <sodium.h>
#endif

#if defined(HAVE_ARGON2)
#include <argon2.h>
#endif

static void throw_link_error(JNIEnv* env, const char* msg) {
    jclass exClass = env->FindClass("java/lang/UnsatisfiedLinkError");
    if (exClass) env->ThrowNew(exClass, msg);
}

static void zeroize_buf(unsigned char* buf, size_t len) {
#if defined(HAVE_SODIUM)
    sodium_memzero(buf, len);
#else
    volatile unsigned char* p = buf;
    while (len--) *p++ = 0;
#endif
}

// Helper: convert jlong handle to key pointer
static std::vector<unsigned char>* to_keyptr(jlong handle) {
    return reinterpret_cast<std::vector<unsigned char>*>(intptr_t(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_securevault_NativeCrypto_jniInit(JNIEnv* env, jclass clazz) {
#if defined(HAVE_SODIUM)
    if (sodium_init() < 0) {
        // initialization failed
    }
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_generateSalt(JNIEnv* env, jclass clazz) {
    const int len = 16;
    std::vector<unsigned char> salt(len);
#if defined(HAVE_SODIUM)
    randombytes_buf(salt.data(), len);
#else
    throw_link_error(env, "libsodium not available: cryptographic operations disabled");
    jbyteArray empty = env->NewByteArray(0);
    return empty;
#endif
    jbyteArray out = env->NewByteArray(len);
    env->SetByteArrayRegion(out, 0, len, reinterpret_cast<jbyte*>(salt.data()));
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_securevault_NativeCrypto_selfTest(JNIEnv* env, jclass clazz) {
#if defined(HAVE_SODIUM) && defined(HAVE_ARGON2)
    // Quick runtime self-test: sodium init, argon2 derive, AEAD roundtrip
    if (sodium_init() < 0) return JNI_FALSE;

    // Argon2 derivation test
    const char* password = "test-password";
    unsigned char salt[16];
    randombytes_buf(salt, sizeof(salt));
    unsigned char out_key[32];
    if (argon2id_hash_raw(2, 65536, 1, password, strlen(password), salt, sizeof(salt), out_key, sizeof(out_key)) != ARGON2_OK) {
        return JNI_FALSE;
    }

    // AEAD roundtrip test
    const unsigned char msg[] = "hello world";
    unsigned long long clen = 0;
    unsigned char nonce[24];
    randombytes_buf(nonce, sizeof(nonce));
    unsigned char cbuf[1024];
    if (crypto_aead_xchacha20poly1305_ietf_encrypt(cbuf, &clen, msg, sizeof(msg)-1, NULL, 0, NULL, nonce, out_key) != 0) {
        return JNI_FALSE;
    }
    unsigned char mbuf[1024];
    unsigned long long mlen = 0;
    if (crypto_aead_xchacha20poly1305_ietf_decrypt(mbuf, &mlen, NULL, cbuf, clen, NULL, 0, nonce, out_key) != 0) {
        return JNI_FALSE;
    }
    if (mlen != sizeof(msg)-1) return JNI_FALSE;

    return JNI_TRUE;
#else
#endif
    // If compiled without required crypto libs, fail fast at runtime
    throw_link_error(env, "Required native crypto libraries not available");
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_deriveKey(JNIEnv* env, jclass clazz, jbyteArray password, jbyteArray salt) {
    const int keyLen = 32;
    std::vector<unsigned char> key(keyLen);

    jsize passLen = env->GetArrayLength(password);
    std::vector<unsigned char> pass(passLen);
    env->GetByteArrayRegion(password, 0, passLen, reinterpret_cast<jbyte*>(pass.data()));

    jsize saltLen = env->GetArrayLength(salt);
    std::vector<unsigned char> s(saltLen);
    env->GetByteArrayRegion(salt, 0, saltLen, reinterpret_cast<jbyte*>(s.data()));

#if defined(HAVE_ARGON2)
    // Use Argon2id: time=2, mem=65536 KB, parallelism=1 as example (tunable)
    uint32_t t = 2;
    uint32_t m = 65536;
    uint32_t p = 1;
    if (argon2id_hash_raw(t, m, p, pass.data(), pass.size(), s.data(), s.size(), key.data(), key.size()) != ARGON2_OK) {
        // fallback to zeroed key on failure
        for (int i = 0; i < keyLen; ++i) key[i] = 0;
    }
#else
    throw_link_error(env, "argon2 not available: key derivation disabled");
    jbyteArray empty = env->NewByteArray(0);
    // zero sensitive buffers
    zeroize_buf(pass.data(), pass.size());
    return empty;
#endif

    zeroize_buf(pass.data(), pass.size());

    jbyteArray out = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(out, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));
    zeroize_buf(key.data(), keyLen);
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_generateMasterKey(JNIEnv* env, jclass clazz) {
    const int keyLen = 32;
    std::vector<unsigned char> key(keyLen);
#if defined(HAVE_SODIUM)
    randombytes_buf(key.data(), keyLen);
#else
    throw_link_error(env, "libsodium not available: secure RNG disabled");
    jbyteArray empty = env->NewByteArray(0);
    return empty;
#endif
    jbyteArray out = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(out, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));
    zeroize_buf(key.data(), keyLen);
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_wrapMasterKey(JNIEnv* env, jclass clazz, jbyteArray masterKey) {
    throw_link_error(env, "wrapMasterKey should be performed by Keystore in Kotlin; native stub disabled");
    jbyteArray empty = env->NewByteArray(0);
    return empty;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_unwrapMasterKey(JNIEnv* env, jclass clazz, jbyteArray wrapped) {
    // Unwrapping handled by Keystore in Kotlin; stub returns copy
    jsize len = env->GetArrayLength(wrapped);
    jbyteArray out = env->NewByteArray(len);
    jbyte* buf = env->GetByteArrayElements(wrapped, NULL);
    env->SetByteArrayRegion(out, 0, len, buf);
    env->ReleaseByteArrayElements(wrapped, buf, JNI_ABORT);
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_encryptChunk(JNIEnv* env, jclass clazz, jbyteArray masterKey, jbyteArray chunk, jint sequence) {
    jsize len = env->GetArrayLength(chunk);
    jbyte* buf = env->GetByteArrayElements(chunk, NULL);
#if defined(HAVE_SODIUM)
    // get master key bytes passed from Java
    jsize keyLen = env->GetArrayLength(masterKey);
    std::vector<unsigned char> key((size_t)keyLen);
    if (keyLen > 0) {
        env->GetByteArrayRegion(masterKey, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));
    }

    unsigned long long clen = 0;
    unsigned char nonce[24];
    randombytes_buf(nonce, sizeof(nonce));

    // allocate ciphertext buffer: plaintext + MAC
    std::vector<unsigned char> cbuf(len + crypto_aead_xchacha20poly1305_ietf_ABYTES);

    if (crypto_aead_xchacha20poly1305_ietf_encrypt(cbuf.data(), &clen,
        reinterpret_cast<unsigned char*>(buf), (unsigned long long)len,
        NULL, 0, NULL, nonce, key.data()) != 0) {
        // encryption failed; cleanup and return empty
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        zeroize_buf(key.data(), keyLen);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }

    // Prepare output = nonce || ciphertext
    jsize outLen = (jsize)(sizeof(nonce) + clen);
    jbyteArray out = env->NewByteArray(outLen);
    // copy nonce first
    env->SetByteArrayRegion(out, 0, (jsize)sizeof(nonce), reinterpret_cast<jbyte*>(nonce));
    // then ciphertext
    env->SetByteArrayRegion(out, (jsize)sizeof(nonce), (jsize)clen, reinterpret_cast<jbyte*>(cbuf.data()));

    // zeroize sensitive buffers
    zeroize_buf(key.data(), keyLen);
    zeroize_buf(cbuf.data(), cbuf.size());
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#else
    // If libsodium not available, fail explicitly
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    throw_link_error(env, "libsodium not available: encryption disabled");
    jbyteArray empty = env->NewByteArray(0);
    return empty;
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_decryptChunk(JNIEnv* env, jclass clazz, jbyteArray masterKey, jbyteArray chunk, jint sequence) {
    jsize len = env->GetArrayLength(chunk);
    jbyte* buf = env->GetByteArrayElements(chunk, NULL);
#if defined(HAVE_SODIUM)
#define NONCE_LEN 24
    if (len <= NONCE_LEN) {
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }

    // Extract nonce and ciphertext
    const unsigned char* nonce = reinterpret_cast<unsigned char*>(buf);
    const unsigned char* cdata = reinterpret_cast<unsigned char*>(buf + NONCE_LEN);
    unsigned long long cmlen = (unsigned long long)(len - NONCE_LEN);

    // get master key bytes
    jsize keyLen = env->GetArrayLength(masterKey);
    std::vector<unsigned char> key((size_t)keyLen);
    if (keyLen > 0) {
        env->GetByteArrayRegion(masterKey, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));
    }

    std::vector<unsigned char> outBuf(cmlen); // ciphertext length upper bound
    unsigned long long mlen = 0;
    if (crypto_aead_xchacha20poly1305_ietf_decrypt(outBuf.data(), &mlen,
        NULL, cdata, cmlen,
        NULL, 0, nonce, key.data()) != 0) {
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        zeroize_buf(key.data(), keyLen);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }

    jbyteArray out = env->NewByteArray((jsize)mlen);
    env->SetByteArrayRegion(out, 0, (jsize)mlen, reinterpret_cast<jbyte*>(outBuf.data()));
    zeroize_buf(outBuf.data(), outBuf.size());
    zeroize_buf(key.data(), keyLen);
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#else
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    throw_link_error(env, "libsodium not available: decryption disabled");
    jbyteArray empty = env->NewByteArray(0);
    return empty;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_securevault_NativeCrypto_createSession(JNIEnv* env, jclass clazz, jbyteArray masterKey) {
    jsize keyLen = env->GetArrayLength(masterKey);
    std::vector<unsigned char>* key = new std::vector<unsigned char>((size_t)keyLen);
    if (keyLen > 0) {
        env->GetByteArrayRegion(masterKey, 0, keyLen, reinterpret_cast<jbyte*>(key->data()));
    }
    // return pointer as handle
    return (jlong)(intptr_t(key));
}

extern "C" JNIEXPORT void JNICALL
Java_com_securevault_NativeCrypto_destroySession(JNIEnv* env, jclass clazz, jlong handle) {
    auto key = to_keyptr(handle);
    if (key) {
        if (!key->empty()) zeroize_buf(key->data(), key->size());
        delete key;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_encryptWithSession(JNIEnv* env, jclass clazz, jlong handle, jbyteArray chunk, jint sequence) {
    std::vector<unsigned char>* key = to_keyptr(handle);
    if (!key) {
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    jsize len = env->GetArrayLength(chunk);
    jbyte* buf = env->GetByteArrayElements(chunk, NULL);
#if defined(HAVE_SODIUM)
    unsigned long long clen = 0;
    unsigned char nonce[24];
    randombytes_buf(nonce, sizeof(nonce));
    std::vector<unsigned char> cbuf(len + crypto_aead_xchacha20poly1305_ietf_ABYTES);
    if (crypto_aead_xchacha20poly1305_ietf_encrypt(cbuf.data(), &clen,
        reinterpret_cast<unsigned char*>(buf), (unsigned long long)len,
        NULL, 0, NULL, nonce, key->data()) != 0) {
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    jsize outLen = (jsize)(sizeof(nonce) + clen);
    jbyteArray out = env->NewByteArray(outLen);
    env->SetByteArrayRegion(out, 0, (jsize)sizeof(nonce), reinterpret_cast<jbyte*>(nonce));
    env->SetByteArrayRegion(out, (jsize)sizeof(nonce), (jsize)clen, reinterpret_cast<jbyte*>(cbuf.data()));
    zeroize_buf(cbuf.data(), cbuf.size());
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#else
    jbyteArray out = env->NewByteArray(len);
    env->SetByteArrayRegion(out, 0, len, buf);
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_securevault_NativeCrypto_decryptWithSession(JNIEnv* env, jclass clazz, jlong handle, jbyteArray chunk, jint sequence) {
    std::vector<unsigned char>* key = to_keyptr(handle);
    if (!key) {
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    jsize len = env->GetArrayLength(chunk);
    jbyte* buf = env->GetByteArrayElements(chunk, NULL);
#if defined(HAVE_SODIUM)
    const int NONCE_LEN = 24;
    if (len <= NONCE_LEN) {
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    const unsigned char* nonce = reinterpret_cast<unsigned char*>(buf);
    const unsigned char* cdata = reinterpret_cast<unsigned char*>(buf + NONCE_LEN);
    unsigned long long cmlen = (unsigned long long)(len - NONCE_LEN);
    std::vector<unsigned char> outBuf(cmlen);
    unsigned long long mlen = 0;
    if (crypto_aead_xchacha20poly1305_ietf_decrypt(outBuf.data(), &mlen,
        NULL, cdata, cmlen,
        NULL, 0, nonce, key->data()) != 0) {
        env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    jbyteArray out = env->NewByteArray((jsize)mlen);
    env->SetByteArrayRegion(out, 0, (jsize)mlen, reinterpret_cast<jbyte*>(outBuf.data()));
    zeroize_buf(outBuf.data(), outBuf.size());
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#else
    jbyteArray out = env->NewByteArray(len);
    env->SetByteArrayRegion(out, 0, len, buf);
    env->ReleaseByteArrayElements(chunk, buf, JNI_ABORT);
    return out;
#endif
}
