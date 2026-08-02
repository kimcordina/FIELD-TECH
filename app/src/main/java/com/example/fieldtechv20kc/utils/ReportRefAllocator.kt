package com.example.fieldtechv20kc.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allocates unique human-facing report references:
 *   NC-0132-26
 *   │  │    └─ two-digit year
 *   │  └────── zero-padded sequence (starts at 132)
 *   └───────── company prefix
 *
 * Sequence is persisted in SharedPreferences and never reused.
 * Gaps are acceptable if a save fails after allocation.
 */
object ReportRefAllocator {

    private const val PREFS = "report_ref_allocator"
    private const val KEY_NEXT_SEQ = "next_seq"
    /** First number issued for new reports. */
    private const val START_SEQ = 132

    private val lock = Any()
    private val initialized = AtomicBoolean(false)

    /**
     * Returns the next unique ref for [at] (defaults to now) and advances the counter.
     */
    fun allocate(context: Context, at: Date = Date()): String {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!initialized.get()) {
                // Ensure default start value is seeded once on first install
                if (!prefs.contains(KEY_NEXT_SEQ)) {
                    prefs.edit().putInt(KEY_NEXT_SEQ, START_SEQ).apply()
                }
                initialized.set(true)
            }
            val seq = prefs.getInt(KEY_NEXT_SEQ, START_SEQ)
            val year = SimpleDateFormat("yy", Locale.US).format(at)
            val ref = "NC-%04d-%s".format(Locale.US, seq, year)
            prefs.edit().putInt(KEY_NEXT_SEQ, seq + 1).apply()
            return ref
        }
    }

    /** Peek at what the next ref would look like without consuming it (debug/UI). */
    fun peekNext(context: Context, at: Date = Date()): String {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seq = prefs.getInt(KEY_NEXT_SEQ, START_SEQ)
            val year = SimpleDateFormat("yy", Locale.US).format(at)
            return "NC-%04d-%s".format(Locale.US, seq, year)
        }
    }
}
