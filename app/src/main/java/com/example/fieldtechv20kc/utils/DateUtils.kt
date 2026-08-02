package com.example.fieldtechv20kc.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    
    // ========== STANDARDIZED DATE FORMATS ==========
    
    /**
     * Standard format for displaying dates with time
     * Example: "15 Jan 2025, 14:30"
     */
    fun formatDateTime(timestamp: Long): String {
        val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    /**
     * Standard format for displaying dates only (no time)
     * Example: "15 Jan 2025"
     */
    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    /**
     * Standard format for displaying dates with day of week
     * Example: "Mon, 15 Jan 2025"
     */
    fun formatDateWithDay(timestamp: Long): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    /**
     * Compact format for route names and short displays
     * Example: "Mon 15.01.25"
     */
    fun formatCompact(timestamp: Long): String {
        val format = SimpleDateFormat("EEE dd.MM.yy", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    /**
     * Format for file names (no spaces or special chars)
     * Example: "20250115_143045"
     */
    fun formatForFileName(timestamp: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    // ========== EXISTING UTILITY FUNCTIONS ==========
    
    /**
     * Get local midnight (00:00:00.000) in epoch millis for today
     */
    fun getTodayMidnight(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Get local midnight for tomorrow
     */
    fun getTomorrowMidnight(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Get local midnight for the start of this week (Monday)
     */
    fun getThisWeekStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Get local midnight for the end of this week (Sunday + 1 day)
     */
    fun getThisWeekEnd(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Get local midnight for a specific day (dayOffset from today)
     * dayOffset = 0 -> today, 1 -> tomorrow, -1 -> yesterday
     */
    fun getDayMidnight(dayOffset: Int = 0): Long {
        val cal = Calendar.getInstance()
        if (dayOffset != 0) {
            cal.add(Calendar.DAY_OF_MONTH, dayOffset)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Check if a given epoch millis is today
     */
    fun isToday(epochMillis: Long): Boolean {
        val today = getTodayMidnight()
        val tomorrow = getTomorrowMidnight()
        return epochMillis >= today && epochMillis < tomorrow
    }
    
    /**
     * Check if a given epoch millis is tomorrow
     */
    fun isTomorrow(epochMillis: Long): Boolean {
        val tomorrow = getTomorrowMidnight()
        val dayAfter = getDayMidnight(2)
        return epochMillis >= tomorrow && epochMillis < dayAfter
    }
}



