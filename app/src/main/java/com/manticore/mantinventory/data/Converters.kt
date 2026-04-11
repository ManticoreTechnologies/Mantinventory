package com.manticore.mantinventory.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.let(formatter::format)

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)
}
