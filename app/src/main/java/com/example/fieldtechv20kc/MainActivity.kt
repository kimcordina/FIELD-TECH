package com.example.fieldtechv20kc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.fieldtechv20kc.data.remote.firestore.UsersRemote
import com.example.fieldtechv20kc.navigation.MainNavigation
import com.example.fieldtechv20kc.notifications.PushRegistrar
import com.example.fieldtechv20kc.ui.screens.SplashScreen
import com.example.fieldtechv20kc.ui.theme.AppTheme
import com.example.fieldtechv20kc.utils.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Register FCM token when user is signed in
        val auth = FirebaseAuth.getInstance()
        
        // Register token if already signed in
        if (auth.currentUser != null) {
            registerFcmToken()
        }
        
        // Listen for auth state changes to register token after sign-in
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                registerFcmToken()
            }
        }
        
        setContent {
            SplashScreenWrapper()
        }
    }
    
    private fun registerFcmToken() {
        val usersRemote = UsersRemote()
        lifecycleScope.launch {
            try {
                PushRegistrar(this@MainActivity, usersRemote).ensureRegistered()
                Log.d("MainActivity", "FCM token registered successfully")
            } catch (e: Exception) {
                // Silently fail - notifications are not critical
                // Common in emulators without proper Google Play Services
                Log.w("MainActivity", "FCM token registration failed (expected in emulator): ${e.message}")
            }
        }
    }
}

@Composable
fun SplashScreenWrapper() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    var showSplash by remember { mutableStateOf(true) }
    var initialRoute by remember { mutableStateOf<String?>(null) }
    
    // Check for navigation intent from notification
    LaunchedEffect(Unit) {
        activity?.intent?.getStringExtra("navigate_to")?.let { route ->
            initialRoute = route
        }
    }
    
    AppTheme(
        isDarkMode = settings.isDarkMode,
        accentColor = settings.accentColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSplash) {
                SplashScreen(
                    onNavigateToMain = {
                        showSplash = false
                    }
                )
            } else {
                MainNavigation(initialRoute = initialRoute)
            }
        }
    }
}