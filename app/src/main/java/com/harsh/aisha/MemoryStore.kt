
package com.harsh.aisha

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local encrypted memory.
 *
 * Encryption: AES-256-GCM
 * Key storage: Android Keystore
 *
 * No network access is performed by this class.
 */
class MemoryStore(context: Context) {

    private val appContext = context.applicationContext

    private val preferences = appContext.getSharedPreferences(
        "nishtha_secure_memory",
        Context.MODE_PRIVATE
    )

    private val legacyPreferences = appContext.getSharedPreferences(
        "aisha_local_memory",
        Context.MODE_PRIVATE
    )

    @Volatile
    var lastError: String? = null
        private set

    init {
        migrateLegacyNotes()
    }

    @Synchronized
    fun getAll(): List<String> {
        return try {
            lastError = null
            readEncryptedNotes()
        } catch (error: Exception) {
            lastError = "Memory could not be decrypted. Existing data was not overwritten."
            emptyList()
        }
    }

    @Synchronized
    fun add(note: String): Boolean {
        val cleaned = note.trim()
        if (cleaned.isBlank()) return false

        return try {
            val notes = readEncryptedNotes().toMutableList()

            if (notes.contains(cleaned)) {
                lastError = null
                return true
            }

            notes.add(cleaned)
            writeEncryptedNotes(notes)

            lastError = null
            true
        } catch (error: Exception) {
            lastError = "Secure memory save failed. Please try again."
            false
        }
    }

    @Synchronized
    fun delete(note: String) {
        try {
            val notes = readEncryptedNotes().toMutableList()
            notes.remove(note)
            writeEncryptedNotes(notes)
            lastError = null
        } catch (error: Exception) {
            lastError = "Memory could not be updated."
        }
    }

    @Synchronized
    fun clearAll() {
        try {
            preferences.edit()
                .remove(KEY_ENCRYPTED_NOTES)
                .commit()

            lastError = null
        } catch (error: Exception) {
            lastError = "Memory could not be cleared."
        }
    }

    private fun migrateLegacyNotes() {
        synchronized(this) {
            if (preferences.contains(KEY_ENCRYPTED_NOTES)) return

            val oldNotes = try {
                legacyPreferences
                    .getStringSet("notes", emptySet())
                    .orEmpty()
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }

            if (oldNotes.isEmpty()) {
                preferences.edit()
                    .putString(KEY_ENCRYPTED_NOTES, "[]")
                    .commit()
                return
            }

            try {
                writeEncryptedNotes(oldNotes)

                // Delete old plaintext notes only after encrypted save succeeds.
                legacyPreferences.edit()
                    .remove("notes")
                    .commit()

                lastError = null
            } catch (error: Exception) {
                // Preserve the original notes if migration fails.
                lastError = "Old memories could not be migrated yet."
            }
        }
    }

    private fun readEncryptedNotes(): List<String> {
        val stored = preferences.getString(KEY_ENCRYPTED_NOTES, null)
            ?: return emptyList()

        if (stored == "[]") return emptyList()

        val array = JSONArray(stored)
        val result = ArrayList<String>(array.length())

        for (index in 0 until array.length()) {
            val encrypted = array.getString(index)
            val decrypted = decrypt(encrypted)
            if (decrypted.isNotBlank()) result.add(decrypted)
        }

        return result.distinct().sorted()
    }

    private fun writeEncryptedNotes(notes: List<String>) {
        val array = JSONArray()

        notes.distinct().forEach { note ->
            array.put(encrypt(note))
        }

        val saved = preferences.edit()
            .putString(KEY_ENCRYPTED_NOTES, array.toString())
            .commit()

        check(saved) { "Could not persist encrypted memory." }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encryptedBytes = cipher.doFinal(
            plainText.toByteArray(Charsets.UTF_8)
        )

        val iv = cipher.iv
        val combined = ByteBuffer
            .allocate(iv.size + encryptedBytes.size)
            .put(iv)
            .put(encryptedBytes)
            .array()

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)

        require(combined.size > GCM_IV_LENGTH) {
            "Invalid encrypted memory record."
        }

        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)

        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        return String(
            cipher.doFinal(encryptedBytes),
            Charsets.UTF_8
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "nishtha_local_memory_aes_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_ENCRYPTED_NOTES = "encrypted_notes_v1"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
