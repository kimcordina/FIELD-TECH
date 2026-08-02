package com.example.fieldtechv20kc.navigation

sealed class Screen(val route: String) {
    // Bottom Navigation Screens
    object SavedReports : Screen("saved_reports")
    object NewReport : Screen("new_report")
    object Clients : Screen("clients")
    object Tasks : Screen("tasks")
    object Requests : Screen("requests")
    object Settings : Screen("settings")
    
    // Report Creation Flow Screens
    object ClientInfo : Screen("client_info")
    object ClientPicker : Screen("client_picker")
    object JobType : Screen("job_type")
    object JobDocumentation : Screen("job_documentation")
    object LegalText : Screen("legal_text")
    object Signature : Screen("signature")
    object SignaturePad : Screen("signature_pad")
    object Camera : Screen("camera")
    object ReportDetail : Screen("report_detail/{reportId}") {
        fun createRoute(reportId: Long) = "report_detail/$reportId"
    }
    
    // Client Management Screens
    object ClientsList : Screen("clients_list")
    object ClientDetail : Screen("client_detail/{clientId}") {
        fun createRoute(clientId: String) = "client_detail/$clientId"
    }
    object ClientEdit : Screen("client_edit?clientId={clientId}") {
        fun createRoute(clientId: String?) = if (clientId != null) {
            "client_edit?clientId=$clientId"
        } else {
            "client_edit"
        }
    }
    object ClientNew : Screen("client_new")
    object ClientImport : Screen("client_import")
    
    // Service Tasks Screens
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    
    // Route Screens
    object SavedRoutes : Screen("saved_routes")
    
    object RoutePlanner : Screen("route_planner/{jobIds}/{createdBy}/{intendedAssignee}") {
        fun createRoute(jobIds: String, createdBy: String, intendedAssignee: String?) = 
            "route_planner/$jobIds/$createdBy/${intendedAssignee ?: "none"}"
    }
    
    object RouteDetail : Screen("route_detail/{routeId}") {
        fun createRoute(routeId: String) = "route_detail/$routeId"
    }
    
    // Service Requests Screens
    object RequestsList : Screen("requests")
    object RequestCreate : Screen("requests/new")
    object RequestDetail : Screen("requests/{requestId}") {
        fun createRoute(requestId: String) = "requests/$requestId"
    }
    
    // Statistics (moved from bottom nav to Reports)
    object Statistics : Screen("reports/statistics")

    // Settings Screens
    object ClientInfoSettings : Screen("client_info_settings")
    object JobTypeSettings : Screen("job_type_settings")
    object JobDocumentationSettings : Screen("job_documentation_settings")
    
    // Diagnostics Screens
    object ErrorTray : Screen("diagnostics/error_tray")
    
    // Legacy - keeping for backward compatibility
    object Home : Screen("home")
}
