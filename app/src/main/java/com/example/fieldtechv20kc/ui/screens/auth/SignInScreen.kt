package com.example.fieldtechv20kc.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
  val auth = remember { FirebaseAuth.getInstance() }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  fun signIn() {
    if (email.isBlank() || password.isBlank()) { 
      error = "Please enter both email and password"
      return 
    }
    loading = true
    error = null
    auth.signInWithEmailAndPassword(email.trim(), password)
      .addOnCompleteListener { t ->
        loading = false
        if (t.isSuccessful) {
          // Register FCM token after successful login
          CoroutineScope(Dispatchers.IO).launch {
            try {
              com.example.fieldtechv20kc.notifications.NotificationHelper.registerToken()
              android.util.Log.d("SignIn", "FCM token registered successfully")
            } catch (e: Exception) {
              android.util.Log.e("SignIn", "Failed to register FCM token", e)
            }
          }
          onSignedIn()
        }
        else error = t.exception?.localizedMessage ?: "Sign-in failed. Please check your credentials."
      }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // App Title and Subtitle
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "NC Field Tech",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Professional Field Service",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Email Field
        OutlinedTextField(
          value = email,
          onValueChange = { 
            email = it
            error = null
          },
          label = { Text("Email Address") },
          placeholder = { Text("your.email@example.com") },
          leadingIcon = {
            Icon(
              Icons.Default.Email,
              contentDescription = "Email",
              tint = MaterialTheme.colorScheme.primary
            )
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          )
        )

        // Password Field
        OutlinedTextField(
          value = password,
          onValueChange = { 
            password = it
            error = null
          },
          label = { Text("Password") },
          placeholder = { Text("Enter your password") },
          leadingIcon = {
            Icon(
              Icons.Default.Lock,
              contentDescription = "Password",
              tint = MaterialTheme.colorScheme.primary
            )
          },
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(
                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          )
        )

        // Error Message
        if (error != null) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = error!!,
              modifier = Modifier.padding(12.dp),
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sign In Button
        Button(
          onClick = { if (!loading) signIn() },
          enabled = !loading,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          if (loading) {
            CircularProgressIndicator(
              strokeWidth = 2.dp,
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(12.dp))
          }
          Text(
            text = if (loading) "Signing in..." else "Sign In",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        // Version Info
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Version ${com.example.fieldtechv20kc.BuildConfig.VERSION_NAME}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

