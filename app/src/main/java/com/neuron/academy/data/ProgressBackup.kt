package com.neuron.academy.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.neuron.academy.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Сохранение прогресса в файл и восстановление из него.
 * В файл попадают база с прогрессом (темы, тесты, уровень, карточки, достижения, чат)
 * и настройки. API-ключи не попадают: для них есть отдельный зашифрованный экспорт.
 */
class ProgressBackup(private val context: Context, private val db: AppDatabase) {

    private val dbFile get() = context.getDatabasePath(DB_NAME)
    private val settingsFile get() = File(context.applicationInfo.dataDir, "shared_prefs/$SETTINGS_PREFS.xml")

    suspend fun export(target: Uri): Unit = withContext(Dispatchers.IO) {
        // переносим всё из журнала WAL в основной файл базы, чтобы копия была полной
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(target) ?: error("Не удалось создать файл")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MARKER)); zip.write("neuron-academy-backup v1".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_NAME)); dbFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            if (settingsFile.exists()) {
                zip.putNextEntry(ZipEntry("$SETTINGS_PREFS.xml")); settingsFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /**
     * Проверяет файл и подменяет им текущий прогресс. После успешного восстановления
     * приложение нужно перезапустить ([restartApp]).
     */
    suspend fun import(source: Uri): Unit = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var marker = false
            val input = context.contentResolver.openInputStream(source) ?: error("Не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (name == MARKER) marker = true
                    // берём только известные файлы, никаких путей вида ../
                    if (name == DB_NAME || name == "$SETTINGS_PREFS.xml") {
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val newDb = File(tmp, DB_NAME)
            if (!marker || !newDb.exists()) error("Это не файл прогресса «Нейрон: Академия»")
            val header = newDb.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) error("Файл прогресса повреждён")
            val version = RandomAccessFile(newDb, "r").use { it.seek(60); it.readInt() }
            if (version > DB_VERSION) error("Файл сохранён более новой версией приложения. Сначала обнови приложение")

            db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            newDb.copyTo(dbFile, overwrite = true)
            File(tmp, "$SETTINGS_PREFS.xml").takeIf { it.exists() }?.copyTo(settingsFile.apply { parentFile?.mkdirs() }, overwrite = true)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Перезапуск, чтобы приложение открыло восстановленную базу и настройки. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val DB_NAME = "neuron.db"
        const val DB_VERSION = AppDatabase.VERSION
        const val SETTINGS_PREFS = "neuron_settings"
        private const val MARKER = "neuron-backup.txt"

        fun fileName(): String {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            return "neuron-progress-$d.zip"
        }
    }
}
