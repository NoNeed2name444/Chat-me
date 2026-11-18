package com.cramdown.sorta.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// The vault (S08 A3): a credential is kept only as AES-GCM ciphertext under a key that never leaves the Android
// Keystore. The file sits in noBackupFilesDir, so it is never in a backup or a device transfer, and uninstalling
// removes the file and the Keystore key together, so a new install never sees an old credential.

interface AIVault {
    fun save(secret: String, provider: AIProviderId)
    fun read(provider: AIProviderId): String?
    fun delete(provider: AIProviderId)
}

/** The stand-in for tests. */
class AIMemoryVault : AIVault {
    private val items = HashMap<AIProviderId, String>()
    @Synchronized override fun save(secret: String, provider: AIProviderId) { items[provider] = secret }
    @Synchronized override fun read(provider: AIProviderId): String? = items[provider]
    @Synchronized override fun delete(provider: AIProviderId) { items.remove(provider) }
}

class AIKeystoreVault(private val folder: File) : AIVault {
    companion object {
        const val ALIAS = "sorta.ai."
        private const val STORE = "AndroidKeyStore"
        private const val CIPHER = "AES/GCM/NoPadding"
    }

    private fun file(p: AIProviderId) = File(folder, p.raw + ".bin")

    private fun keyStore(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key(p: AIProviderId, create: Boolean): SecretKey? {
        val store = keyStore()
        (store.getKey(ALIAS + p.raw, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS + p.raw, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    @Synchronized
    override fun save(secret: String, provider: AIProviderId) {
        try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, key(provider, true))
            val sealed = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            folder.mkdirs()
            val tmp = File(folder, provider.raw + ".tmp")
            tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + sealed)
            if (!tmp.renameTo(file(provider))) { tmp.delete(); throw AIError.of(AIError.Reason.VAULT) }
        } catch (e: AIError) {
            throw e
        } catch (_: Exception) {
            throw AIError.of(AIError.Reason.VAULT)
        }
    }

    @Synchronized
    override fun read(provider: AIProviderId): String? {
        val f = file(provider)
        if (!f.exists()) return null
        try {
            val key = key(provider, false) ?: return null
            val bytes = f.readBytes()
            val n = bytes[0].toInt()
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 1 + n)))
            return String(cipher.doFinal(bytes.copyOfRange(1 + n, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            throw AIError.of(AIError.Reason.VAULT)
        }
    }

    @Synchronized
    override fun delete(provider: AIProviderId) {
        file(provider).delete()
        try {
            val store = keyStore()
            if (store.containsAlias(ALIAS + provider.raw)) store.deleteEntry(ALIAS + provider.raw)
        } catch (_: Exception) {
            throw AIError.of(AIError.Reason.VAULT)
        }
        if (file(provider).exists()) throw AIError.of(AIError.Reason.VAULT)
    }
}
