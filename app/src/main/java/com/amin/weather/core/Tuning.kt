package com.amin.weather.core

object Tuning {
    // ناحیه
    const val TARGET_RADIUS_KM = 20.0
    const val RADAR_WATCH_RADIUS_KM = 150.0

    // آستانه‌های بارش (mm)
    const val WET_HOUR_MM = 0.2          // ساعت بارانی
    const val WET_3H_MM = 0.3            // پنجره‌ی ۳ ساعته‌ی بارانی (برای ensemble ECMWF)
    const val MODERATE_3H_MM = 2.0
    const val HEAVY_6H_MM = 10.0

    // رادار (dBZ)
    const val DBZ_LIGHT = 15
    const val DBZ_RAIN = 20
    const val DBZ_MODERATE = 30
    const val DBZ_STRONG = 35
    const val DBZ_SEVERE = 45
    const val DBZ_CAP_FOR_RAINRATE = 53  // جلوگیری از تخمین بیش از حد به دلیل تگرگ
    const val MIN_CELL_PIXELS = 4        // حدود ۴ کیلومتر مربع
    const val RADAR_MAX_FRAME_AGE_MIN = 30
    const val TRACK_MATCH_MAX_KM = 25.0  // بیشینه جابه‌جایی سلول در ۱۰ دقیقه
    const val CELL_SPEED_MIN_KMH = 5.0
    const val CELL_SPEED_MAX_KMH = 120.0
    const val ETA_UNCERTAINTY_FRAC = 0.3

    // اصلاحیهٔ دوم
    const val RADAR_FRAMES_USED = 3              // فریم‌های لازم برای ردیابی و کلاتر
    const val RADAR_TILE_CACHE_MAX_AGE_MIN = 180
    const val CLUTTER_FRAMES = 3                 // جایگزین CLUTTER_MIN_FRAMES
    const val CLUTTER_MAX_DBZ_RANGE = 3          // اختلاف بیشینه و کمینهٔ dBZ پیکسل در فریم‌ها
    const val TRACK_FRAME_GAP_MIN_MIN = 5
    const val TRACK_FRAME_GAP_MAX_MIN = 20
    const val POINT_RADIUS_KM = 3.0              // «نقطهٔ» شمس‌آباد برای nowcast
    const val UPWIND_RING_MIN_KM = 50.0
    const val UPWIND_BEARING_MIN = 202.5         // جنوب‌غرب
    const val UPWIND_BEARING_MAX = 337.5         // شمال‌غرب
    const val NEIGH_MIN_VALID_POINTS = 12        // حداقل نقطهٔ معتبر از ۱۷ نقطه
    const val SYNOP_FRESH_SEC = 5400L            // ۹۰ دقیقه
    const val VERIFY_WINDOW_H = 6
    const val VERIFY_MAX_LINES = 5000
    const val YELLOW_MOIST_MIN_PROB = 0.20

    // وزن ترکیب nowcast رادار با مدل برای ساعت ۱، ۲، ۳
    val NOWCAST_WEIGHT = doubleArrayOf(0.70, 0.40, 0.15)

    // وزن ترکیب منابع مدل در احتمال محدوده
    const val W_ENSEMBLE = 0.5
    const val W_NEIGH_ECMWF = 0.3
    const val W_NEIGH_ICON = 0.2

    // محافظ اقلیمی: ماه‌های خشک (۶ تا ۹ = ژوئن تا سپتامبر)
    val DRY_MONTHS = setOf(6, 7, 8, 9)
    const val DRY_MONTH_PROB_CAP = 0.05
    const val DRY_MONTH_STRONG_SIGNAL = 0.30

    // ویرگا
    const val VIRGA_DEW_DEPRESSION_C = 15.0
    const val VIRGA_RH_MAX = 30.0
    const val VIRGA_PROB_FACTOR = 0.6

    // سطوح هشدار (احتمال 0..1)
    const val RED_PROB_HEAVY_6H = 0.50
    const val ORANGE_PROB_WET_6H = 0.60
    const val YELLOW_PROB_WET_12H = 0.30
    const val RED_ETA_MIN = 30
    const val ORANGE_ETA_MIN = 90

    // گزارش کاربر
    const val USER_REPORT_TTL_MIN = 60
    const val USER_REPORT_MAX_PROB = 0.85

    // ورود رطوبت تراز ۸۵۰
    const val MOIST_INFLOW_MIN_KMH = 20.0
    const val MOIST_INFLOW_MIN_RH = 70.0

    // کالیبراسیون (فاز ۶)
    const val CALIBRATION_ASSET = "calibration_40794.json"
    const val CALIB_QM_MAX_RATIO = 2.0   // سقف ضریب تصحیح مقدار در بالاتر از آخرین چندک
    const val CALIB_QM_MIN_RATIO = 0.5
    const val ONDEVICE_MIN_WET_EVENTS = 30

    // فراخوانی منابع
    const val REFRESH_INTERVAL_MIN = 30
    const val SYNOP_MIN_INTERVAL_MIN = 60
    const val HTTP_TIMEOUT_MS = 12_000
}
