package com.example.fieldtechv20kc.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object EmailHelper {
    
    /**
     * Email a report PDF to the specified recipient using Gmail
     * @param context Android context
     * @param pdfPath Local path to the PDF file
     * @param recipientEmail Email address to send to
     * @param clientName Name of the client for the email subject
     * @return true if email intent was launched successfully
     */
    fun emailReport(
        context: Context,
        pdfPath: String,
        recipientEmail: String,
        clientName: String
    ): Boolean {
        try {
            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                android.util.Log.e("EmailHelper", "PDF file does not exist: $pdfPath")
                return false
            }
            
            // Create content URI using FileProvider
            val pdfUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            
            // Create email intent
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, "Service Report - $clientName")
                putExtra(Intent.EXTRA_TEXT, "Please find attached the service report for $clientName.")
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Try to use Gmail specifically
                setPackage("com.google.android.gm")
            }
            
            // Check if Gmail is installed
            val packageManager = context.packageManager
            if (emailIntent.resolveActivity(packageManager) != null) {
                context.startActivity(emailIntent)
                android.util.Log.d("EmailHelper", "Gmail intent launched successfully")
                return true
            } else {
                // Gmail not installed, try generic email app
                emailIntent.setPackage(null)
                context.startActivity(Intent.createChooser(emailIntent, "Send Report via Email"))
                android.util.Log.d("EmailHelper", "Generic email intent launched")
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("EmailHelper", "Error emailing report: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Check if the email recipient is configured
     */
    fun isEmailConfigured(settings: com.example.fieldtechv20kc.data.model.ReportSettings): Boolean {
        return settings.autoEmailReportsEnabled && 
               settings.reportEmailRecipient.isNotBlank() &&
               android.util.Patterns.EMAIL_ADDRESS.matcher(settings.reportEmailRecipient).matches()
    }
}










