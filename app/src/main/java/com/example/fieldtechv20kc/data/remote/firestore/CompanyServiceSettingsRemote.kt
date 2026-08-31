package com.example.fieldtechv20kc.data.remote.firestore

import com.example.fieldtechv20kc.BuildConfig
import com.example.fieldtechv20kc.data.model.ServiceDueThresholds
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Company-wide Service due thresholds.
 * Path: companies/{companyId}/config/serviceDue
 */
class CompanyServiceSettingsRemote(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun doc() =
        db.collection("companies").document(companyId)
            .collection("config").document("serviceDue")

    suspend fun getThresholds(): ServiceDueThresholds {
        val snap = doc().get().await()
        if (!snap.exists()) return ServiceDueThresholds.DEFAULT
        return ServiceDueThresholds(
            soonMonths = (snap.getLong("soonMonths") ?: 1L).toInt().coerceIn(1, 24),
            lateMonths = (snap.getLong("lateMonths") ?: 2L).toInt().coerceIn(1, 24),
            overdueMonths = (snap.getLong("overdueMonths") ?: 3L).toInt().coerceIn(1, 36),
            starredOverdueMonths = (snap.getLong("starredOverdueMonths") ?: 1L).toInt().coerceIn(1, 12)
        )
    }

    suspend fun saveThresholds(thresholds: ServiceDueThresholds) {
        doc().set(
            mapOf(
                "soonMonths" to thresholds.soonMonths,
                "lateMonths" to thresholds.lateMonths,
                "overdueMonths" to thresholds.overdueMonths,
                "starredOverdueMonths" to thresholds.starredOverdueMonths,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }
}
