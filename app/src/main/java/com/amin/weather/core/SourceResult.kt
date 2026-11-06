package com.amin.weather.core

enum class SourceStatus { OK, PARTIAL, FAILED, UNAVAILABLE }

data class SourceResult<T>(
    val source: String,          // مثلاً "open-meteo/ecmwf_ifs"
    val status: SourceStatus,
    val data: T?,
    val message: String? = null, // دلیل خطا یا مدل جایگزین استفاده‌شده
    val fetchedAtEpochSec: Long = System.currentTimeMillis() / 1000
)
