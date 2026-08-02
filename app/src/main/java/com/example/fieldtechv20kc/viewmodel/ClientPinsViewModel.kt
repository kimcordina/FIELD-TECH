package com.example.fieldtechv20kc.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.model.ClientPinEntity
import com.example.fieldtechv20kc.data.model.ClientPinInput
import com.example.fieldtechv20kc.data.model.PinStatus
import com.example.fieldtechv20kc.data.repository.ClientPinsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class ClientPinsViewModel(context: Context) : ViewModel() {
    
    private val repository: ClientPinsRepository
    private val fusedLocationClient: FusedLocationProviderClient
    
    private val _isLoadingLocation = MutableStateFlow(false)
    val isLoadingLocation: StateFlow<Boolean> = _isLoadingLocation
    
    init {
        val app = context.applicationContext as com.example.fieldtechv20kc.FieldTechApplication
        repository = app.pinsRepository
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }
    
    fun observePins(clientId: String): Flow<List<ClientPinEntity>> {
        return repository.observePins(clientId)
    }
    
    /**
     * Add a pin from pasted text (link or coordinates)
     * Creates a SEEDED pin
     */
    fun addFromPaste(clientId: String, label: String, text: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val parsed = ClientPinsRepository.parseLocationInput(text)
                
                if (parsed == null) {
                    onResult(Result.failure(Exception("Invalid input format. Please paste coordinates or a Google Maps link.")))
                    return@launch
                }
                
                val (lat, lng) = parsed
                
                val input = ClientPinInput(
                    clientId = clientId,
                    label = label.ifBlank { "Location" },
                    latitude = lat,
                    longitude = lng,
                    status = PinStatus.SEEDED,
                    sourceUrl = if (text.startsWith("http")) text else null
                )
                
                val pinId = repository.addOrUpdate(input)
                onResult(Result.success(pinId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    
    /**
     * Add a pin from current GPS location
     * Creates a VERIFIED pin
     * Requires ACCESS_FINE_LOCATION permission
     */
    fun addFromCurrentLocation(
        context: Context,
        clientId: String,
        label: String,
        onResult: (Result<String>) -> Unit
    ) {
        // Check permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(Result.failure(SecurityException("Location permission not granted")))
            return
        }
        
        viewModelScope.launch {
            _isLoadingLocation.value = true
            try {
                // Get current location with 10 second timeout
                val location = withTimeout(10_000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                }
                
                if (location == null) {
                    onResult(Result.failure(Exception("Could not get location. Please try again.")))
                    _isLoadingLocation.value = false
                    return@launch
                }
                
                val input = ClientPinInput(
                    clientId = clientId,
                    label = label.ifBlank { "Current location" },
                    latitude = location.latitude,
                    longitude = location.longitude,
                    status = PinStatus.VERIFIED,
                    sourceUrl = null
                )
                
                val pinId = repository.addOrUpdate(input)
                onResult(Result.success(pinId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isLoadingLocation.value = false
            }
        }
    }
    
    /**
     * Capture the device's current GPS coordinates WITHOUT saving a pin.
     * Used by the client creation screen, where the client id doesn't exist yet -
     * the coordinates are held in UI state and saved after the client is created.
     * Requires ACCESS_FINE_LOCATION permission.
     */
    fun captureCurrentLocation(
        context: Context,
        onResult: (Result<Pair<Double, Double>>) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(Result.failure(SecurityException("Location permission not granted")))
            return
        }
        
        viewModelScope.launch {
            _isLoadingLocation.value = true
            try {
                val location = withTimeout(10_000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                }
                
                if (location == null) {
                    onResult(Result.failure(Exception("Could not get location. Please try again.")))
                } else {
                    onResult(Result.success(location.latitude to location.longitude))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isLoadingLocation.value = false
            }
        }
    }
    
    /**
     * Save an already-captured pin for a client.
     * Suspend so callers can await completion before navigating away.
     */
    suspend fun addPin(
        clientId: String,
        label: String,
        latitude: Double?,
        longitude: Double?,
        status: PinStatus,
        sourceUrl: String? = null,
        setAsPrimary: Boolean = false
    ): String {
        val pinId = repository.addOrUpdate(
            ClientPinInput(
                clientId = clientId,
                label = label.ifBlank { "Location" },
                latitude = latitude,
                longitude = longitude,
                status = status,
                sourceUrl = sourceUrl
            )
        )
        if (setAsPrimary) {
            repository.setPrimary(clientId, pinId)
        }
        return pinId
    }
    
    /**
     * Set a pin as primary for this client
     * Clears any existing primary pin first
     */
    fun setPrimary(clientId: String, pinId: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.setPrimary(clientId, pinId)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    
    /**
     * Delete a pin
     */
    fun deletePin(pinId: String, clientId: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.delete(pinId, clientId)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    
    /**
     * Get the primary pin for navigation
     * Falls back to first pin if no primary is set
     */
    suspend fun getPinForNavigation(clientId: String): ClientPinEntity? {
        return repository.getPrimary(clientId) ?: repository.getFirstPin(clientId)
    }
}
