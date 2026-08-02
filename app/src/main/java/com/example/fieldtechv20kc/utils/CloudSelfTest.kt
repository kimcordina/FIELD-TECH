package com.example.fieldtechv20kc.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

data class CloudSelfTestResult(
    val ok: Boolean,
    val steps: List<String>
)

object CloudSelfTest {

    suspend fun run(context: Context): CloudSelfTestResult {
        val steps = mutableListOf<String>()
        try {
            // 1) App / bucket / user
            val app = FirebaseApp.getInstance()
            val opts = app.options
            val bucket = opts.storageBucket ?: "(null)"
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                steps += "❌ Not signed in (FirebaseAuth.currentUser == null)"
                return CloudSelfTestResult(false, steps)
            }
            steps += "✅ Signed in as uid=${user.uid}"
            steps += "ℹ️ ProjectId=${opts.projectId}  Bucket=$bucket"

            // 2) Firestore write/read
            val db = FirebaseFirestore.getInstance()
            val companyId = BuildConfig.COMPANY_ID
            val ts = System.currentTimeMillis()
            val docRef = db.collection("companies").document(companyId)
                .collection("healthchecks").document(ts.toString())

            val payload = mapOf(
                "who" to (user.email ?: user.uid),
                "ts" to ts,
                "device" to android.os.Build.MODEL
            )
            docRef.set(payload).await()
            val snap = docRef.get().await()
            if (!snap.exists()) {
                steps += "❌ Firestore doc not readable after write"
                return CloudSelfTestResult(false, steps)
            }
            steps += "✅ Firestore write/read ok at companies/$companyId/healthchecks/$ts"

            // 3) Storage upload/getUrl/delete
            val storage = FirebaseStorage.getInstance() // uses default bucket from google-services.json
            val testBytes = "ping-${user.uid}-$ts".toByteArray()
            val path = "companies/$companyId/healthchecks/${user.uid}/ping_$ts.txt"
            val ref = storage.reference.child(path)
            ref.putBytes(testBytes).await()
            val url: Uri = ref.downloadUrl.await()
            steps += "✅ Storage upload ok at $path"
            steps += "ℹ️ Download URL resolved (${url.toString().take(60)}...)"
            // try delete to confirm write permission fully works
            ref.delete().await()
            steps += "✅ Storage delete ok"

            return CloudSelfTestResult(true, steps)
        } catch (t: Throwable) {
            steps += "❌ Exception: ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
            Log.e("FT/CLOUD_TEST", "Self-test failed", t)
            return CloudSelfTestResult(false, steps)
        }
    }
}




