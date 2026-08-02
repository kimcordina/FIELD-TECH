package com.example.fieldtechv20kc.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportPhotoDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CloudReportsViewModel(
    private val remote: ReportsRemote = ReportsRemote(),
    private val storage: ReportStorage = ReportStorage()
) : ViewModel() {

    val cloudReports: StateFlow<List<ReportCloudDto>> =
        remote.listenAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun pdfUrl(dto: ReportCloudDto): Uri? =
        dto.pdfPath?.let { storage.downloadUrl(it) }

    suspend fun photoUrl(path: String): Uri =
        storage.downloadUrl(path)
    
    fun listenPhotos(reportId: Long): StateFlow<List<ReportPhotoDto>> =
        remote.listenPhotos(reportId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

