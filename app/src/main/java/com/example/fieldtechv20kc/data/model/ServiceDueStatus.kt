package com.example.fieldtechv20kc.data.model

/**
 * Service-due classification for the Service tab and weekly digest.
 *
 * Defaults (editable in Settings / company config):
 * - Soon: 1 month+
 * - Late: 2 months+
 * - Overdue: 3 months+
 * - Starred clients at 1 month+ jump straight to Overdue
 */
enum class ServiceDueStatus {
    OVERDUE,
    LATE,
    SOON,
    OK
}

data class ServiceDueThresholds(
    val soonMonths: Int = 1,
    val lateMonths: Int = 2,
    val overdueMonths: Int = 3,
    val starredOverdueMonths: Int = 1
) {
    companion object {
        val DEFAULT = ServiceDueThresholds()
    }
}

object ServiceDueRules {
    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val DAYS_PER_MONTH = 30L

    fun monthsSinceLastService(lastServiceDate: Long?, now: Long = System.currentTimeMillis()): Double {
        if (lastServiceDate == null) return Double.POSITIVE_INFINITY
        val days = ((now - lastServiceDate).coerceAtLeast(0L)).toDouble() / MS_PER_DAY
        return days / DAYS_PER_MONTH
    }

    fun classify(
        client: Client,
        thresholds: ServiceDueThresholds = ServiceDueThresholds.DEFAULT,
        now: Long = System.currentTimeMillis()
    ): ServiceDueStatus {
        val months = monthsSinceLastService(client.lastServiceDate, now)
        val overdueFloor = if (client.priorityStarred) {
            minOf(thresholds.starredOverdueMonths, thresholds.overdueMonths)
        } else {
            thresholds.overdueMonths
        }
        return when {
            months >= overdueFloor -> ServiceDueStatus.OVERDUE
            months >= thresholds.lateMonths -> ServiceDueStatus.LATE
            months >= thresholds.soonMonths -> ServiceDueStatus.SOON
            else -> ServiceDueStatus.OK
        }
    }

    fun sortKey(
        client: Client,
        status: ServiceDueStatus
    ): Long {
        // Lower = higher priority in the Service queue
        val tier = when {
            client.priorityStarred && status == ServiceDueStatus.OVERDUE -> 0
            status == ServiceDueStatus.OVERDUE -> 1
            status == ServiceDueStatus.LATE -> 2
            status == ServiceDueStatus.SOON -> 3
            else -> 4
        }
        val age = client.lastServiceDate ?: 0L
        return tier * 1_000_000_000_000L + age
    }
}
