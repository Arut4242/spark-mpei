package ru.mpei.assistant.data.local

import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class DatabaseEncryptionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key ?: ""] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { temp[key ?: ""] = null; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                for ((k, v) in temp) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    @Test
    fun testDatabasePassphraseGenerationAndPersistence() {
        val prefs = FakeSharedPreferences()
        val storage = SessionStorage(prefs)

        val passphrase1 = storage.getOrCreateDatabasePassphrase()
        assertEquals(32, passphrase1.size)

        // Повторный вызов должен возвращать в точности тот же сохранённый ключ
        val passphrase2 = storage.getOrCreateDatabasePassphrase()
        assertArrayEquals(passphrase1, passphrase2)

        // Проверяем формат hex-строки в SharedPreferences
        val storedHex = prefs.getString("key_database_passphrase", null)
        assertTrue(!storedHex.isNullOrBlank())
        assertEquals(64, storedHex!!.length)
        assertArrayEquals(passphrase1, SessionStorage.hexToBytes(storedHex))
    }

    @Test
    fun testHexConversionRoundtrip() {
        val originalBytes = byteArrayOf(0x00, 0x0F, 0x10, 0x7F, -0x80, -0x01)
        val hex = SessionStorage.bytesToHex(originalBytes)
        val decodedBytes = SessionStorage.hexToBytes(hex)
        assertArrayEquals(originalBytes, decodedBytes)
    }

    @Test
    fun testPlaintextDatabaseHeaderDetection() {
        val plaintextDb = tempFolder.newFile("legacy_plaintext.db")
        FileOutputStream(plaintextDb).use { fos ->
            fos.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            fos.write(ByteArray(100) { 0 })
        }
        assertTrue(AppDatabase.isPlaintextDatabase(plaintextDb))

        val encryptedDb = tempFolder.newFile("encrypted_sqlcipher.db")
        FileOutputStream(encryptedDb).use { fos ->
            fos.write(ByteArray(116) { (it * 31).toByte() })
        }
        assertFalse(AppDatabase.isPlaintextDatabase(encryptedDb))

        val emptyDb = tempFolder.newFile("empty.db")
        assertFalse(AppDatabase.isPlaintextDatabase(emptyDb))

        val nonExistentDb = File(tempFolder.root, "does_not_exist.db")
        assertFalse(AppDatabase.isPlaintextDatabase(nonExistentDb))
    }
}
