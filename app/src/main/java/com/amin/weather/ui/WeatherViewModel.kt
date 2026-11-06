package com.amin.weather.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amin.weather.core.AlertEvaluationResult
import com.amin.weather.core.AlertSettings
import com.amin.weather.core.ForecastColumn
import com.amin.weather.core.ForecastDashboard
import com.amin.weather.core.ForecastEngine
import com.amin.weather.core.ForecastRange
import com.amin.weather.core.SkyObservationReport
import com.amin.weather.core.WeatherNotificationManager
import com.amin.weather.core.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class WeatherUiState(
    val dashboard: ForecastDashboard? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val errorMessage: String? = null,
    val selectedRange: ForecastRange = ForecastRange.HOURLY,
    val selectedBottomTab: Int = 0, // 0: رادار و توده‌ها, 1: دیدبانی صفی‌آباد, 2: پتانسیل همرفتی
    val visibleColumns: Set<ForecastColumn> = ForecastColumn.entries
        .filter { it.defaultVisible }
        .toSet(),
    val alertSettings: AlertSettings = AlertSettings(),
    val lastAlertEvaluation: AlertEvaluationResult? = null,
    val alertFeedbackMessage: String? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application.applicationContext)
    private val notificationManager = WeatherNotificationManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        WeatherUiState(alertSettings = notificationManager.loadSettings())
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cached = repository.getCachedForecast()
            if (cached != null) {
                _uiState.update { it.copy(dashboard = cached, isLoading = false, isFromCache = true) }
                evaluateAlerts(cached, sendSystemNotification = false)
            }
            refresh(isInitial = cached == null)
        }
    }

    fun refresh(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = isInitial && it.dashboard == null,
                    isRefreshing = !isInitial || it.dashboard != null,
                    errorMessage = null
                )
            }
            val result = repository.refreshForecast()
            result.fold(
                onSuccess = { dashboard ->
                    _uiState.update {
                        it.copy(
                            dashboard = dashboard,
                            isLoading = false,
                            isRefreshing = false,
                            isFromCache = false,
                            errorMessage = null
                        )
                    }
                    evaluateAlerts(dashboard, sendSystemNotification = false)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isFromCache = it.dashboard != null,
                            errorMessage = if (it.dashboard != null) {
                                "خطا در برقراری ارتباط: ${err.localizedMessage ?: "عدم پاسخ سرور"} (نمایش داده‌های کش)"
                            } else {
                                err.localizedMessage ?: "خطا در دریافت اطلاعات هواشناسی. لطفاً اتصال اینترنت خود را بررسی نمایید."
                            }
                        )
                    }
                }
            )
        }
    }

    fun selectRange(range: ForecastRange) {
        _uiState.update { it.copy(selectedRange = range) }
    }

    fun toggleColumn(column: ForecastColumn) {
        _uiState.update { state ->
            val current = state.visibleColumns.toMutableSet()
            if (current.contains(column)) {
                if (current.size > 1) current.remove(column)
            } else {
                current.add(column)
            }
            state.copy(visibleColumns = current)
        }
    }

    fun updateAlertSettings(newSettings: AlertSettings) {
        notificationManager.saveSettings(newSettings)
        _uiState.update { it.copy(alertSettings = newSettings) }
        _uiState.value.dashboard?.let {
            evaluateAlerts(it, sendSystemNotification = false)
        }
    }

    fun checkAndTriggerAlertNotification(hasNotificationPermission: Boolean) {
        val dashboard = _uiState.value.dashboard ?: return
        val settings = _uiState.value.alertSettings

        val result = notificationManager.evaluateDashboard(dashboard, settings)
        _uiState.update { it.copy(lastAlertEvaluation = result) }

        if (result.shouldNotify) {
            if (hasNotificationPermission) {
                val sent = notificationManager.sendNotificationWithCooldown(result, force = true)
                val msg = if (sent) {
                    "نوتیفیکیشن هشدار ارسال شد: ${result.title}"
                } else {
                    "ارسال اعلان با خطا مواجه شد یا دسترسی مسدود است."
                }
                _uiState.update { it.copy(alertFeedbackMessage = msg) }
            } else {
                _uiState.update {
                    it.copy(alertFeedbackMessage = "دسترسی نوتیفیکیشن در تنظیمات غیرفعال است.")
                }
            }
        } else {
            _uiState.update {
                it.copy(alertFeedbackMessage = "در شرایط فعلی، هیچ هشدار فعالی شناسایی نشد (آسمان آرام).")
            }
        }
    }

    fun clearAlertFeedbackMessage() {
        _uiState.update { it.copy(alertFeedbackMessage = null) }
    }

    fun selectBottomTab(index: Int) {
        _uiState.update { it.copy(selectedBottomTab = index.coerceIn(0, 2)) }
    }

    fun submitSkyObservation(
        bitmap: Bitmap?,
        direction: String,
        phenomenon: String,
        temp: Double?,
        notes: String
    ) {
        viewModelScope.launch {
            val photoPath = bitmap?.let { repository.saveSkyObservationPhoto(it) }

            val nowUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            val iranTime = ForecastEngine.utcToIran(nowUtc)

            val report = SkyObservationReport(
                id = UUID.randomUUID().toString().take(8),
                photoPath = photoPath,
                direction = direction,
                phenomenon = phenomenon,
                userTemp = temp,
                notes = notes,
                timestampIran = iranTime,
                createdAtEpochMs = System.currentTimeMillis(),
                isCalibrated = true
            )
            repository.saveSkyObservation(report)
            refresh(isInitial = false)
        }
    }

    fun clearSkyObservation() {
        viewModelScope.launch {
            repository.clearSkyObservation()
            refresh(isInitial = false)
        }
    }

    private fun evaluateAlerts(dashboard: ForecastDashboard, sendSystemNotification: Boolean) {
        val settings = _uiState.value.alertSettings
        val result = notificationManager.evaluateDashboard(dashboard, settings)
        _uiState.update { it.copy(lastAlertEvaluation = result) }
        if (sendSystemNotification && result.shouldNotify && settings.isEnabled) {
            notificationManager.sendNotificationWithCooldown(result, force = false)
        }
    }
}
