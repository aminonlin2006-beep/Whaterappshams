package com.amin.weather.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.amin.weather.core.*
import com.amin.weather.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

fun computeFeelsLike(tempC: Double?, humidity: Double?, windSpeedKmh: Double?): Double? {
    if (tempC == null || humidity == null || windSpeedKmh == null) return null
    val t = tempC
    val rh = humidity.coerceIn(5.0, 100.0)
    val wsMs = (windSpeedKmh / 3.6).coerceAtLeast(0.0)
    val e = (rh / 100.0) * 6.105 * Math.exp((17.27 * t) / (237.7 + t))
    val at = t + 0.33 * e - 0.70 * wsMs - 4.00
    return Math.round(at * 10.0) / 10.0
}

@Composable
fun EducationalDisclosureBox(
    sourceName: String,
    reasoningText: String,
    accentColor: Color = PrimarySky,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .border(0.8.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "درباره رادار جوی داپلر دزفول و نحوه خوانش داده‌ها",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "بستن آموزش" else "مشاهده آموزش",
                tint = TextOnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(
                    color = OutlineVariant.copy(alpha = 0.4f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "داده‌های این سامانه از تحلیل تلفیقی تصاویر رادار داپلر باند C شبکه هواشناسی و پردازش زنده توده‌ها با تفکیک‌پذیری ۵ دقیقه‌ای استخراج گردیده است. رنگ‌های زرد تا زرشکی نشانگر تشدید نرخ بارش قطرات درشت، ناپایداری‌های همرفتی و خطر تگرگ یا تندباد در سطح زمین می‌باشند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextOnSurfaceVariant,
                    lineHeight = 20.sp
                )
                if (sourceName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "📡 منبع اولیه: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimarySky
                        )
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (reasoningText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "💡 تفسیر وضعیت: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryAmber
                        )
                        Text(
                            text = reasoningText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDashboardScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onSelectRange: (ForecastRange) -> Unit,
    onToggleColumn: (ForecastColumn) -> Unit,
    onUpdateAlertSettings: (AlertSettings) -> Unit,
    onTriggerAlertNotification: (Boolean) -> Unit,
    onClearFeedbackMessage: () -> Unit,
    onSelectBottomTab: (Int) -> Unit = {},
    onSubmitSkyObservation: (Bitmap?, String, String, Double?, String) -> Unit = { _, _, _, _, _ -> },
    onClearSkyObservation: () -> Unit = {}
) {
    val context = LocalContext.current
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showSkyObservationSheet by remember { mutableStateOf(false) }
    var showRegionInfoSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onTriggerAlertNotification(isGranted)
    }

    // اجازهٔ اعلان در اولین اجرا و هنگام فعال‌سازی هشدارها
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }
    LaunchedEffect(uiState.alertSettings.isEnabled) {
        if (uiState.alertSettings.isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startupPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val handleTriggerAlert: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                onTriggerAlertNotification(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onTriggerAlertNotification(true)
        }
    }

    // بازگشت با دکمه سخت‌افزاری از زبانه‌های دیگر به زبانه اصلی (امروز)
    BackHandler(enabled = uiState.selectedBottomTab != 0) {
        onSelectBottomTab(0)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = SurfaceBg,
            topBar = {
                AppTopHeader(
                    isRefreshing = uiState.isRefreshing,
                    hasActiveUserObservation = uiState.dashboard?.userObservation != null,
                    isAlertsEnabled = uiState.alertSettings.isEnabled,
                    onRefresh = onRefresh,
                    onOpenRegionInfo = { showRegionInfoSheet = true },
                    onOpenObservation = { showSkyObservationSheet = true },
                    onOpenAlertSettings = { showNotificationSheet = true },
                    onShare = {
                        val d = uiState.dashboard
                        if (d != null) {
                            val curTemp = d.synopObs?.tempC ?: d.modelCurrent?.tempC
                            val shareText = buildString {
                                append("🌤️ وضعیت هوای شعاع ۲۰km شمس‌آباد دزفول\n")
                                append("⏱️ زمان به‌روزرسانی: ${JalaliCalendar.formatLong(d.generatedIran)}\n\n")
                                if (curTemp != null) {
                                    append("🌡️ دما: ${JalaliCalendar.formatNumber(curTemp)}°C\n")
                                }
                                append("⚠️ وضعیت: ${d.verdict.text}\n")
                                if (d.verdict.advisoryNote.isNotBlank()) {
                                    append("💡 توصیه: ${d.verdict.advisoryNote}\n")
                                }
                                if (d.radarNowcast.statusText.isNotBlank()) {
                                    append("📡 رادار: ${d.radarNowcast.statusText}\n")
                                }
                                append("\n📲 هواشناسی هوشمند امین")
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val chooser = Intent.createChooser(sendIntent, "اشتراک‌گذاری وضعیت هوا")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        }
                    }
                )
            },
            bottomBar = {
                AppBottomNavigationBar(
                    selectedTab = uiState.selectedBottomTab,
                    onSelectTab = onSelectBottomTab
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isLoading && uiState.dashboard == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimarySky)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "در حال تحلیل پایش ۲۰km شمس‌آباد و دیدبانی صفی‌آباد...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextOnSurfaceVariant
                            )
                        }
                    }

                    uiState.dashboard == null && uiState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "خطا",
                                tint = ErrorRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("retry_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimarySky, contentColor = Color(0xFF00354A))
                            ) {
                                Text("تلاش مجدد")
                            }
                        }
                    }

                    uiState.dashboard != null -> {
                        when (uiState.selectedBottomTab) {
                            0 -> TodayTabContent(
                                dashboard = uiState.dashboard,
                                isFromCache = uiState.isFromCache,
                                errorMessage = uiState.errorMessage,
                                onOpenRegionInfo = { showRegionInfoSheet = true },
                                onClearSkyObservation = onClearSkyObservation,
                                onSelectBottomTab = onSelectBottomTab
                            )
                            1 -> ForecastTabContent(
                                dashboard = uiState.dashboard,
                                selectedRange = uiState.selectedRange,
                                onSelectRange = onSelectRange
                            )
                            2 -> RadarDedicatedTabContent(
                                dashboard = uiState.dashboard,
                                onOpenRegionInfo = { showRegionInfoSheet = true }
                            )
                            3 -> StationsTabContent(
                                dashboard = uiState.dashboard,
                                isAlertsEnabled = uiState.alertSettings.isEnabled,
                                onOpenSkyObservation = { showSkyObservationSheet = true },
                                onOpenAlertSettings = { showNotificationSheet = true }
                            )
                            else -> TodayTabContent(
                                dashboard = uiState.dashboard,
                                isFromCache = uiState.isFromCache,
                                errorMessage = uiState.errorMessage,
                                onOpenRegionInfo = { showRegionInfoSheet = true },
                                onClearSkyObservation = onClearSkyObservation,
                                onSelectBottomTab = onSelectBottomTab
                            )
                        }
                    }
                }
            }

            if (showRegionInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showRegionInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SurfaceContainer
                ) {
                    RegionCoverageBottomSheetContent(onDismiss = { showRegionInfoSheet = false })
                }
            }

            if (showSkyObservationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSkyObservationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SurfaceContainer
                ) {
                    SkyObservationBottomSheetContent(
                        existingReport = uiState.dashboard?.userObservation,
                        onSubmit = { photo, dir, phen, temp, notes ->
                            onSubmitSkyObservation(photo, dir, phen, temp, notes)
                            showSkyObservationSheet = false
                        },
                        onClear = {
                            onClearSkyObservation()
                            showSkyObservationSheet = false
                        },
                        onDismiss = { showSkyObservationSheet = false }
                    )
                }
            }

            if (showNotificationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showNotificationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SurfaceContainer
                ) {
                    WeatherAlertBottomSheetContent(
                        settings = uiState.alertSettings,
                        feedbackMessage = uiState.alertFeedbackMessage,
                        onUpdateSettings = onUpdateAlertSettings,
                        onTriggerTest = handleTriggerAlert,
                        onClearFeedback = onClearFeedbackMessage,
                        onDismiss = { showNotificationSheet = false }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Top Header (Matching HTML exactly)
// -----------------------------------------------------------------------------
@Composable
private fun AppTopHeader(
    isRefreshing: Boolean,
    hasActiveUserObservation: Boolean,
    isAlertsEnabled: Boolean,
    onRefresh: () -> Unit,
    onOpenRegionInfo: () -> Unit,
    onOpenObservation: () -> Unit,
    onOpenAlertSettings: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        color = SurfaceBg.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Start: Sensors Squircle + Titles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenRegionInfo)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "آیکون رادار",
                        tint = PrimarySky,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "شمس‌آباد، دزفول",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "انتخاب محدوده",
                            tint = PrimarySky,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "شعاع عملیاتی ۲۰ کیلومتر • پایش آنلاین",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextOnSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            // End: Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Refresh
                if (isRefreshing) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimarySky
                        )
                    }
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "به‌روزرسانی داده‌ها",
                            tint = TextOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Region info / Search
                IconButton(
                    onClick = onOpenRegionInfo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("region_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجوی موقعیت و محدوده",
                        tint = TextOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // User observation / profile button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (hasActiveUserObservation) SecondaryAmber else PrimarySky)
                        .clickable(onClick = onOpenObservation)
                        .testTag("sky_camera_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasActiveUserObservation) Icons.Default.CameraAlt else Icons.Default.Person,
                        contentDescription = "دیده‌بان آسمان و گزارش کاربر",
                        tint = Color(0xFF00354A),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Notification alerts
                IconButton(
                    onClick = onOpenAlertSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("open_alerts_sheet_button")
                ) {
                    Icon(
                        imageVector = if (isAlertsEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = "تنظیمات اعلان‌ها",
                        tint = if (isAlertsEnabled) SecondaryAmber else TextOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("share_weather_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "اشتراک‌گذاری",
                        tint = TextOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bottom Navigation Bar (Matching HTML exactly: 4 tabs)
// -----------------------------------------------------------------------------
@Composable
private fun AppBottomNavigationBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceContainerLow.copy(alpha = 0.95f),
        contentColor = PrimarySky,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        val tabs = listOf(
            Triple(0, "امروز", Icons.Default.Air),
            Triple(1, "پیش‌بینی", Icons.Default.CalendarMonth),
            Triple(2, "رادار جوی", Icons.Default.Radar),
            Triple(3, "ایستگاه‌ها", Icons.Default.LocationCity)
        )

        tabs.forEach { (index, title, icon) ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF00354A),
                    selectedTextColor = PrimarySky,
                    indicatorColor = PrimarySky,
                    unselectedIconColor = TextOnSurfaceVariant,
                    unselectedTextColor = TextOnSurfaceVariant
                )
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: "امروز" (Today Screen)
// -----------------------------------------------------------------------------
@Composable
private fun TodayTabContent(
    dashboard: ForecastDashboard,
    isFromCache: Boolean,
    errorMessage: String?,
    onOpenRegionInfo: () -> Unit,
    onClearSkyObservation: () -> Unit,
    onSelectBottomTab: (Int) -> Unit
) {
    val hourlyRows = dashboard.ranges[ForecastRange.HOURLY].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isFromCache) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, SecondaryAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "آفلاین",
                            tint = SecondaryAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📦 داده‌های ذخیره‌شده کش (${JalaliCalendar.formatLong(dashboard.generatedIran)}). برای استعلام جدید دکمه 🔄 را بزنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Region Location Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRegionInfo),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                border = BorderStroke(1.dp, PrimarySky.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = PrimarySky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "محدوده پایش: شمس‌آباد دزفول (شعاع ۲۰km)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimarySky
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "جزئیات منطقه",
                                    tint = PrimarySky.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "شامل شمس‌آباد، صفی‌آباد، دزفول، اندیمشک و دشت مزارع",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = JalaliCalendar.formatShort(dashboard.generatedIran),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextOnSurfaceVariant
                    )
                }
            }
        }

        // Hero Alert & Current Status Card
        item {
            HeroAlertTierCard(
                verdict = dashboard.verdict,
                synop = dashboard.synopObs,
                modelCurrent = dashboard.modelCurrent,
                userObservation = dashboard.userObservation,
                onClearObservation = onClearSkyObservation,
                onOpenRegionInfo = onOpenRegionInfo
            )
        }

        // Quick Radar Alert Teaser
        if (dashboard.radarNowcast.available && dashboard.radarNowcast.maxDbzInTarget >= Tuning.DBZ_LIGHT) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBottomTab(2) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    border = BorderStroke(1.dp, SecondaryAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.Radar, contentDescription = null, tint = SecondaryAmber, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "رادار زنده: ${dashboard.radarNowcast.maxDbzInTarget} dBZ در محدوده",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryAmber
                                )
                                Text(
                                    text = dashboard.radarNowcast.statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text("مشاهده رادار 📡", style = MaterialTheme.typography.labelSmall, color = PrimarySky, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Hourly Preview
        item {
            if (hourlyRows.isNotEmpty()) {
                HourlyTimelineSection(
                    rows = hourlyRows,
                    selectedRange = ForecastRange.HOURLY,
                    onSelectRange = {}
                )
            }
        }

        // 24-Hour Pattern Highlights
        item {
            PatternConvectiveSummaryCard(pattern = dashboard.pattern)
        }

        // Footer Attribution
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "داده‌های هواشناسی: Open-Meteo.com · رادار: RainViewer · دیدبانی: Ogimet/WMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: "پیش‌بینی" (Forecast Screen)
// -----------------------------------------------------------------------------
@Composable
private fun ForecastTabContent(
    dashboard: ForecastDashboard,
    selectedRange: ForecastRange,
    onSelectRange: (ForecastRange) -> Unit
) {
    val rangesMap = dashboard.ranges
    val activeRows = rangesMap[selectedRange] ?: rangesMap[ForecastRange.HOURLY].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Range Filter Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "بازه زمانی پیش‌بینی:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextOnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ranges = listOf(
                            ForecastRange.HOURLY to "ساعتی (۴۸h)",
                            ForecastRange.H1 to "۱ ساعته",
                            ForecastRange.H3 to "۳ ساعته",
                            ForecastRange.H6 to "۶ ساعته",
                            ForecastRange.H12 to "۱۲ ساعته",
                            ForecastRange.H24 to "۲۴ ساعته"
                        )
                        items(ranges) { (r, label) ->
                            val isSel = selectedRange == r
                            FilterChip(
                                selected = isSel,
                                onClick = { onSelectRange(r) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimarySky,
                                    selectedLabelColor = Color(0xFF00354A),
                                    containerColor = SurfaceContainerLow,
                                    labelColor = TextOnSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // Active Rows Section
        if (activeRows.isEmpty()) {
            item {
                Text(
                    text = "پیش‌بینی ساعتی در دسترس نیست (مدل‌های پیش‌بینی نرسیدند). وضعیت ساعات آینده نامشخص است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            item {
                HourlyTimelineSection(
                    rows = activeRows,
                    selectedRange = selectedRange,
                    onSelectRange = onSelectRange
                )
            }
        }

        // Neighbourhood 17-points grid summary
        item {
            NeighbourhoodSummaryCard(neighbourhood = dashboard.neighbourhood)
        }

        // Convective analysis
        item {
            PatternConvectiveSummaryCard(pattern = dashboard.pattern)
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: "رادار جوی" (Dedicated Radar Screen - Matching HTML exactly!)
// -----------------------------------------------------------------------------
@Composable
private fun RadarDedicatedTabContent(
    dashboard: ForecastDashboard,
    onOpenRegionInfo: () -> Unit
) {
    val radar = dashboard.radarNowcast
    var selectedLayer by rememberSaveable { mutableStateOf("precip") }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable { mutableIntStateOf(1) } // 1x or 2x
    var currentFrameIndex by rememberSaveable { mutableIntStateOf(3) } // 0..5 (3 is live now)

    val frames = listOf(
        Pair("امروز، ۱۲:۴۵", "۲ ساعت پیش"),
        Pair("امروز، ۱۳:۴۵", "۱ ساعت پیش"),
        Pair("امروز، ۱۴:۱۵", "۳۰- دقیقه"),
        Pair("امروز، ۱۴:۴۵", "اکنون (زنده)"),
        Pair("امروز، ۱۵:۱۵", "۳۰+ دقیقه"),
        Pair("امروز، ۱۵:۴۵", "۱+ ساعت آینده")
    )

    // Animated playback loop
    LaunchedEffect(isPlaying, playbackSpeed) {
        if (isPlaying) {
            val interval = if (playbackSpeed == 1) 1200L else 600L
            while (isPlaying) {
                delay(interval)
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sub-bar: Station telemetry & view tools
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(SurfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing green dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TertiaryEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "شمس‌آباد، دزفول و حومه",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "۳۲.۳۱° N , ۴۸.۴۳° E",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimarySky,
                        fontSize = 10.sp
                    )
                }

                // Quick tool buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable(onClick = onOpenRegionInfo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = "حالت نقشه", tint = PrimarySky, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { /* Toggle layers */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Layers, contentDescription = "تنظیمات لایه‌ها", tint = TextOnSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 2. Quick Layer Selector Ribbon
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val layers = listOf(
                    Triple("precip", "بارش و ابر", Icons.Default.WaterDrop),
                    Triple("wind", "جبهه باد", Icons.Default.Air),
                    Triple("temp", "دما و حرارت", Icons.Default.Thermostat),
                    Triple("storm", "رعد و برق", Icons.Default.Bolt)
                )

                layers.forEach { (layerKey, label, icon) ->
                    val isSel = selectedLayer == layerKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isSel) PrimarySky else SurfaceContainer)
                            .clickable { selectedLayer = layerKey }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSel) Color(0xFF00354A) else TextOnSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color(0xFF00354A) else TextOnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Interactive Map Telemetry Viewport (4:5 Aspect Ratio Canvas)
        item {
            RichDopplerRadarViewport(
                dashboard = dashboard,
                radar = radar,
                selectedLayer = selectedLayer,
                currentFrame = currentFrameIndex
            )
        }

        // 4. Real-Time Playback & Timeline Scrubber Dock
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Top controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PrimarySky)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "توقف" else "پخش",
                                    tint = Color(0xFF00354A),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = frames[currentFrameIndex].first,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (currentFrameIndex == 3) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(TertiaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "زنده",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF003825),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "وضوح زمانی: ۵ یا ۱۰ دقیقه‌ای",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Speed toggle
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(SurfaceContainer)
                                    .clickable { playbackSpeed = if (playbackSpeed == 1) 2 else 1 }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "سرعت: ${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimarySky
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrubber Slider
                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { currentFrameIndex = it.toInt().coerceIn(0, frames.size - 1) },
                        valueRange = 0f..(frames.size - 1).toFloat(),
                        steps = frames.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimarySky,
                            activeTrackColor = PrimaryContainer,
                            inactiveTrackColor = SurfaceContainerHighest
                        )
                    )

                    // Scrubber Ticks Labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        frames.forEachIndexed { idx, f ->
                            Text(
                                text = f.second,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx == currentFrameIndex) PrimarySky else TextOnSurfaceVariant,
                                fontWeight = if (idx == currentFrameIndex) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // 5. Nowcasting Intelligence & Atmospheric Alert Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = SecondaryAmber, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "سامانه هوشمند پایش لحظه‌ای (Nowcasting)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "تحلیل خودکار همرفت جوی در حومه شمس‌آباد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        val verdictTier = dashboard.verdict.tier
                        val badgeColor = when (verdictTier) {
                            AlertTier.RED -> ErrorRed
                            AlertTier.ORANGE -> Color(0xFFF97316)
                            AlertTier.YELLOW -> SecondaryAmber
                            AlertTier.GREEN -> TertiaryEmerald
                            AlertTier.UNKNOWN -> TextOnSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = verdictTier.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Quick Metrics Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val arr = radar.firstArrival
                        // Metric 1: Cell Location
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("موقعیت توده", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                                Text(
                                    text = if (arr != null) "${arr.distKm.toInt()} km" else if (radar.maxDbzInTarget >= Tuning.DBZ_LIGHT) "داخل دایره" else "بدون توده",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = arr?.directionFa ?: "حومه شمس‌آباد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimarySky,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Metric 2: Precipitation Probability
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("احتمال رگبار", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                                val probVal = if (radar.wetFractionInTarget > 0.05) "${(radar.wetFractionInTarget * 100).toInt()}٪" else if (radar.maxDbzInTarget >= Tuning.DBZ_RAIN) "۷۰٪" else "۱۰٪"
                                Text(
                                    text = probVal,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TertiaryEmerald
                                )
                                Text(
                                    text = if (arr?.etaMinLow != null) "در ${arr.etaMinLow} دقیقه" else "ساعات آینده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Metric 3: Front Velocity
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("سرعت جبهه", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                                val speedStr = arr?.speedKmh?.toInt()?.let { "$it km/h" } ?: "۲۵ km/h"
                                Text(
                                    text = speedStr,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = arr?.headingFa ?: "به سمت جنوب‌شرق",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryAmber,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Insight Advisory
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (dashboard.verdict.advisoryNote.isNotBlank()) dashboard.verdict.advisoryNote else dashboard.pattern.precipSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextOnSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 6. Educational Scientific Disclosure Accordion
        item {
            EducationalDisclosureBox(
                sourceName = "رادار باند C کشوری + RainViewer + مدل‌های ECMWF و ICON",
                reasoningText = dashboard.verdict.text
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Interactive Doppler Map Canvas (Matching HTML 4:5 Aspect Ratio with Zoom & Station Markers)
// -----------------------------------------------------------------------------
@Composable
private fun RichDopplerRadarViewport(
    dashboard: ForecastDashboard,
    radar: RadarNowcastResult,
    selectedLayer: String,
    currentFrame: Int
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var mapType by rememberSaveable { mutableStateOf("dark") } // "dark" or "satellite"
    var layerMode by rememberSaveable { mutableStateOf("both") } // "radar", "cloud", "both"
    var selectedMarker by remember { mutableStateOf<StationMapMarker?>(null) }

    val context = LocalContext.current
    val hourlyRows = dashboard.ranges[ForecastRange.HOURLY].orEmpty()
    val cloudCoverAvg = hourlyRows.firstOrNull()?.cloud?.toInt() ?: 45

    // Generate station & neighbourhood markers
    val stationMarkers = remember(dashboard) {
        val markers = mutableListOf<StationMapMarker>()

        // 1. Center: Shamsabad
        val curTemp = dashboard.synopObs?.tempC ?: dashboard.modelCurrent?.tempC
        markers.add(
            StationMapMarker(
                id = "center",
                nameFa = "مرکز شمس‌آباد",
                lat = RegionConfig.CENTER.lat,
                lon = RegionConfig.CENTER.lon,
                isCenter = true,
                tempC = curTemp,
                rh = dashboard.synopObs?.rh ?: dashboard.modelCurrent?.rh,
                cloudCover = hourlyRows.firstOrNull()?.cloud,
                dbz = radar.maxDbzInTarget,
                skyConditionFa = if (radar.maxDbzInTarget >= Tuning.DBZ_RAIN) "بارش سبک تا متوسط در مرکز" else "پایش شعاع ۲۰km"
            )
        )

        // 2. Safiabad SYNOP Station (WMO 40794)
        if (dashboard.synopObs != null) {
            val s = dashboard.synopObs
            val estCloud = if (s.isRainNow) 90.0 else if ((s.rh ?: 0.0) > 70.0) 75.0 else 40.0
            markers.add(
                StationMapMarker(
                    id = "synop_40794",
                    nameFa = "ایستگاه سینوپتیک صفی‌آباد (WMO 40794)",
                    lat = 32.2667,
                    lon = 48.4167,
                    isCenter = false,
                    isSynopStation = true,
                    tempC = s.tempC,
                    rh = s.rh,
                    cloudCover = estCloud,
                    dbz = radar.maxDbzInTarget,
                    skyConditionFa = if (s.isRainNow) "بارش زنده ثبت شد 🌧️" else if (s.isThunder) "رعد و برق ⚡" else "دیده‌بانی زمینی فعال ✅"
                )
            )
        }

        // 3. Towns
        markers.add(StationMapMarker(id = "dezful", nameFa = "شهر دزفول", lat = 32.3811, lon = 48.4058, tempC = curTemp, cloudCover = 50.0, skyConditionFa = "حومه شمالی دایره پایش"))
        markers.add(StationMapMarker(id = "andimeshk", nameFa = "شهر اندیمشک", lat = 32.4601, lon = 48.3582, tempC = curTemp, cloudCover = 55.0, skyConditionFa = "مرز شمال‌غرب دایره پایش"))
        markers.add(StationMapMarker(id = "shush", nameFa = "شهر شوش", lat = 32.1942, lon = 48.2436, tempC = curTemp, cloudCover = 40.0, skyConditionFa = "خارج دایره پایش (جنوب‌غرب)"))
        markers.add(StationMapMarker(id = "shushtar", nameFa = "شهر شوشتر", lat = 32.0456, lon = 48.8567, tempC = curTemp, cloudCover = 45.0, skyConditionFa = "خارج دایره پایش (جنوب‌شرق)"))

        // 4. 16 Ring Neighbourhood Points
        val ringNames = mapOf(
            "r10_N" to "مزارع شمال شمس‌آباد (۱۰km)",
            "r10_NE" to "مزارع شمال‌شرق (۱۰km)",
            "r10_E" to "مزارع شرق شمس‌آباد (۱۰km)",
            "r10_SE" to "مزارع جنوب‌شرق (۱۰km)",
            "r10_S" to "مزارع جنوب صفی‌آباد (۱۰km)",
            "r10_SW" to "مزارع جنوب‌غرب (۱۰km)",
            "r10_W" to "مزارع غرب شمس‌آباد (۱۰km)",
            "r10_NW" to "مزارع شمال‌غرب (۱۰km)",
            "r20_N" to "حومه دزفول (۲۰km)",
            "r20_NE" to "پایکوه زاگرس (۲۰km)",
            "r20_E" to "مزارع شرق دز (۲۰km)",
            "r20_SE" to "راه شوشتر (۲۰km)",
            "r20_S" to "دشت جنوب صفی‌آباد (۲۰km)",
            "r20_SW" to "دشت شوش (۲۰km)",
            "r20_W" to "غرب شمس‌آباد (۲۰km)",
            "r20_NW" to "حومه اندیمشک (۲۰km)"
        )
        for (pt in RegionConfig.NEIGHBOURHOOD) {
            if (pt.id == "shamsabad") continue
            val name = ringNames[pt.id] ?: pt.nameFa
            markers.add(
                StationMapMarker(
                    id = pt.id,
                    nameFa = name,
                    lat = pt.lat,
                    lon = pt.lon,
                    tempC = curTemp,
                    cloudCover = hourlyRows.firstOrNull()?.cloud,
                    dbz = radar.maxDbzInTarget,
                    skyConditionFa = "نقطه محاسباتی شبکه پایش"
                )
            )
        }
        markers
    }

    // Viewport Analysis when zoomed or zoomed out
    val viewportAnalysis = remember(radar, zoomScale, cloudCoverAvg) {
        RadarAnalyzer.computeViewportAnalysis(radar, zoomScale, cloudCoverAvg)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mapType == "satellite") Color(0xFF1B2418) else SurfaceContainerLowest)
                .border(1.dp, if (mapType == "satellite") Color(0xFF3F5438) else OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.0f)
                        panOffset += pan
                    }
                }
                .pointerInput(stationMarkers, zoomScale, panOffset) {
                    detectTapGestures { tapPos ->
                        val sizeW = size.width.toFloat()
                        val sizeH = size.height.toFloat()
                        val cx = sizeW / 2f
                        val cy = sizeH / 2f
                        val maxRadiusPx = minOf(cx, cy) - 20f
                        val baseScale = maxRadiusPx / 55f

                        val hit = stationMarkers.minByOrNull { m ->
                            val (xKm, yKm) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, m.lat, m.lon)
                            val mPx = cx + (xKm.toFloat() * baseScale * zoomScale) + panOffset.x
                            val mPy = cy - (yKm.toFloat() * baseScale * zoomScale) + panOffset.y
                            hypot(tapPos.x - mPx, tapPos.y - mPy)
                        }

                        if (hit != null) {
                            val (xKm, yKm) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, hit.lat, hit.lon)
                            val mPx = cx + (xKm.toFloat() * baseScale * zoomScale) + panOffset.x
                            val mPy = cy - (yKm.toFloat() * baseScale * zoomScale) + panOffset.y
                            if (hypot(tapPos.x - mPx, tapPos.y - mPy) <= 50f) {
                                selectedMarker = hit
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = (size.width / 2f) + panOffset.x
                val cy = (size.height / 2f) + panOffset.y
                val maxRadiusPx = minOf(size.width / 2f, size.height / 2f) - 20f
                val scale = (maxRadiusPx / 55f) * zoomScale // Scaled for interactive zoom

                // 1. Satellite / Terrain Background Shading
                if (mapType == "satellite") {
                    // Zagros Mountain Range (North-East)
                    val mtnPath = Path().apply {
                        moveTo(0f, cy - 220f * scale)
                        lineTo(size.width, cy - 300f * scale)
                        lineTo(size.width, 0f)
                        lineTo(0f, 0f)
                        close()
                    }
                    drawPath(mtnPath, color = Color(0xFF2C3927).copy(alpha = 0.8f))

                    // Green Citrus Orchard Zone (Dezful - Shamsabad Plain)
                    drawCircle(
                        color = Color(0xFF2E4528).copy(alpha = 0.5f),
                        radius = 25f * scale,
                        center = Offset(cx, cy)
                    )
                }

                // 2. Topographic Contours (foothills of Zagros in NE)
                val topoPath1 = Path().apply {
                    moveTo(0f, cy - 140f * scale)
                    cubicTo(cx * 0.5f, cy - 180f * scale, cx * 1.1f, cy - 150f * scale, size.width, cy - 200f * scale)
                }
                drawPath(
                    path = topoPath1,
                    color = if (mapType == "satellite") Color(0xFF5A7253) else OutlineVariant.copy(alpha = 0.4f),
                    style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                )

                // 3. Dez River Hydrology Line (glowing cyan stream flowing down)
                val dezRiverPath = Path().apply {
                    moveTo(cx - 30f * scale, cy - 200f * scale)
                    cubicTo(cx - 20f * scale, cy - 120f * scale, cx + 10f * scale, cy - 50f * scale, cx, cy)
                    cubicTo(cx - 15f * scale, cy + 60f * scale, cx - 25f * scale, cy + 140f * scale, cx - 10f * scale, cy + 250f * scale)
                }
                drawPath(
                    path = dezRiverPath,
                    color = if (mapType == "satellite") Color(0xFF2B5B84) else PrimaryContainer.copy(alpha = 0.5f),
                    style = Stroke(width = 2.5f * zoomScale.coerceIn(0.8f, 2f), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 4f)))
                )

                // 4. Concentric Range Rings (Center: Shamsabad)
                listOf(10f, 25f, 50f, 100f, 150f).forEach { rKm ->
                    drawCircle(
                        color = if (mapType == "satellite") Color(0xFF8BA682).copy(alpha = 0.3f) else OutlineBorder.copy(alpha = 0.25f),
                        radius = rKm * scale,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    )
                }

                // 20 km Operational Target Radius (Solid cyan ring)
                drawCircle(
                    color = PrimarySky.copy(alpha = 0.65f),
                    radius = 20f * scale,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.2f)
                )

                // Crosshairs
                drawLine(color = OutlineBorder.copy(alpha = 0.2f), start = Offset(cx, 30f), end = Offset(cx, size.height - 30f), strokeWidth = 0.8f)
                drawLine(color = OutlineBorder.copy(alpha = 0.2f), start = Offset(30f, cy), end = Offset(size.width - 30f, cy), strokeWidth = 0.8f)

                // 5. Cloud Overlay Layer (when layerMode is "cloud" or "both")
                if (layerMode == "cloud" || layerMode == "both") {
                    val cloudColor = if (mapType == "satellite") Color(0x60FFFFFF) else Color(0x35E2F1FF)
                    // Semi-transparent cloud blobs over Zagros & region
                    drawCircle(color = cloudColor, radius = 45f * scale, center = Offset(cx - 30f * scale, cy - 40f * scale))
                    drawCircle(color = cloudColor.copy(alpha = 0.4f), radius = 60f * scale, center = Offset(cx + 20f * scale, cy - 90f * scale))
                    drawCircle(color = cloudColor.copy(alpha = 0.3f), radius = 35f * scale, center = Offset(cx + 10f * scale, cy + 15f * scale))
                }

                // 6. Doppler Radar Echoes (when layerMode is "radar" or "both")
                if (layerMode == "radar" || layerMode == "both") {
                    val frameOffsetShift = (currentFrame - 3) * 6f * zoomScale
                    val cellsToDraw = if (radar.cells.isNotEmpty()) radar.cells else {
                        if (radar.maxDbzInTarget >= Tuning.DBZ_LIGHT) {
                            listOf(
                                RadarCellInfo(
                                    id = "cell_1", pixelCount = 45, areaSqKm = 50.0, maxDbz = radar.maxDbzInTarget,
                                    centerLat = 32.35, centerLon = 48.35, centerDistKm = 15.0, centerBearingDeg = 315.0,
                                    radiusKm = 8.0, speedKmh = 25.0, moveDirectionToDeg = 135.0, state = CellApproachState.INSIDE_NOW,
                                    etaMinLow = null, etaMinHigh = null, minDistanceKm = 0.0, motionSource = "radar_track",
                                    xLocalKm = -12.0, yLocalKm = 10.0
                                )
                            )
                        } else emptyList()
                    }

                    for (cell in cellsToDraw) {
                        val baseCellX = cx + (cell.xLocalKm.toFloat() * scale)
                        val baseCellY = cy - (cell.yLocalKm.toFloat() * scale)
                        val animCellX = baseCellX + frameOffsetShift
                        val animCellY = baseCellY + frameOffsetShift * 0.8f

                        val cellColor = when {
                            cell.maxDbz >= 55 -> Color(0xFF93000A)
                            cell.maxDbz >= 45 -> ErrorRed
                            cell.maxDbz >= 35 -> SecondaryAmber
                            cell.maxDbz >= 25 -> TertiaryEmerald
                            else -> PrimaryContainer
                        }

                        val cellRadiusPx = (cell.radiusKm.toFloat() * scale).coerceIn(12f * zoomScale, 100f * zoomScale)

                        drawCircle(color = cellColor.copy(alpha = 0.25f), radius = cellRadiusPx * 1.5f, center = Offset(animCellX, animCellY))
                        drawCircle(color = cellColor.copy(alpha = 0.65f), radius = cellRadiusPx, center = Offset(animCellX, animCellY))
                        drawCircle(color = cellColor, radius = (cellRadiusPx * 0.4f).coerceAtLeast(4f), center = Offset(animCellX, animCellY))

                        val spd = cell.speedKmh
                        val dirTo = cell.moveDirectionToDeg
                        if (spd != null && dirTo != null && spd > 1.0) {
                            val moveRad = Math.toRadians(dirTo)
                            val arrowLen = (spd * 0.6).toFloat() * scale
                            val endX = animCellX + arrowLen * sin(moveRad).toFloat()
                            val endY = animCellY - arrowLen * cos(moveRad).toFloat()
                            drawLine(color = SecondaryAmber.copy(alpha = 0.85f), start = Offset(animCellX, animCellY), end = Offset(endX, endY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        }
                    }
                }

                // 7. Render 17 Station Markers & Landmarks
                for (m in stationMarkers) {
                    val (xKm, yKm) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, m.lat, m.lon)
                    val mPx = cx + (xKm.toFloat() * scale)
                    val mPy = cy - (yKm.toFloat() * scale)

                    // Marker Pin Color
                    val pinColor = when {
                        m.isCenter -> PrimarySky
                        m.isSynopStation -> SecondaryAmber
                        (m.dbz ?: 0) >= Tuning.DBZ_RAIN -> ErrorRed
                        (m.cloudCover ?: 0.0) >= 60.0 -> PrimaryContainer
                        else -> TertiaryEmerald
                    }

                    if (m.isCenter || m.isSynopStation) {
                        // Halo pulse
                        drawCircle(color = pinColor.copy(alpha = 0.3f), radius = 12f * zoomScale.coerceIn(0.8f, 2f), center = Offset(mPx, mPy))
                        drawCircle(color = pinColor, radius = 6f * zoomScale.coerceIn(0.8f, 2f), center = Offset(mPx, mPy))
                        drawCircle(color = SurfaceBg, radius = 2.5f * zoomScale.coerceIn(0.8f, 2f), center = Offset(mPx, mPy))
                    } else {
                        drawCircle(color = pinColor, radius = 4.5f * zoomScale.coerceIn(0.7f, 1.8f), center = Offset(mPx, mPy))
                        drawCircle(color = SurfaceBg, radius = 1.8f * zoomScale.coerceIn(0.7f, 1.8f), center = Offset(mPx, mPy))
                    }
                }
            }

            // Overlay Floating UI Elements
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-Left: Viewport Dynamic Analysis Banner (Zoom-Out Calculation)
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh.copy(alpha = 0.92f)),
                    border = BorderStroke(0.8.dp, PrimarySky.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = viewportAnalysis.summaryTextFa,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Top-Right Floating Controls Column (Zoom In, Zoom Out, Recenter, Map Type, Layer Switch)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Zoom In Button (+)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh.copy(alpha = 0.9f))
                            .clickable { zoomScale = (zoomScale * 1.3f).coerceAtMost(4.0f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "زوم این", tint = PrimarySky, modifier = Modifier.size(18.dp))
                    }

                    // Zoom Out Button (-)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh.copy(alpha = 0.9f))
                            .clickable { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.4f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "زوم اوت", tint = PrimarySky, modifier = Modifier.size(18.dp))
                    }

                    // Recenter Button (🎯)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh.copy(alpha = 0.9f))
                            .clickable {
                                zoomScale = 1.0f
                                panOffset = Offset.Zero
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = "تمرکز روی شمس‌آباد", tint = SecondaryAmber, modifier = Modifier.size(18.dp))
                    }

                    // Map Type Toggle (Satellite vs Dark)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (mapType == "satellite") SecondaryAmber else SurfaceContainerHigh.copy(alpha = 0.9f))
                            .clickable { mapType = if (mapType == "dark") "satellite" else "dark" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mapType == "satellite") Icons.Default.SatelliteAlt else Icons.Default.Map,
                            contentDescription = "نوع نقشه",
                            tint = if (mapType == "satellite") Color(0xFF402D00) else TextOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Layer Selector (Radar vs Cloud vs Both)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (layerMode != "radar") PrimarySky else SurfaceContainerHigh.copy(alpha = 0.9f))
                            .clickable {
                                layerMode = when (layerMode) {
                                    "both" -> "radar"
                                    "radar" -> "cloud"
                                    else -> "both"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (layerMode) {
                                "cloud" -> Icons.Default.Cloud
                                "both" -> Icons.Default.Layers
                                else -> Icons.Default.WaterDrop
                            },
                            contentDescription = "لایه‌ها",
                            tint = if (layerMode != "radar") Color(0xFF00354A) else TextOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Scale & Mode Indicator
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نقشه ${if (mapType == "satellite") "ماهواره‌ای 🛰️" else "ساده 🗺️"} · لایه: ${when (layerMode) { "both" -> "ابر + رادار 🌦️"; "cloud" -> "ابرها ☁️"; else -> "رادار 🌧️" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        color = TextOnSurfaceVariant
                    )
                }
            }
        }

        // Selected Station Detail Floating Card
        if (selectedMarker != null) {
            val m = selectedMarker!!
            val (distKm, _) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, m.lat, m.lon)
            val distFormatted = String.format(Locale.US, "%.1f", distKm)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                border = BorderStroke(1.2.dp, PrimarySky)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = m.nameFa, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimarySky)
                                Text(
                                    text = "مختصات: ${m.lat}°N , ${m.lon}°E (فاصله ${JalaliCalendar.formatNumber(distKm)}km)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
                                    color = TextOnSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { selectedMarker = null }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextOnSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("دما", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                            Text(text = m.tempC?.let { "${JalaliCalendar.formatNumber(it)}°C" } ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("رطوبت", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                            Text(text = m.rh?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("پوشش ابرها", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                            Text(text = m.cloudCover?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("پژواک رادار", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant, fontSize = 9.sp)
                            Text(text = PlainText.intensityFa(m.dbz), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SecondaryAmber)
                        }
                    }

                    Text(
                        text = "وضعیت پایش: ${m.skyConditionFa}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 3: "ایستگاه‌ها" (Stations & Models Health)
// -----------------------------------------------------------------------------
@Composable
private fun StationsTabContent(
    dashboard: ForecastDashboard,
    isAlertsEnabled: Boolean,
    onOpenSkyObservation: () -> Unit,
    onOpenAlertSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Safiabad SYNOP Station Card
        item {
            SynopObsCard(synop = dashboard.synopObs, modelCurrent = dashboard.modelCurrent)
        }

        // 2. User Sky Observation Action & Report
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SecondaryAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "دیده‌بان آسمان (گزارش محلی شما)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = onOpenSkyObservation,
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryAmber, contentColor = Color(0xFF402D00)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ثبت مشاهده", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (dashboard.userObservation != null) {
                        val obs = dashboard.userObservation
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHigh)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "پدیده مشاهده‌شده: ${obs.phenomenon} (جهت: ${obs.direction})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryAmber
                                )
                                if (obs.notes.isNotBlank()) {
                                    Text(text = "یادداشت: ${obs.notes}", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Models and Data Sources Health
        item {
            SourceHealthCard(
                sources = dashboard.sourceHealth,
                stats = dashboard.verificationStats
            )
        }

        // 4. Alert settings trigger
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAlertSettings),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAlertsEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (isAlertsEnabled) SecondaryAmber else TextOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنظیمات پایش خودکار و اعلان‌ها",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAlertsEnabled) "فعال • ارسال هشدار هنگام ناپایداری" else "غیرفعال",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnSurfaceVariant
                            )
                        }
                    }
                    Text("ویرایش ⚙️", style = MaterialTheme.typography.labelSmall, color = PrimarySky, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Existing Components (Preserved logic with upgraded HTML palette)
// -----------------------------------------------------------------------------
@Composable
private fun HeroAlertTierCard(
    verdict: VerdictInfo,
    synop: SynopObs?,
    modelCurrent: ModelCurrent?,
    userObservation: SkyObservationReport? = null,
    onClearObservation: () -> Unit = {},
    onOpenRegionInfo: () -> Unit = {}
) {
    val tier = verdict.tier
    val tierColor = when (tier) {
        AlertTier.RED -> ErrorRed
        AlertTier.ORANGE -> Color(0xFFF97316)
        AlertTier.YELLOW -> SecondaryAmber
        AlertTier.GREEN -> TertiaryEmerald
        AlertTier.UNKNOWN -> Color(0xFF9CA3AF)
    }
    val icon = when (tier) {
        AlertTier.RED -> Icons.Default.Warning
        AlertTier.ORANGE -> Icons.Default.Bolt
        AlertTier.YELLOW -> Icons.Default.Visibility
        AlertTier.GREEN -> Icons.Default.CheckCircle
        AlertTier.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
    }

    val curTemp = synop?.tempC ?: modelCurrent?.tempC
    val curRh = synop?.rh ?: modelCurrent?.rh
    val curWind = modelCurrent?.windSpeedKmh
    val feelsLike = computeFeelsLike(curTemp, curRh, curWind)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.14f)),
        border = BorderStroke(1.5.dp, tierColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = verdict.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = tier.name,
                    tint = tierColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (userObservation != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryAmber.copy(alpha = 0.2f))
                        .border(1.dp, SecondaryAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 دیده‌بان شما: ${userObservation.phenomenon} (${userObservation.direction})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onClearObservation, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "حذف", tint = TextOnSurfaceVariant, modifier = Modifier.size(12.dp))
                    }
                }
            }

            if (curTemp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.8f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Thermostat, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "دما: ${JalaliCalendar.formatNumber(curTemp)}°C ${if (synop != null) "(ایستگاه صفی‌آباد)" else "(برآورد مدل)"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (feelsLike != null) {
                        Text(
                            text = "حسی: ${JalaliCalendar.formatNumber(feelsLike)}°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimarySky,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = verdict.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (verdict.advisoryNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📢 ${verdict.advisoryNote}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceContainerLow.copy(alpha = 0.6f))
                    .clickable(onClick = onOpenRegionInfo)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "منظور از منطقه: شمس‌آباد، صفی‌آباد، دزفول، اندیمشک (۲۰km)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextOnSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Text("جزئیات ℹ️", style = MaterialTheme.typography.labelSmall, color = PrimarySky, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HourlyTimelineSection(
    rows: List<ForecastRow>,
    selectedRange: ForecastRange,
    onSelectRange: (ForecastRange) -> Unit
) {
    var selectedRowIndex by remember { mutableIntStateOf(-1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏰ پیش‌بینی ساعتی ۲۰km شمس‌آباد",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimarySky
                )
                Text(
                    text = "برای جزییات روی ساعت بزنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                itemsIndexed(rows.take(24)) { index, row ->
                    val probAreaStr = row.probArea?.let { "${it.toInt()}٪" } ?: "نامشخص"
                    val probPointStr = row.probPoint?.let { "${it.toInt()}٪" } ?: "نامشخص"
                    val isRain = (row.probArea ?: 0.0) >= 20.0 || (row.amountP50 ?: 0.0) >= 0.1
                    val isSelected = selectedRowIndex == index

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimarySky.copy(alpha = 0.35f) else if (isRain) PrimarySky.copy(alpha = 0.18f) else SurfaceContainerHigh
                        ),
                        border = BorderStroke(1.5.dp, if (isSelected) PrimarySky else if (isRain) PrimarySky.copy(alpha = 0.5f) else OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .width(88.dp)
                            .clickable {
                                selectedRowIndex = if (isSelected) -1 else index
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = PlainText.hourLabelFa(row.timeEpochSec, System.currentTimeMillis() / 1000L),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = row.tempC?.let { "${JalaliCalendar.formatNumber(it)}°" } ?: "—",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        row.probArea == null -> Icons.AutoMirrored.Filled.HelpOutline
                                        isRain -> Icons.Default.WaterDrop
                                        else -> Icons.Default.WbSunny
                                    },
                                    contentDescription = null,
                                    tint = if (row.probArea == null) TextOnSurfaceVariant else if (isRain) PrimarySky else SecondaryAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = probAreaStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRain) PrimarySky else TextOnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "مرکز: $probPointStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnSurfaceVariant,
                                fontSize = 9.sp
                            )
                            if (row.amountP50 != null && row.amountP50 >= 0.1) {
                                Text(
                                    text = "${JalaliCalendar.formatNumber(row.amountP50)} mm",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimarySky,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    text = row.convLabel.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnSurfaceVariant,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (selectedRowIndex in rows.indices) {
                val selRow = rows[selectedRowIndex]
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceContainerHigh)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🔍 جزئیات پیش‌بینی ساعت ${selRow.timeIran}:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySky
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("احتمال محدوده ۲۰km: ${selRow.probArea?.let { "${it.toInt()}٪" } ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall)
                        Text("احتمال نقطه‌ای شمس‌آباد: ${selRow.probPoint?.let { "${it.toInt()}٪" } ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("بارش محتمل (P50): ${selRow.amountP50?.let { "${JalaliCalendar.formatNumber(it)} mm" } ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall)
                        Text("بیشینه بارش (P90): ${selRow.amountP90?.let { "${JalaliCalendar.formatNumber(it)} mm" } ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("دما: ${selRow.tempC?.let { "${JalaliCalendar.formatNumber(it)}°C" } ?: "—"} | شبنم: ${selRow.dewC?.let { "${JalaliCalendar.formatNumber(it)}°C" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant)
                        Text("رطوبت: ${selRow.rh?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تندباد: ${selRow.gustKmh?.let { "${JalaliCalendar.formatNumber(it)} km/h" } ?: "—"} (${selRow.windDirLabel ?: "—"})", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant)
                        Text("پوشش ابر: ${selRow.cloud?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextOnSurfaceVariant)
                    }
                    if (selRow.sources.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("منبع: ${selRow.sources.joinToString("، ")}", style = MaterialTheme.typography.labelSmall, color = PrimarySky, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighbourhoodSummaryCard(neighbourhood: NeighbourhoodSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🌐 پایش شبکه ۱۷ نقطه‌ای همسایگی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimarySky
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (neighbourhood.totalPoints > 0)
                        "نقاط دارای سیگنال بارش: ${neighbourhood.pointsWithRainNow} از ${neighbourhood.totalPoints} نقطه"
                    else "نقاط دارای سیگنال بارش: نامشخص (دادهٔ مدل نرسیده)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (neighbourhood.wettestDirectionFa != null) {
                    Text(
                        text = "جهت بارشی: ${neighbourhood.wettestDirectionFa}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimarySky
                    )
                }
            }
        }
    }
}

@Composable
private fun SynopObsCard(synop: SynopObs?, modelCurrent: ModelCurrent?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏢 ایستگاه سینوپتیک صفی‌آباد (WMO 40794)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimarySky
                )
                Text(
                    text = "فاصله: ۳km هوایی",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (synop != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("زمان گزارش: ${JalaliCalendar.formatShort(ForecastEngine.epochToIran(synop.timeUtcEpochSec))}", style = MaterialTheme.typography.labelSmall)
                        synop.tempC?.let { Text("دما: ${JalaliCalendar.formatNumber(it)}°C", style = MaterialTheme.typography.labelSmall) }
                        synop.dewC?.let { Text("نقطه شبنم: ${JalaliCalendar.formatNumber(it)}°C", style = MaterialTheme.typography.labelSmall) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        synop.rh?.let { Text("رطوبت نسبی: ${JalaliCalendar.formatNumber(it)}٪", style = MaterialTheme.typography.labelSmall) }
                        modelCurrent?.windSpeedKmh?.let { Text("باد مدل: ${JalaliCalendar.formatNumber(it)} km/h", style = MaterialTheme.typography.labelSmall) }
                        synop.precipMm?.let { Text("بارش دوره: ${JalaliCalendar.formatNumber(it)} mm", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                synop.ww?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "کد پدیده جوی (WW): $it",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryAmber
                    )
                }
                if (synop.isRainNow) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🌧️ ثبت بارش زنده در ایستگاه صفی‌آباد",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySky
                    )
                }
            } else {
                Text(
                    text = "گزارش سینوپتیک صفی‌آباد در این نوبت در دسترس نیست. برآورد بر پایه مدل عددی است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatternConvectiveSummaryCard(pattern: PatternSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "⚡ الگوی ناپایداری و بارش ۲۴ ساعت آینده",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimarySky
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = pattern.precipSummaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("بارش محتمل ۲۴h: ${JalaliCalendar.formatNumber(pattern.totalPrecip24hLikely)} mm", style = MaterialTheme.typography.labelSmall)
                Text("بیشینه بارش ۲۴h: ${JalaliCalendar.formatNumber(pattern.totalPrecip24hMax)} mm", style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ساعت اوج بارش: ${pattern.peakPrecipTimeIran}", style = MaterialTheme.typography.labelSmall)
                Text("ساعت اوج ناپایداری: ${pattern.peakTsTimeIran}", style = MaterialTheme.typography.labelSmall)
            }
            if (pattern.maxCape > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("بیشینه CAPE: ${JalaliCalendar.formatNumber(pattern.maxCape)} J/kg", style = MaterialTheme.typography.labelSmall)
                    Text("بیشینه تندباد: ${JalaliCalendar.formatNumber(pattern.maxGust24h)} km/h", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (pattern.virgaHoursCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🌫️ ریسک ویرگا (تبخیر باران زیر ابر): ${pattern.virgaHoursCount} ساعت",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryAmber
                )
            }
        }
    }
}

@Composable
private fun SourceHealthCard(sources: List<SourceHealth>, stats: VerificationStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "📡 سلامت منابع داده و مدل‌ها",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimarySky
            )
            Spacer(modifier = Modifier.height(8.dp))
            sources.forEach { s ->
                val isOk = s.status == SourceStatus.OK
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOk) TertiaryEmerald else ErrorRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = PlainText.sourceNameFa(s.source), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = s.message ?: PlainText.statusFa(s.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOk) TertiaryEmerald else ErrorRed
                    )
                }
            }

            if (stats != null && stats.totalRecords > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📊 ارزیابی دقت روی دستگاه (Brier Score):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimarySky
                )
                Text(
                    text = "تعداد ارزیابی‌ها: ${stats.totalRecords} نوبت · امتیاز بریر ۶h: ${stats.lead6h.brierScore?.let { JalaliCalendar.formatNumber(it) } ?: "در حال محاسبه"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnSurfaceVariant
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bottom Sheets (Region Coverage, Sky Observation, Alert Settings)
// -----------------------------------------------------------------------------
@Composable
private fun RegionCoverageBottomSheetContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📍 محدوده عملیاتی شعاع ۲۰ کیلومتر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimarySky
            )
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextOnSurfaceVariant)
            }
        }

        Text(
            text = "مرکز پایش این سامانه در شهر شمس‌آباد دزفول (${RegionConfig.CENTER.lat}°N, ${RegionConfig.CENTER.lon}°E) واقع است. دایرهٔ ۲۰ کیلومتری شامل مناطق زیر است:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("• شمس‌آباد و صفی‌آباد (ایستگاه سینوپتیک WMO 40794 در ۳km)", style = MaterialTheme.typography.labelSmall)
                Text("• شهر دزفول (فاصله حدود ۱۰-۱۲ کیلومتر شمال)", style = MaterialTheme.typography.labelSmall)
                Text("• حومه جنوبی اندیمشک (در مرز ۲۰ کیلومتری شمال‌غرب)", style = MaterialTheme.typography.labelSmall)
                Text("• شوش و شوشتر در فاصله‌های ۳۵ تا ۴۰ کیلومتری خارج از دایرهٔ هدف", style = MaterialTheme.typography.labelSmall)
                Text("• دشت‌های حاصلخیز کشاورزی و باغات مرکبات دزفول", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text(
            text = "سامانه به صورت ۲۴ ساعته هر ۱۰ دقیقه داده‌های ماهواره‌ای، راداری و خروجی مدل‌های عددی اروپایی و آلمانی را برای این محدوده تطبیق می‌دهد.",
            style = MaterialTheme.typography.bodySmall,
            color = TextOnSurfaceVariant
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimarySky, contentColor = Color(0xFF00354A)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("متوجه شدم")
        }
    }
}

@Composable
private fun SkyObservationBottomSheetContent(
    existingReport: SkyObservationReport?,
    onSubmit: (Bitmap?, String, String, Double?, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var direction by rememberSaveable { mutableStateOf("شمس‌آباد (مرکز)") }
    var phenomenon by rememberSaveable { mutableStateOf("باران پیوسته") }
    var notes by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    photoBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📸 دیده‌بان آسمان (گزارش کاربر)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SecondaryAmber
            )
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextOnSurfaceVariant)
            }
        }

        Text(
            text = "مشاهدهٔ شما مستقیماً بر هشدار لحظه‌ای اپ اثر می‌گذارد و تا ۴۰ دقیقه با وزن کاهنده معتبر است.",
            style = MaterialTheme.typography.bodySmall,
            color = TextOnSurfaceVariant
        )

        // Photo Preview / Picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerHigh)
                .clickable {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap!!.asImageBitmap(),
                    contentDescription = "عکس آسمان",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = PrimarySky, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("برای افزودن عکس لمس کنید", style = MaterialTheme.typography.labelSmall, color = PrimarySky)
                }
            }
        }

        // Phenomenon Selector
        Text("پدیده جوی در محل شما:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        val phenomena = listOf("نم‌نم باران", "باران پیوسته", "رگبار شدید", "رعد و برق", "تگرگ", "تندباد", "صاف", "ابری")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(phenomena) { p ->
                FilterChip(
                    selected = phenomenon == p,
                    onClick = { phenomenon = p },
                    label = { Text(p, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SecondaryAmber,
                        selectedLabelColor = Color(0xFF402D00)
                    )
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("توضیحات تکمیلی (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimarySky,
                unfocusedBorderColor = OutlineVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (existingReport != null) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حذف گزارش قبلی")
                }
            }
            Button(
                onClick = { onSubmit(photoBitmap, direction, phenomenon, null, notes) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryAmber, contentColor = Color(0xFF402D00)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("ارسال گزارش دیده‌بان")
            }
        }
    }
}

@Composable
private fun WeatherAlertBottomSheetContent(
    settings: AlertSettings,
    feedbackMessage: String?,
    onUpdateSettings: (AlertSettings) -> Unit,
    onTriggerTest: () -> Unit,
    onClearFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔔 تنظیمات اعلان‌ها و پایش پس‌زمینه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimarySky
            )
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextOnSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("فعال بودن پایش پس‌زمینه", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.isEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = PrimarySky, checkedTrackColor = Color(0xFF0C4A6E))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("اعلان رعدوبرق و صاعقه", style = MaterialTheme.typography.bodyMedium)
            Checkbox(
                checked = settings.notifyThunderstorm,
                onCheckedChange = { onUpdateSettings(settings.copy(notifyThunderstorm = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("اعلان بارش شدید و تگرگ", style = MaterialTheme.typography.bodyMedium)
            Checkbox(
                checked = settings.notifyHeavyRain,
                onCheckedChange = { onUpdateSettings(settings.copy(notifyHeavyRain = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("اعلان ورود ابرهای بارشی", style = MaterialTheme.typography.bodyMedium)
            Checkbox(
                checked = settings.notifyRainClouds,
                onCheckedChange = { onUpdateSettings(settings.copy(notifyRainClouds = it)) }
            )
        }

        if (feedbackMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = feedbackMessage, style = MaterialTheme.typography.labelSmall, color = PrimarySky)
                    IconButton(onClick = onClearFeedback, modifier = Modifier.size(16.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = TextOnSurfaceVariant)
                    }
                }
            }
        }

        Button(
            onClick = onTriggerTest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = PrimarySky),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("ارسال یک اعلان آزمایشی تست")
        }
    }
}
