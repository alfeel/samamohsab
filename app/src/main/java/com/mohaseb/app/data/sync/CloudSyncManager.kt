package com.mohaseb.app.data.sync

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mohaseb.app.data.db.DatabaseHelper
import com.mohaseb.app.data.model.CloudSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudSyncManager(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJsonString(): String = withContext(Dispatchers.IO) {
        val payload = dbHelper.exportAllData()
        gson.toJson(payload)
    }

    suspend fun importFromJsonString(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = gson.fromJson(jsonString, CloudSyncPayload::class.java)
                ?: return@withContext Result.failure(Exception("ملف البيانات غير صالح"))

            dbHelper.importAllData(payload)
            val count = payload.items.size + payload.customers.size + payload.bills.size
            dbHelper.insertSyncLog("نجاح", "تم استيراد $count سجل بنجاح", count)
            Result.success(count)
        } catch (e: Exception) {
            dbHelper.insertSyncLog("فشل", "فشل الاستيراد: ${e.message}", 0)
            Result.failure(e)
        }
    }

    suspend fun createLocalBackupFile(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val json = exportToJsonString()
            val backupDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MohasebBackups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val file = File(backupDir, "backup_mohaseb_$timestamp.json")

            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            }

            dbHelper.insertSyncLog("نجاح", "تم إنشاء نسخة احتياطية محلية: ${file.name}", 1)
            Result.success(file)
        } catch (e: Exception) {
            dbHelper.insertSyncLog("فشل", "فشل إنشاء النسخة الاحتياطية: ${e.message}", 0)
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackupFile(file: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = FileInputStream(file).use { fis ->
                InputStreamReader(fis, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
            importFromJsonString(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncWithCloudServer(customUrl: String? = null, customApiKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val settings = dbHelper.getStoreSettings()
        val targetUrl = customUrl?.ifBlank { null } ?: settings.cloudSyncUrl
        val apiKey = customApiKey?.ifBlank { null } ?: settings.cloudSyncApiKey

        if (targetUrl.isBlank()) {
            return@withContext Result.failure(Exception("لم يتم تحديد عنوان خادم المزامنة في الإعدادات"))
        }

        try {
            val payload = dbHelper.exportAllData()
            val jsonPayload = gson.toJson(payload)

            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            // إرسال البيانات
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }

                // إذا كان الخادم يرجع بيانات محدثة للدمج
                try {
                    val remotePayload = gson.fromJson(responseText, CloudSyncPayload::class.java)
                    if (remotePayload != null && remotePayload.items.isNotEmpty()) {
                        dbHelper.importAllData(remotePayload)
                    }
                } catch (_: Exception) {
                    // الخادم أرجع رسالة تأكيد عادية
                }

                val totalSynced = payload.items.size + payload.bills.size + payload.customers.size
                dbHelper.insertSyncLog("نجاح", "تمت المزامنة السحابية بنجاح مع الخادم", totalSynced)
                Result.success("تمت المزامنة السحابية بنجاح ($totalSynced سجل)")
            } else {
                val err = "خطأ في الاتصال بالخادم: رمز الاستجابة $responseCode"
                dbHelper.insertSyncLog("فشل", err, 0)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "فشل الاتصال بخادم المزامنة: ${e.localizedMessage ?: e.message}"
            dbHelper.insertSyncLog("فشل", err, 0)
            Result.failure(Exception(err))
        }
    }
}
