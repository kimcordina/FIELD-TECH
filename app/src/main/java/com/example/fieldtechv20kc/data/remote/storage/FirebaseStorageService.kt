package com.example.fieldtechv20kc.data.remote.storage

import android.net.Uri
import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseStorageService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun root(): StorageReference =
        storage.reference.child("companies").child(companyId)

    fun requestAudioRef(requestId: String, fileName: String): StorageReference =
        root().child("requests").child(requestId).child("audio").child(fileName)

    fun taskAudioRef(taskId: String, fileName: String): StorageReference =
        root().child("tasks").child(taskId).child("audio").child(fileName)

    fun requestPhotoRef(requestId: String, fileName: String): StorageReference =
        root().child("requests").child(requestId).child("photos").child(fileName)

    fun taskPhotoRef(taskId: String, fileName: String): StorageReference =
        root().child("tasks").child(taskId).child("photos").child(fileName)

    suspend fun uploadFromUri(dest: StorageReference, uri: Uri): String {
        dest.putFile(uri).awaitKtx()
        return dest.path // e.g. /companies/NCORDINA/requests/{id}/audio/voice_...m4a
    }

    suspend fun uploadFromFile(dest: StorageReference, file: File): String {
        dest.putFile(Uri.fromFile(file)).awaitKtx()
        return dest.path
    }

    suspend fun downloadUrl(path: String): Uri {
        val ref = storage.getReference(path)
        return ref.downloadUrl.awaitKtx()
    }
}

suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitKtx(): T =
    suspendCancellableCoroutine { c ->
        addOnCompleteListener { t ->
            if (t.isSuccessful) c.resume(t.result)
            else c.resumeWithException(t.exception ?: RuntimeException("Storage task failed"))
        }
    }
