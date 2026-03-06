package com.securevault.db

import android.content.Context
import android.util.Base64
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

private const val DB_NAME = "secure_vault.db"
private const val DB_VERSION = 1

class SecureVaultOpenHelper(context: Context, private val passphraseB64: String) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    init {
        // load SQLCipher libs
        SQLiteDatabase.loadLibs(context)
    }

    private fun passphraseString(): String = passphraseB64

    // Open (writable) database using the provided passphrase
    fun openDatabase(): SQLiteDatabase {
        return this.getWritableDatabase(passphraseString())
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create tables according to DATABASE_SCHEMA.md
        db.execSQL("""
            CREATE TABLE vaults(
                id TEXT PRIMARY KEY,
                name TEXT,
                salt BLOB,
                wrapped_master_key BLOB,
                created_at INTEGER,
                updated_at INTEGER,
                quota_bytes INTEGER
            )""")

        db.execSQL("""
            CREATE TABLE files(
                id TEXT PRIMARY KEY,
                vault_id TEXT,
                filename TEXT,
                mime TEXT,
                size INTEGER,
                chunk_count INTEGER,
                metadata TEXT,
                created_at INTEGER
            )""")

        db.execSQL("""
            CREATE TABLE file_chunks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_id TEXT,
                sequence INTEGER,
                blob BLOB,
                length INTEGER
            )""")

        db.execSQL("""
            CREATE TABLE settings(
                key TEXT PRIMARY KEY,
                value TEXT
            )""")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Migration logic goes here
    }
}

object DBManager {
    private var helper: SecureVaultOpenHelper? = null

    fun init(context: Context, passphrase: ByteArray) {
        val passphraseB64 = Base64.encodeToString(passphrase, Base64.NO_WRAP)
        helper = SecureVaultOpenHelper(context, passphraseB64)
        // zero caller passphrase asap
        passphrase.fill(0)
        // Open writable to ensure DB is created and accessible using the passphrase
        val db = helper!!.openDatabase()
        db.close()
    }

    fun insertVault(context: Context, passphrase: ByteArray, id: String, name: String, salt: ByteArray, wrappedMaster: ByteArray) {
        try {
            val db = helper!!.openDatabase()
        val stmt = db.compileStatement("INSERT OR REPLACE INTO vaults(id,name,salt,wrapped_master_key,created_at,updated_at,quota_bytes) VALUES(?,?,?,?,?,?,?)")
        stmt.bindString(1, id)
        stmt.bindString(2, name)
        stmt.bindBlob(3, salt)
        stmt.bindBlob(4, wrappedMaster)
        val now = System.currentTimeMillis()
        stmt.bindLong(5, now)
        stmt.bindLong(6, now)
        stmt.bindLong(7, 0)
        stmt.executeInsert()
        stmt.close()
        db.close()
        } finally {
            passphrase.fill(0)
        }
    }

    fun getWrappedKey(context: Context, passphrase: ByteArray, vaultId: String): ByteArray? {
        try {
            val db = helper!!.openDatabase()
            val cursor = db.rawQuery("SELECT wrapped_master_key FROM vaults WHERE id = ?", arrayOf(vaultId))
            val res: ByteArray? = if (cursor.moveToFirst()) {
                cursor.getBlob(0)
            } else null
            cursor.close()
            db.close()
            return res
        } finally {
            passphrase.fill(0)
        }
    }

    fun getVaultSalt(context: Context, passphrase: ByteArray, vaultId: String): ByteArray? {
        try {
            val db = helper!!.openDatabase()
            val cursor = db.rawQuery("SELECT salt FROM vaults WHERE id = ?", arrayOf(vaultId))
            val res: ByteArray? = if (cursor.moveToFirst()) {
                cursor.getBlob(0)
            } else null
            cursor.close()
            db.close()
            return res
        } finally {
            passphrase.fill(0)
        }
    }

    fun updateWrappedKey(context: Context, passphrase: ByteArray, vaultId: String, newWrapped: ByteArray) {
        try {
            val db = helper!!.openDatabase()
            val stmt = db.compileStatement("UPDATE vaults SET wrapped_master_key = ?, updated_at = ? WHERE id = ?")
            stmt.bindBlob(1, newWrapped)
            stmt.bindLong(2, System.currentTimeMillis())
            stmt.bindString(3, vaultId)
            stmt.execute()
            stmt.close()
            db.close()
        } finally {
            passphrase.fill(0)
        }
    }

    fun setSetting(context: Context, passphrase: ByteArray, key: String, value: String) {
        try {
            val db = helper!!.openDatabase()
            val stmt = db.compileStatement("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)")
            stmt.bindString(1, key)
            stmt.bindString(2, value)
            stmt.executeInsert()
            stmt.close()
            db.close()
        } finally {
            passphrase.fill(0)
        }
    }
}
