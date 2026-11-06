package com.amin.weather.core

enum class AlertTier(
    val levelName: String,
    val title: String,
    val hexColor: String
) {
    GREEN("green", "آسمان آرام و پایدار", "#10B981"),
    YELLOW("yellow", "احتمال رگبار موضعی و ناپایداری جوی", "#F59E0B"),
    ORANGE("orange", "توده بارشی در راه", "#F97316"),
    RED("red", "هشدار فوری بارش/رعدوبرق در محدوده", "#EF4444")
}

enum class ForecastRange(val key: String, val label: String) {
    HOURLY("hourly", "ساعت به ساعت"),
    H1("h1", "۱ ساعت آینده"),
    H3("h3", "۳ ساعت آینده"),
    H6("h6", "۶ ساعت آینده"),
    H12("h12", "۱۲ ساعت آینده"),
    H24("h24", "۲۴ ساعت آینده")
}

enum class ForecastColumn(val key: String, val label: String, val defaultVisible: Boolean = true) {
    SOURCE("source", "منبع تحلیل", true),
    TEMP("temp", "دما (°C)", true),
    PROB_AREA("prob_area", "احتمال محدوده (%)", true),
    PROB_POINT("prob_point", "احتمال شمس‌آباد (%)", true),
    PRECIP_P50("precip_p50", "بارش محتمل (mm)", true),
    PRECIP_P90("precip_p90", "بیشینه بارش (P90)", true),
    CONV_INDEX("conv_index", "پتانسیل همرفتی", true),
    RAIN_TYPE("rain_type", "نوع پدیده", true),
    WIND_GUST("wind_gust", "تندباد (km/h)", true),
    DEW_POINT("dew_point", "نقطه شبنم (°C)", false),
    VIRGA("virga", "بارش تبخیرشونده (ویرگا)", false),
    CLOUD("cloud", "پوشش ابر (%)", false)
}

data class VerdictInfo(
    val tier: AlertTier,
    val text: String,
    val advisoryNote: String = ""
)

data class NeighbourhoodSummary(
    val pointsWithRainNow: Int = 0,
    val totalPoints: Int = 17,
    val wettestDirectionFa: String? = null
)

data class SourceHealth(
    val source: String,
    val status: SourceStatus,
    val message: String? = null
)

data class ModelCurrent(
    val tempC: Double? = null,
    val rh: Double? = null,
    val dewC: Double? = null,
    val precipMm: Double? = null,
    val weatherCode: Int? = null,
    val cloudCover: Double? = null,
    val windSpeedKmh: Double? = null,
    val windDirDeg: Double? = null,
    val windGustKmh: Double? = null
)

data class PatternSummary(
    val windowStartIran: String,
    val windowEndIran: String,
    val peakPrecipTimeIran: String,
    val peakPrecipMm: Double,
    val peakTsTimeIran: String,
    val peakConvectiveScore: Double,
    val maxCape: Double,
    val totalPrecip24hLikely: Double = 0.0,
    val totalPrecip24hMax: Double = 0.0,
    val maxProb24h: Double = 0.0,
    val maxGust24h: Double = 0.0,
    val precipSummaryText: String = "",
    val convectiveHoursCount: Int = 0,
    val virgaHoursCount: Int = 0
)

data class ForecastRow(
    val timeEpochSec: Long,
    val timeIran: String,
    val forecastSource: String,
    val probArea: Double?,
    val probPoint: Double?,
    val amountP50: Double?,
    val amountP90: Double?,
    val tempC: Double?,
    val tempMinC: Double? = tempC,
    val tempMaxC: Double? = tempC,
    val dewC: Double?,
    val rh: Double?,
    val cloud: Double?,
    val gustKmh: Double?,
    val windSpeedKmh: Double? = null,
    val windDirFromDeg: Double? = null,
    val windDirLabel: String? = null,
    val convScore: Double?,
    val convLabel: String = "پایدار",
    val virgaRisk: Boolean = false,
    val confidence: Int? = null,
    val sources: List<String> = emptyList(),
    val eventLabel: String = "—"
)

data class SkyObservationReport(
    val id: String = "",
    val photoPath: String? = null,
    val direction: String = "بالای سر",
    val phenomenon: String = "ابرهای تیره و جوششی",
    val userTemp: Double? = null,
    val notes: String = "",
    val timestampIran: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isCalibrated: Boolean = false
)

data class ForecastDashboard(
    val location: String = "شعاع ۲۰ کیلومتری شمس‌آباد دزفول",
    val lat: Double = 32.2978,
    val lon: Double = 48.4283,
    val generatedIran: String,
    val verdict: VerdictInfo,
    val radarNowcast: RadarNowcastResult,
    val neighbourhood: NeighbourhoodSummary,
    val synopObs: SynopObs?,
    val modelCurrent: ModelCurrent?,
    val pattern: PatternSummary,
    val ranges: Map<ForecastRange, List<ForecastRow>>,
    val sourceHealth: List<SourceHealth> = emptyList(),
    val verificationStats: VerificationStats? = null,
    val userObservation: SkyObservationReport? = null
)
