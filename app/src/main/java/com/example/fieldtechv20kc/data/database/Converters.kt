package com.example.fieldtechv20kc.data.database

import androidx.room.TypeConverter
import com.example.fieldtechv20kc.data.model.JobType
import java.util.Date

class Converters {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromJobType(jobType: JobType): String {
        return jobType.name
    }
    
    @TypeConverter
    fun toJobType(jobType: String): JobType {
        return JobType.valueOf(jobType)
    }
}

