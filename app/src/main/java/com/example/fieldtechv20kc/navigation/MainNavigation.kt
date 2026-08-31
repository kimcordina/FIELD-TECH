package com.example.fieldtechv20kc.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistryOwner
import com.example.fieldtechv20kc.FieldTechApplication
import com.example.fieldtechv20kc.ui.screens.*
import com.example.fieldtechv20kc.ui.screens.auth.SignInScreen
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import com.google.firebase.auth.FirebaseAuth

// Factory for creating ReportViewModel with SavedStateHandle
@Composable
fun rememberReportViewModelFactory(
    owner: SavedStateRegistryOwner
): ViewModelProvider.Factory {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    
    return remember(owner) {
        object : AbstractSavedStateViewModelFactory(owner, null) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
            ): T {
                return ReportViewModel(application, handle) as T
            }
        }
    }
}

@Composable
fun MainNavigation(initialRoute: String? = null) {
  val navController = rememberNavController()
  val viewModel: ReportViewModel = viewModel()
  val isSignedIn = remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }

  DisposableEffect(Unit) {
    val auth = FirebaseAuth.getInstance()
    val listener = FirebaseAuth.AuthStateListener { fb -> isSignedIn.value = fb.currentUser != null }
    auth.addAuthStateListener(listener)
    onDispose { auth.removeAuthStateListener(listener) }
  }

  NavHost(
    navController = navController,
    startDestination = if (isSignedIn.value) "home" else "auth/signin"
  ) {
    composable("auth/signin") {
      SignInScreen(
        onSignedIn = {
          navController.navigate("home") {
            popUpTo("auth/signin") { inclusive = true }
          }
        }
      )
    }
    
    composable("home") {
      AuthenticatedApp(viewModel = viewModel, initialRoute = initialRoute)
    }
  }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Reports : BottomNavItem(
        route = Screen.SavedReports.route,
        title = "Reports",
        icon = Icons.Default.List
    )
    
    object Clients : BottomNavItem(
        route = Screen.Clients.route,
        title = "Clients",
        icon = Icons.Default.Business
    )

    object ServiceNeeds : BottomNavItem(
        route = Screen.ServiceNeeds.route,
        title = "Service",
        icon = Icons.Default.Build
    )
    
    object Tasks : BottomNavItem(
        route = Screen.Tasks.route,
        title = "Jobs",
        icon = Icons.Default.Assignment
    )
    
    object Requests : BottomNavItem(
        route = Screen.Requests.route,
        title = "Requests",
        icon = Icons.Default.RequestPage
    )
    
    object Settings : BottomNavItem(
        route = Screen.Settings.route,
        title = "Settings",
        icon = Icons.Default.Settings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedApp(
    navController: NavHostController = rememberNavController(),
    viewModel: ReportViewModel,
    initialRoute: String? = null
) {
    val items = listOf(
        BottomNavItem.Reports,
        BottomNavItem.Clients,
        BottomNavItem.ServiceNeeds,
        BottomNavItem.Tasks,
        BottomNavItem.Settings
    )
    
    // Navigate to initial route if provided
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            val route = when (initialRoute) {
                "tasks", "requests" -> Screen.Tasks.route // Requests merged into Jobs
                "reports" -> Screen.SavedReports.route
                "service", "service_needs" -> Screen.ServiceNeeds.route
                else -> null
            }
            if (route != null) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            com.example.fieldtechv20kc.ui.components.OfflineBanner()
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            ) 
                        },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            // Clear the back stack and navigate to the selected item
                            navController.navigate(item.route) {
                                // Pop up to the start destination to clear the back stack
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = false // Don't restore state to always go to root
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SavedReports.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Bottom Navigation Screens
            composable(Screen.SavedReports.route) {
                SavedReportsScreen(navController = navController, viewModel = viewModel)
            }
            
            composable("trash_bin") {
                TrashBinScreen(navController = navController)
            }
            
            composable(Screen.Statistics.route) {
                val context = LocalContext.current
                val database = com.example.fieldtechv20kc.data.database.AppDatabase.getDatabase(context)
                val statisticsRepository = com.example.fieldtechv20kc.data.repository.StatisticsRepository(database.statisticsDao())
                StatisticsScreen(
                    navController = navController,
                    statisticsRepository = statisticsRepository
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController, viewModel = viewModel)
            }
            
            // Diagnostics Screens
            composable(Screen.ErrorTray.route) {
                ErrorTrayScreen(navController = navController)
            }
            
            // Clients Tab
            composable(Screen.Clients.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                val tasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                        }
                    }
                )
                ClientsListScreen(
                    navController = navController, 
                    viewModel = clientsViewModel,
                    tasksViewModel = tasksViewModel
                )
            }

            // Service Needs Tab
            composable(Screen.ServiceNeeds.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val settingsManager = SettingsManager.getInstance(context)
                val serviceNeedsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceNeedsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceNeedsViewModel(
                                app.clientsRepository,
                                settingsManager
                            ) as T
                        }
                    }
                )
                val tasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                        }
                    }
                )
                var userRole by remember { mutableStateOf("NONE") }
                LaunchedEffect(Unit) {
                    try {
                        userRole = com.example.fieldtechv20kc.data.remote.firestore.UsersRemote()
                            .getProfile()?.role ?: "NONE"
                    } catch (_: Exception) {
                        userRole = "NONE"
                    }
                }
                ServiceNeedsScreen(
                    navController = navController,
                    viewModel = serviceNeedsViewModel,
                    tasksViewModel = tasksViewModel,
                    userRole = userRole
                )
            }

            composable(Screen.ServiceDueSettings.route) {
                ServiceDueSettingsScreen(navController = navController)
            }
            
            // Jobs Tab (unified: unassigned requests + assigned jobs)
            composable(Screen.Tasks.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val serviceTasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                        }
                    }
                )
                val requestsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel(app.requestsRepository) as T
                        }
                    }
                )
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                val settingsManager = SettingsManager.getInstance(context)
                val settings by settingsManager.settings.collectAsState()
                val technicianName = settings.defaultTechnicianName
                UnifiedJobsScreen(
                    navController = navController,
                    tasksViewModel = serviceTasksViewModel,
                    requestsViewModel = requestsViewModel,
                    clientsViewModel = clientsViewModel,
                    defaultTechnicianName = technicianName
                )
            }
            
            // Job Detail Screen
            composable(Screen.TaskDetail.route) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId")
                if (taskId != null) {
                    val context = LocalContext.current
                    val app = context.applicationContext as FieldTechApplication
                    val serviceTasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                            }
                        }
                    )
                    val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                            }
                        }
                    )
                    TaskDetailScreen(
                        taskId = taskId,
                        navController = navController,
                        tasksViewModel = serviceTasksViewModel,
                        clientsViewModel = clientsViewModel
                    )
                }
            }
            
            // Route Screens
            composable(Screen.SavedRoutes.route) {
                SavedRoutesScreen(navController = navController)
            }
            
            composable(Screen.RoutePlanner.route) { backStackEntry ->
                val jobIds = backStackEntry.arguments?.getString("jobIds") ?: ""
                val createdBy = backStackEntry.arguments?.getString("createdBy") ?: "Unknown"
                val intendedAssigneeArg = backStackEntry.arguments?.getString("intendedAssignee")
                val intendedAssignee = if (intendedAssigneeArg == "none") null else intendedAssigneeArg
                
                RoutePlannerScreen(
                    navController = navController,
                    jobIds = jobIds,
                    createdBy = createdBy,
                    intendedAssignee = intendedAssignee
                )
            }
            
            composable(Screen.RouteDetail.route) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                RouteDetailScreen(
                    navController = navController,
                    routeId = routeId
                )
            }
            
            // Legacy "requests" list route → same unified Jobs inbox
            composable(Screen.Requests.route) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Tasks.route) {
                        popUpTo(Screen.Requests.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            
            // Request Create Screen
            composable(Screen.RequestCreate.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val requestsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel(app.requestsRepository) as T
                        }
                    }
                )
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                RequestCreateScreen(
                    navController = navController,
                    requestsViewModel = requestsViewModel,
                    clientsViewModel = clientsViewModel
                )
            }
            
            // Request Detail Screen
            composable(Screen.RequestDetail.route) { backStackEntry ->
                val requestId = backStackEntry.arguments?.getString("requestId")
                if (requestId != null) {
                    val context = LocalContext.current
                    val app = context.applicationContext as FieldTechApplication
                    val requestsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel(app.requestsRepository) as T
                            }
                        }
                    )
                    val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                            }
                        }
                    )
                    val serviceTasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                            }
                        }
                    )
                    RequestDetailScreen(
                        navController = navController,
                        requestId = requestId,
                        requestsViewModel = requestsViewModel,
                        clientsViewModel = clientsViewModel,
                        tasksViewModel = serviceTasksViewModel
                    )
                }
            }
            
            // Client Detail Screen
            composable(Screen.ClientDetail.route) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId")
                if (clientId != null) {
                    val context = LocalContext.current
                    val app = context.applicationContext as FieldTechApplication
                    val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                            }
                        }
                    )
                    val serviceTasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel(app.tasksRepository) as T
                            }
                        }
                    )
                    ClientDetailScreen(
                        clientId = clientId,
                        navController = navController,
                        viewModel = clientsViewModel,
                        tasksViewModel = serviceTasksViewModel
                    )
                }
            }
            
            // Client Edit/New Screen
            composable(Screen.ClientEdit.route) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId")
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                ClientEditScreen(clientId = clientId, navController = navController, viewModel = clientsViewModel)
            }
            
            composable(Screen.ClientNew.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                ClientEditScreen(clientId = null, navController = navController, viewModel = clientsViewModel)
            }
            
            // Client Picker for Report Flow
            composable(Screen.ClientPicker.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                ClientPickerScreen(navController = navController, clientsViewModel = clientsViewModel, reportViewModel = viewModel)
            }
            
            // Client Import Screen
            composable(Screen.ClientImport.route) {
                val context = LocalContext.current
                val app = context.applicationContext as FieldTechApplication
                val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                        }
                    }
                )
                ClientImportScreen(navController = navController, viewModel = clientsViewModel)
            }
            
            // Report Creation Flow - Scoped to maintain ViewModel across navigation
            navigation(
                startDestination = Screen.ClientInfo.route,
                route = "report_graph"
            ) {
                composable(Screen.ClientInfo.route) {
                    // Get the scoped ViewModel from the parent navigation graph
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    ClientInfoScreen(navController = navController, viewModel = reportViewModel)
                }
                
                // Report flow from job with pre-filled client and job linkage
                composable("report/start?clientId={clientId}&taskId={taskId}") { backStackEntry ->
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    val clientId = backStackEntry.arguments?.getString("clientId")
                    val taskId = backStackEntry.arguments?.getString("taskId")
                    
                    // Set the job ID in the viewModel
                    reportViewModel.setLinkedTaskId(taskId)
                    
                    // Load and set the client
                    if (clientId != null) {
                        val context = LocalContext.current
                        val app = context.applicationContext as FieldTechApplication
                        
                        LaunchedEffect(clientId) {
                            val client = app.clientsRepository.getClientById(clientId)
                            if (client != null) {
                                reportViewModel.setSelectedClient(client.id, client.name)
                            }
                        }
                    }
                    
                    // Navigate to ClientInfoScreen (will show pre-selected client)
                    ClientInfoScreen(navController = navController, viewModel = reportViewModel)
                }
                
                // Client Picker for Report Flow
                composable("report/clientPicker") {
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    val context = LocalContext.current
                    val app = context.applicationContext as FieldTechApplication
                    val clientsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fieldtechv20kc.viewmodel.ClientsViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.example.fieldtechv20kc.viewmodel.ClientsViewModel(app.clientsRepository, context) as T
                            }
                        }
                    )
                    ClientPickerScreen(
                        navController = navController,
                        clientsViewModel = clientsViewModel,
                        reportViewModel = reportViewModel
                    )
                }
                
                composable(Screen.JobType.route) {
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    JobTypeScreen(navController = navController, viewModel = reportViewModel)
                }
                
                composable(Screen.JobDocumentation.route) {
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    JobDocumentationScreen(navController = navController, viewModel = reportViewModel)
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
                    val parentEntry = remember(navController) {
                        navController.getBackStackEntry("report_graph")
                    }
                    val factory = rememberReportViewModelFactory(parentEntry)
                    val reportViewModel: ReportViewModel = viewModel(parentEntry, factory = factory)
                    
                    SignatureScreen(navController = navController, viewModel = reportViewModel)
                }
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
            
            // Settings Screens
            composable(Screen.ClientInfoSettings.route) {
                ClientInfoSettingsScreen(navController = navController)
            }
            
            composable(Screen.JobTypeSettings.route) {
                JobTypeSettingsScreen(navController = navController)
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
}
