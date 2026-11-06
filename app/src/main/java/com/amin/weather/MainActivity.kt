package com.amin.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amin.weather.ui.WeatherDashboardScreen
import com.amin.weather.ui.WeatherViewModel
import com.amin.weather.ui.theme.DezfulWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val notifManager = com.amin.weather.core.WeatherNotificationManager(this)
        notifManager.schedulePeriodicSync()

        setContent {
            DezfulWeatherTheme {
                val vm: WeatherViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                WeatherDashboardScreen(
                    uiState = uiState,
                    onRefresh = { vm.refresh() },
                    onSelectRange = { vm.selectRange(it) },
                    onToggleColumn = { vm.toggleColumn(it) },
                    onUpdateAlertSettings = { vm.updateAlertSettings(it) },
                    onTriggerAlertNotification = { vm.checkAndTriggerAlertNotification(it) },
                    onClearFeedbackMessage = { vm.clearAlertFeedbackMessage() },
                    onSelectBottomTab = { vm.selectBottomTab(it) },
                    onSubmitSkyObservation = { photo, dir, phen, temp, notes ->
                        vm.submitSkyObservation(photo, dir, phen, temp, notes)
                    },
                    onClearSkyObservation = { vm.clearSkyObservation() }
                )
            }
        }
    }
}
