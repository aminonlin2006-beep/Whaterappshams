package com.amin.weather.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlin.math.cos
import kotlin.math.sin

fun computeFeelsLike(tempC: Double?, humidity: Double?, windSpeedKmh: Double?): Double? {
    if (tempC == null) return null
    val t = tempC
    val rh = (humidity ?: 40.0).coerceIn(5.0, 100.0)
    val wsMs = ((windSpeedKmh ?: 8.0) / 3.6).coerceAtLeast(0.0)
    val e = (rh / 100.0) * 6.105 * Math.exp((17.27 * t) / (237.7 + t))
    val at = t + 0.33 * e - 0.70 * wsMs - 4.00
    return Math.round(at * 10.0) / 10.0
}

@Composable
fun EducationalDisclosureBox(
    sourceName: String,
    reasoningText: String,
    accentColor: Color = SkyAccent,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateDeep.copy(alpha = 0.55f))
            .border(0.8.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🎓 مبنای این پیش‌بینی و تحلیل علمی",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "بستن آموزش" else "مشاهده آموزش",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
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
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(
                    color = SlateBorder.copy(alpha = 0.35f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "📡 منبع داده: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SkyAccent
                    )
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "🔍 تحلیل و مبنای علمی: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberStorm
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = SlateDeep,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "آیکون رادار",
                                tint = SkyAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "هواشناسی امین",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "پایش ۲۰ km شمس‌آباد دزفول",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showRegionInfoSheet = true },
                            modifier = Modifier.testTag("region_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "محدوده دقیق منطقه",
                                tint = SkyAccent
                            )
                        }
                        IconButton(
                            onClick = { showSkyObservationSheet = true },
                            modifier = Modifier.testTag("sky_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "دیده‌بان آسمان",
                                tint = if (uiState.dashboard?.userObservation != null) AmberStorm else SkyAccent
                            )
                        }
                        if (uiState.dashboard != null) {
                            IconButton(
                                onClick = {
                                    val d = uiState.dashboard
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
                                },
                                modifier = Modifier.testTag("share_weather_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "اشتراک‌گذاری",
                                    tint = SkyAccent
                                )
                            }
                        }
                        IconButton(
                            onClick = { showNotificationSheet = true },
                            modifier = Modifier.testTag("open_alerts_sheet_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.alertSettings.isEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "تنظیمات اعلان‌ها",
                                tint = if (uiState.alertSettings.isEnabled) AmberStorm else TextSecondary
                            )
                        }
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                                color = SkyAccent
                            )
                        } else {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "به‌روزرسانی",
                                    tint = SkyAccent
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
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
                            CircularProgressIndicator(color = SkyAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "در حال تحلیل پایش ۲۰km شمس‌آباد و دیدبانی صفی‌آباد...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
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
                                tint = CoralAlert,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Text("تلاش مجدد")
                            }
                        }
                    }

                    uiState.dashboard != null -> {
                        DashboardContent(
                            dashboard = uiState.dashboard,
                            selectedRange = uiState.selectedRange,
                            isFromCache = uiState.isFromCache,
                            errorMessage = uiState.errorMessage,
                            onSelectRange = onSelectRange,
                            selectedBottomTab = uiState.selectedBottomTab,
                            onSelectBottomTab = onSelectBottomTab,
                            onClearSkyObservation = onClearSkyObservation,
                            onOpenRegionInfo = { showRegionInfoSheet = true }
                        )
                    }
                }
            }

            if (showRegionInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showRegionInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SlateCard
                ) {
                    RegionCoverageBottomSheetContent(onDismiss = { showRegionInfoSheet = false })
                }
            }

            if (showSkyObservationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSkyObservationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = SlateCard
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
                    containerColor = SlateCard
                ) {
                    WeatherAlertBottomSheetContent(
                        settings = uiState.alertSettings,
                        feedbackMessage = uiState.alertFeedbackMessage,
                        onUpdateSettings = onUpdateAlertSettings,
                        onTriggerAlert = handleTriggerAlert,
                        onClearFeedback = onClearFeedbackMessage,
                        onDismiss = { showNotificationSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: ForecastDashboard,
    selectedRange: ForecastRange,
    isFromCache: Boolean,
    errorMessage: String?,
    onSelectRange: (ForecastRange) -> Unit,
    selectedBottomTab: Int = 0,
    onSelectBottomTab: (Int) -> Unit = {},
    onClearSkyObservation: () -> Unit = {},
    onOpenRegionInfo: () -> Unit = {}
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
                    colors = CardDefaults.cardColors(containerColor = AmberStorm.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AmberStorm.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "آفلاین",
                            tint = AmberStorm,
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

        // 1. Region Location Banner & Refresh Time
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRegionInfo),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = SkyAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "محدوده پایش: منطقه شمس‌آباد (شعاع ۲۰km)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SkyAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "جزئیات منطقه",
                                    tint = SkyAccent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "شامل: شمس‌آباد، صفی‌آباد، دزفول، اندیمشک و مزارع اطراف",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = JalaliCalendar.formatShort(dashboard.generatedIran),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralAlert.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "⚠️ $errorMessage",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralAlert,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 2. Hero Alert Tier Card
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

        // 3. Compact Horizontal Timeline
        item {
            HourlyTimelineSection(
                rows = hourlyRows,
                selectedRange = selectedRange,
                onSelectRange = onSelectRange
            )
        }

        // 4. Tab selection row
        item {
            TabRow(
                selectedTabIndex = selectedBottomTab,
                containerColor = SlateSurface,
                contentColor = SkyAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedBottomTab]),
                        color = SkyAccent
                    )
                }
            ) {
                Tab(
                    selected = selectedBottomTab == 0,
                    onClick = { onSelectBottomTab(0) },
                    text = { Text("📡 رادار و همسایگی", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedBottomTab == 1,
                    onClick = { onSelectBottomTab(1) },
                    text = { Text("🏢 دیدبانی صفی‌آباد", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedBottomTab == 2,
                    onClick = { onSelectBottomTab(2) },
                    text = { Text("⚡ الگوی همرفتی و سلامت", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // 5. Active Tab Content
        item {
            when (selectedBottomTab) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadarCanvasCard(radar = dashboard.radarNowcast)
                        NeighbourhoodSummaryCard(neighbourhood = dashboard.neighbourhood)
                    }
                }
                1 -> SynopObsCard(synop = dashboard.synopObs, modelCurrent = dashboard.modelCurrent)
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PatternConvectiveSummaryCard(pattern = dashboard.pattern)
                        SourceHealthCard(
                            sources = dashboard.sourceHealth,
                            stats = dashboard.verificationStats
                        )
                    }
                }
            }
        }

        // 6. Footer Attribution
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "داده‌های هواشناسی: Open-Meteo.com · رادار: RainViewer · مشاهده: Ogimet/WMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                )
                Text(
                    text = "پایش دقیق بارش شعاع ۲۰ کیلومتری شمس‌آباد دزفول (پکیج com.amin.weather)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp
                )
            }
        }
    }
}

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
        AlertTier.RED -> CoralAlert
        AlertTier.ORANGE -> Color(0xFFF97316)
        AlertTier.YELLOW -> AmberStorm
        AlertTier.GREEN -> EmeraldOk
        AlertTier.UNKNOWN -> Color(0xFF9CA3AF)
    }
    val icon = when (tier) {
        AlertTier.RED -> Icons.Default.Warning
        AlertTier.ORANGE -> Icons.Default.Bolt
        AlertTier.YELLOW -> Icons.Default.Visibility
        AlertTier.GREEN -> Icons.Default.CheckCircle
        AlertTier.UNKNOWN -> Icons.Default.HelpOutline
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
                        text = tier.title,
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
                        .background(AmberStorm.copy(alpha = 0.2f))
                        .border(1.dp, AmberStorm.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 دیده‌بان شما: ${userObservation.phenomenon} (${userObservation.direction})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberStorm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onClearObservation, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "حذف", tint = TextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }

            if (curTemp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurface.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Thermostat, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(18.dp))
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
                            color = SkyAccent,
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
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateSurface.copy(alpha = 0.5f))
                    .clickable(onClick = onOpenRegionInfo)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "منظور از منطقه: شمس‌آباد، صفی‌آباد، دزفول، اندیمشک (۲۰km)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Text("جزئیات ℹ️", style = MaterialTheme.typography.labelSmall, color = SkyAccent, fontSize = 9.sp)
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
        colors = CardDefaults.cardColors(containerColor = SlateCard)
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
                    color = SkyAccent
                )
                Text(
                    text = "برای جزییات روی ساعت بزنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
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
                            containerColor = if (isSelected) SkyAccent.copy(alpha = 0.35f) else if (isRain) SkyAccent.copy(alpha = 0.18f) else SlateSurface
                        ),
                        border = BorderStroke(1.5.dp, if (isSelected) SkyAccent else if (isRain) SkyAccent.copy(alpha = 0.5f) else SlateBorder.copy(alpha = 0.3f)),
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
                                text = row.timeIran.takeLast(5),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
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
                                    imageVector = if (isRain) Icons.Default.WaterDrop else Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = if (isRain) SkyAccent else AmberStorm,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = probAreaStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRain) SkyAccent else TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "مرکز: $probPointStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                            if (row.amountP50 != null && row.amountP50 >= 0.1) {
                                Text(
                                    text = "${JalaliCalendar.formatNumber(row.amountP50)} mm",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SkyAccent,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    text = row.convLabel.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Expanded Detail View for Selected Hour
            if (selectedRowIndex in rows.indices) {
                val selRow = rows[selectedRowIndex]
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SlateBorder.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🔍 جزئیات پیش‌بینی ساعت ${selRow.timeIran}:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SkyAccent
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
                        Text("دما: ${selRow.tempC?.let { "${JalaliCalendar.formatNumber(it)}°C" } ?: "—"} | شبنم: ${selRow.dewC?.let { "${JalaliCalendar.formatNumber(it)}°C" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("رطوبت: ${selRow.rh?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تندباد: ${selRow.gustKmh?.let { "${JalaliCalendar.formatNumber(it)} km/h" } ?: "—"} (${selRow.windDirLabel ?: "—"})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("پوشش ابر: ${selRow.cloud?.let { "${JalaliCalendar.formatNumber(it)}٪" } ?: "—"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    if (selRow.sources.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("منبع: ${selRow.sources.joinToString("، ")}", style = MaterialTheme.typography.labelSmall, color = SkyAccent, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarCanvasCard(radar: RadarNowcastResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, if (radar.maxDbzInTarget >= Tuning.DBZ_RAIN) AmberStorm.copy(alpha = 0.5f) else SkyAccent.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📡 رادار زنده RainViewer — شعاع ۲۰km",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SkyAccent
                )
                Text(
                    text = if (radar.available) "سن فریم: ${radar.frameAgeMin} دقیقه" else "ناموجود",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateDeep)
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxRadiusPx = minOf(cx, cy) - 16f

                    val scale = maxRadiusPx / 100f

                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.2f),
                        radius = 100f * scale,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )

                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.3f),
                        radius = 50f * scale,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    )

                    drawCircle(
                        color = SkyAccent,
                        radius = 20f * scale,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )

                    drawCircle(
                        color = Color.Yellow,
                        radius = 4f,
                        center = Offset(cx, cy)
                    )

                    for (cell in radar.cells) {
                        val cellPxX = cx + (cell.xLocalKm.toFloat() * scale)
                        val cellPxY = cy - (cell.yLocalKm.toFloat() * scale)

                        val cellColor = when {
                            cell.maxDbz >= 45 -> CoralAlert
                            cell.maxDbz >= 35 -> Color(0xFFF97316)
                            cell.maxDbz >= 25 -> AmberStorm
                            else -> SkyAccent
                        }

                        val cellRadiusPx = (cell.radiusKm.toFloat() * scale).coerceAtLeast(6f)
                        drawCircle(
                            color = cellColor.copy(alpha = 0.5f),
                            radius = cellRadiusPx,
                            center = Offset(cellPxX, cellPxY)
                        )
                        drawCircle(
                            color = cellColor,
                            radius = 3f,
                            center = Offset(cellPxX, cellPxY)
                        )

                        val spd = cell.speedKmh
                        val dirTo = cell.moveDirectionToDeg
                        if (spd != null && dirTo != null && spd > 1.0) {
                            val moveRad = Math.toRadians(dirTo)
                            val arrowLen = (spd * 0.5).toFloat() * scale
                            val endX = cellPxX + arrowLen * sin(moveRad).toFloat()
                            val endY = cellPxY - arrowLen * cos(moveRad).toFloat()
                            drawLine(
                                color = Color.White,
                                start = Offset(cellPxX, cellPxY),
                                end = Offset(endX, endY),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(6.dp)
                ) {
                    Text("📍 مرکز: شمس‌آباد (دایره ۲۰km)", color = SkyAccent, fontSize = 9.sp)
                    Text("⚪ دایره‌ها: ۵۰km و ۱۰۰km", color = Color.LightGray, fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = radar.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (radar.firstArrival != null) {
                val arr = radar.firstArrival!!
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberStorm.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "⚡ نزدیک‌ترین توده بارشی: ${arr.maxDbz} dBZ در ${arr.distKm.toInt()}km (${arr.directionFa})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberStorm
                        )
                        val spdStr = arr.speedKmh?.toInt()?.toString() ?: "نامعلوم"
                        val headingStr = if (arr.state == CellApproachState.UNKNOWN_MOTION) "جهت حرکت نامعلوم" else "حرکت به سمت ${arr.headingFa}"
                        val motionSourceFa = when (arr.motionSource) {
                            "radar_track" -> "ردیابی رادار"
                            "steering_wind" -> "باد مدل"
                            else -> "نامعلوم"
                        }
                        val etaStr = if (arr.etaMinLow != null && arr.etaMinHigh != null) "${arr.etaMinLow} تا ${arr.etaMinHigh} دقیقه دیگر" else "نامشخص"
                        val covNotice = if (!radar.coverageVerified) " · پوشش رادار تایید نشده" else ""
                        val clutterNotice = if (radar.clutterPixelsRemoved > 0) " (${radar.clutterPixelsRemoved} پیکسل کلاتر حذف شد)" else ""

                        Text(
                            text = "$headingStr با سرعت $spdStr km/h ($motionSourceFa) · زمان ورود: $etaStr$covNotice$clutterNotice",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
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
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🌐 پایش شبکه ۱۷ نقطه‌ای همسایگی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SkyAccent
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نقاط دارای سیگنال بارش: ${neighbourhood.pointsWithRainNow} از ${neighbourhood.totalPoints} نقطه",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (neighbourhood.wettestDirectionFa != null) {
                    Text(
                        text = "جهت بارشی: ${neighbourhood.wettestDirectionFa}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyAccent
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
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🏢 حقیقت زمینی: ایستگاه سینوپتیک صفی‌آباد (WMO 40794)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SkyAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "فاصله ۳٫۶ کیلومتر از شمس‌آباد (ارتفاع ۸۳ متر) · گزارش رسمی Ogimet/WMO",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (synop != null) {
                val nowSec = System.currentTimeMillis() / 1000L
                val ageMin = ((nowSec - synop.timeUtcEpochSec) / 60L).toInt()
                val isFresh = ageMin <= Tuning.SYNOP_DISPLAY_FRESH_MIN
                val obsTimeIran = ForecastEngine.epochToIran(synop.timeUtcEpochSec)

                Text(
                    text = if (isFresh) "گزارش ساعت $obsTimeIran (${JalaliCalendar.formatNumber(ageMin.toDouble())} دقیقه پیش)"
                           else "⚠️ آخرین گزارش موجود: ساعت $obsTimeIran (${formatAge(ageMin)} پیش) — کهنه است و برای وضعیت فعلی استفاده نمی‌شود",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFresh) TextSecondary else AmberStorm
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("دما: ${JalaliCalendar.formatNumber(synop.tempC)}°C", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("نقطه شبنم: ${JalaliCalendar.formatNumber(synop.dewC)}°C", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("رطوبت نسبی: ${JalaliCalendar.formatNumber(synop.rh)}٪", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val precipText = when {
                    synop.precipMm != null && synop.precipPeriodH != null ->
                        "بارش انباشته: ${JalaliCalendar.formatNumber(synop.precipMm)} mm در ${JalaliCalendar.formatNumber(synop.precipPeriodH)} ساعت منتهی به ساعت $obsTimeIran"
                    synop.precipMm != null && synop.precipPeriodH == null ->
                        "بارش انباشته: ${JalaliCalendar.formatNumber(synop.precipMm)} mm (دوره نامشخص، تا ساعت $obsTimeIran)"
                    else ->
                        "بارش انباشته: گزارش نشده"
                }
                Text(
                    text = precipText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (synop.precipMm != null && synop.precipMm > 0.0) SkyAccent else TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val presentText = when {
                    !isFresh -> "هوای حاضر: نامشخص (گزارش تازه‌ای نیست)"
                    synop.isThunder -> "هوای حاضر: رعدوبرق ⚡"
                    synop.isRainNow -> "هوای حاضر: بارش فعال 🌧️"
                    synop.isRecentPrecip -> "هوای حاضر: بارش در ساعت قبل از گزارش 🌦️"
                    synop.ww != null -> "هوای حاضر: بدون بارش در لحظهٔ گزارش"
                    synop.iX == 2 || synop.iX == 5 -> "هوای حاضر: پدیدهٔ قابل‌گزارشی ثبت نشده"
                    else -> "هوای حاضر: گزارش نشده"
                }
                val presentColor = when {
                    !isFresh || synop.ww == null -> TextSecondary
                    synop.isRainNow || synop.isThunder -> SkyAccent
                    else -> EmeraldOk
                }
                Text(
                    text = presentText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = presentColor
                )
            } else {
                Text(
                    text = "گزارش دیدبانی صفی‌آباد در این نوبت در دسترس نیست.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            if (modelCurrent != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SlateBorder.copy(alpha = 0.3f))
                Text(
                    text = "🌐 خروجی مدل عددی (Current Open-Meteo):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("دما: ${JalaliCalendar.formatNumber(modelCurrent.tempC)}°C", fontSize = 11.sp)
                    Text("نقطه شبنم: ${JalaliCalendar.formatNumber(modelCurrent.dewC)}°C", fontSize = 11.sp)
                    Text("رطوبت: ${JalaliCalendar.formatNumber(modelCurrent.rh)}٪", fontSize = 11.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تندباد: ${JalaliCalendar.formatNumber(modelCurrent.windGustKmh)} km/h", fontSize = 11.sp)
                    Text("باد: ${JalaliCalendar.formatNumber(modelCurrent.windSpeedKmh)} km/h", fontSize = 11.sp)
                    Text("ابر: ${JalaliCalendar.formatNumber(modelCurrent.cloudCover)}٪", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatAge(min: Int): String {
    if (min < 60) {
        return "${JalaliCalendar.formatNumber(min)} دقیقه"
    }
    val h = min / 60
    val m = min % 60
    return if (m == 0) {
        "${JalaliCalendar.formatNumber(h)} ساعت"
    } else {
        "${JalaliCalendar.formatNumber(h)} ساعت و ${JalaliCalendar.formatNumber(m)} دقیقه"
    }
}

@Composable
private fun PatternConvectiveSummaryCard(pattern: PatternSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "⚡ الگوی ۲۴ ساعته ناپایداری و بارش",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SkyAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(pattern.precipSummaryText, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("بارش محتمل ۲۴h: ${JalaliCalendar.formatNumber(pattern.totalPrecip24hLikely)} mm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("بیشینه بارش ۲۴h: ${JalaliCalendar.formatNumber(pattern.totalPrecip24hMax)} mm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SkyAccent)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ساعت اوج بارش: ${pattern.peakPrecipTimeIran}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("ساعت اوج ناپایداری: ${pattern.peakTsTimeIran}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("بیشینه تندباد: ${JalaliCalendar.formatNumber(pattern.maxGust24h)} km/h", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("بیشینه CAPE: ${JalaliCalendar.formatNumber(pattern.maxCape)} J/kg", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SourceHealthCard(sources: List<SourceHealth>, stats: VerificationStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "📊 سلامت منابع داده و کیفیت کالیبراسیون",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SkyAccent
            )

            Spacer(modifier = Modifier.height(10.dp))

            for (sh in sources) {
                val statusColor = when (sh.status) {
                    SourceStatus.OK -> EmeraldOk
                    SourceStatus.PARTIAL -> AmberStorm
                    SourceStatus.FAILED, SourceStatus.UNAVAILABLE -> CoralAlert
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sh.source, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sh.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SlateBorder.copy(alpha = 0.3f))

            Text("🎯 ارزیابی صحت پیش‌بینی و کالیبراسیون MOS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            val calStatusText = stats?.calibrationStatusText ?: if (Calibration.enabled) "جدول کالیبره فعال است (ایستگاه صفی‌آباد 40794)" else "کالیبراسیون غیرفعال"
            Text(
                text = calStatusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (Calibration.enabled) EmeraldOk else TextSecondary
            )

            if (stats != null && stats.totalRecords > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val buckets = listOf(stats.lead6h, stats.lead12h, stats.lead24h)
                    for (b in buckets) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("افق ${b.leadBucket}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SkyAccent)
                            Text("ثبت: ${b.totalForecasts}", fontSize = 9.sp, color = TextSecondary)
                            Text("ارزیابی: ${b.evaluatedForecasts}", fontSize = 9.sp, color = TextSecondary)
                            val brierStr = b.brierScore?.let { JalaliCalendar.formatNumber(it) } ?: "—"
                            Text("Brier: $brierStr", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionCoverageBottomSheetContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = SkyAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🗺️ محدوده دقیق «منطقه» پایش هواشناسی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SkyAccent
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "📍 مرکز مبنا: شهر شمس‌آباد دزفول",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberStorm
                )
                Text(
                    text = "مختصات جغرافیایی: 32.2978N, 48.4283E (ارتفاع ۷۸ متر)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Text(
            text = "🎯 ۱. شعاع پایش مستقیم (۲۰ کیلومتر حول شمس‌آباد):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = SkyAccent
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurface.copy(alpha = 0.6f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("• 🏡 شهر شمس‌آباد (مرکز شبکه پایش)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("• 🏢 ایستگاه سینوپتیک صفی‌آباد (۳٫۶km جنوب‌غرب)", style = MaterialTheme.typography.bodySmall)
            Text("• 🏙️ شهر دزفول و کوی‌های اطراف (~۹٫۵km شمال)", style = MaterialTheme.typography.bodySmall)
            Text("• ✈️ فرودگاه دزفول و پایگاه وحدتی (۱۵٫۵km شمال‌غرب)", style = MaterialTheme.typography.bodySmall)
            Text("• 🏘️ شهر اندیمشک و کوی لور (۱۹km شمال‌غرب - لبه d20)", style = MaterialTheme.typography.bodySmall)
            Text("• 🏭 قطب صنعتی شمس‌آباد، چغامیش، بنه‌گز، شهرک صفی‌آباد و مزارع کشاورزی اطراف", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        Text(
            text = "📡 ۲. شعاع پیش‌هشدار دوربرد (۳۰ تا ۶۰ کیلومتر):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AmberStorm
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurface.copy(alpha = 0.6f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("• 🏞️ گتوند و سردشت (شرق و شمال‌شرق - ۳۰ تا ۴۵km)", style = MaterialTheme.typography.bodySmall)
            Text("• 🏛️ شوش و هفت‌تپه (جنوب‌غرب - ۲۵ تا ۴۰km)", style = MaterialTheme.typography.bodySmall)
            Text("• ⛰️ ارتفاعات شمال دزفول و فتح‌المبین (غرب)", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("متوجه شدم")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SkyObservationBottomSheetContent(
    existingReport: SkyObservationReport?,
    onSubmit: (Bitmap?, String, String, Double?, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var direction by remember { mutableStateOf("بالای سر") }
    var phenomenon by remember { mutableStateOf("") }
    var userTempStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("📷 دیده‌بان آسمان و ثبت مشاهده میدانی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SkyAccent)
        Text("گزارش شما برای ۶۰ دقیقه در کالیبراسیون رادار و پیش‌بینی استفاده می‌شود.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)

        Text("الان چه می‌بینید؟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tuning.USER_PHENOMENA.forEach { p ->
                FilterChip(
                    selected = (phenomenon == p),
                    onClick = { phenomenon = p },
                    label = { Text(p) }
                )
            }
        }

        OutlinedTextField(
            value = direction,
            onValueChange = { direction = it },
            label = { Text("جهت دید (شمال، غرب، بالای سر)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = userTempStr,
            onValueChange = { userTempStr = it },
            label = { Text("دمای فعلی شما (اختیاری)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (photoBitmap != null) "تصویر انتخاب شد ✅" else "انتخاب تصویر آسمان (اختیاری)")
        }

        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap!!.asImageBitmap(),
                contentDescription = "پیش‌نمایش",
                modifier = Modifier
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSubmit(photoBitmap, direction, phenomenon, userTempStr.toDoubleOrNull(), notes)
                },
                enabled = phenomenon.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("ثبت و کالیبره")
            }
            if (existingReport != null) {
                OutlinedButton(onClick = onClear) {
                    Text("حذف گزارش قبلی", color = CoralAlert)
                }
            }
        }
    }
}

@Composable
private fun WeatherAlertBottomSheetContent(
    settings: AlertSettings,
    feedbackMessage: String?,
    onUpdateSettings: (AlertSettings) -> Unit,
    onTriggerAlert: () -> Unit,
    onClearFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🔔 تنظیمات هشدارهای هوشمند شمس‌آباد ۲۰km", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SkyAccent)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("فعال‌سازی اعلان‌های هوشمند")
            Switch(
                checked = settings.isEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) }
            )
        }

        if (feedbackMessage != null) {
            Text("📢 $feedbackMessage", style = MaterialTheme.typography.labelSmall, color = AmberStorm)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "💡 برای دریافت به موقع اعلان‌ها در گوشی‌های شیائومی (HyperOS)، در تنظیمات باتری این اپ گزینه «بدون محدودیت» (No restrictions) را انتخاب کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(10.dp)
            )
        }

        Button(onClick = onTriggerAlert, modifier = Modifier.fillMaxWidth()) {
            Text("تست و ارسال اعلان دستی")
        }
    }
}
