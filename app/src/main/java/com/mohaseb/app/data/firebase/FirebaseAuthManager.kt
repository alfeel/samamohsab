package com.mohaseb.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    val currentUserEmail: String
        get() = auth.currentUser?.email ?: ""

    suspend fun login(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("تعذر استرداد بيانات المستخدم")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, pass: String, storeName: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("تعذر إنشاء الحساب")

            // إنشاء ملف التعريف والتفعيل السحابي للمستخدم
            val profile = hashMapOf(
                "userId" to user.uid,
                "email" to user.email,
                "storeName" to storeName,
                "isActive" to true,
                "createdAt" to System.currentTimeMillis(),
                "plan" to "شامل"
            )
            firestore.collection("users").document(user.uid)
                .collection("profile").document("info")
                .set(profile).await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkCloudActivation(): Boolean {
        val user = currentUser ?: return false
        return try {
            val doc = firestore.collection("users").document(user.uid)
                .collection("profile").document("info").get().await()
            doc.getBoolean("isActive") ?: true
        } catch (_: Exception) {
            // في حال عدم توفر اتصال، نسمح بالدخول مؤقتاً طالما هو مسجل
            true
        }
    }

    fun logout() {
        auth.signOut()
    }
}
