package com.mohaseb.app.data.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.mohaseb.app.data.db.DatabaseHelper
import com.mohaseb.app.data.model.CloudSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreSyncManager(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val gson = Gson()

    /**
     * رفع كافة البيانات المحلية إلى Cloud Firestore
     */
    suspend fun syncLocalToCloud(): Result<String> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("يرجى تسجيل الدخول أولاً"))

        try {
            val payload = dbHelper.exportAllData()
            val jsonString = gson.toJson(payload)

            val backupDoc = hashMapOf(
                "lastSyncTime" to System.currentTimeMillis(),
                "userEmail" to user.email,
                "dataJson" to jsonString,
                "customersCount" to payload.customers.size,
                "itemsCount" to payload.items.size,
                "billsCount" to payload.bills.size,
                "transactionsCount" to payload.transactions.size
            )

            firestore.collection("users").document(user.uid)
                .collection("cloud_backup").document("latest")
                .set(backupDoc).await()

            val total = payload.items.size + payload.bills.size + payload.customers.size
            dbHelper.insertSyncLog("نجاح", "تمت المزامنة مع Cloud Firestore بنجاح", total)
            Result.success("تم رفع $total سجل إلى السحابة بنجاح")
        } catch (e: Exception) {
            val err = "فشل الرفع إلى السحابة: ${e.localizedMessage ?: e.message}"
            dbHelper.insertSyncLog("فشل", err, 0)
            Result.failure(Exception(err))
        }
    }

    /**
     * تنزيل البيانات من Cloud Firestore إلى قاعدة البيانات المحلية
     */
    suspend fun syncCloudToLocal(): Result<String> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("يرجى تسجيل الدخول أولاً"))

        try {
            val doc = firestore.collection("users").document(user.uid)
                .collection("cloud_backup").document("latest").get().await()

            if (!doc.exists()) {
                return@withContext Result.failure(Exception("لا توجد نسخة سحابية سابقة لهذا الحساب"))
            }

            val jsonString = doc.getString("dataJson")
                ?: return@withContext Result.failure(Exception("البيانات السحابية فارغة"))

            val payload = gson.fromJson(jsonString, CloudSyncPayload::class.java)
                ?: return@withContext Result.failure(Exception("تعذر معالجة البيانات السحابية"))

            dbHelper.importAllData(payload)

            val total = payload.items.size + payload.bills.size + payload.customers.size
            dbHelper.insertSyncLog("نجاح", "تمت استعادة $total سجل من السحابة بنجاح", total)
            Result.success("تم استيراد $total سجل من السحابة بنجاح")
        } catch (e: Exception) {
            val err = "فشل تنزيل البيانات من السحابة: ${e.localizedMessage ?: e.message}"
            dbHelper.insertSyncLog("فشل", err, 0)
            Result.failure(Exception(err))
        }
    }
}
