package com.example.fieldtechv20kc.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fieldtechv20kc.ui.screens.*
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ReportViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: ReportViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, viewModel = viewModel)
        }
        
        composable(Screen.ClientInfo.route) {
            ClientInfoScreen(navController = navController, viewModel = viewModel)
        }
        
        composable(Screen.JobType.route) {
            JobTypeScreen(navController = navController, viewModel = viewModel)
        }
        
        composable(Screen.JobDocumentation.route) {
            JobDocumentationScreen(navController = navController, viewModel = viewModel)
        }
        
        composable(Screen.Camera.route) { backStackEntry ->
            CameraScreen(
                navController = navController,
                onPhotoCaptured = { photoPath ->
                    // This will be handled by the calling screen
                    navController.previousBackStackEntry?.savedStateHandle?.set("captured_photo", photoPath)
                }
            )
        }
        
        composable(Screen.Signature.route) {
            SignatureScreen(navController = navController, viewModel = viewModel)
        }
        
        composable(Screen.ReportDetail.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId")?.toLongOrNull()
            if (reportId != null) {
                ReportDetailScreen(
                    reportId = reportId,
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
        
        composable(Screen.JobDocumentationSettings.route) {
            val context = LocalContext.current
            JobDocumentationSettingsScreen(
                navController = navController,
                settingsManager = SettingsManager.getInstance(context)
            )
        }
    }
}
