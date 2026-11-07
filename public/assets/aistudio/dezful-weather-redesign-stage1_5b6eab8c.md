# بازطراحی اپ «هواشناسی امین» — مرحلهٔ ۱ از ۴: پاکسازی و ایمنی

> برای Gemini در Google AI Studio. مخزن: `aminonlin2006-beep/Whaterappshams` (کامیت `66bca2f`).
> **فقط همین مرحله را انجام بده.** پیش از ویرایش هر فایل، نسخهٔ فعلی را بخوان و فقط خطوط گفته‌شده را تغییر بده. ظاهر صفحه، متن کارت‌ها و بقیهٔ منطق را دست نزن. در پایان تست‌ها را اجرا کن و گزارش بده (بخش ۱۲).

## نقشهٔ کلی بازطراحی (برای اطلاع؛ مراحل ۲ تا ۴ هنوز انجام نشود)

هدف جدید اپ: فقط پیش‌بینی بارش در دایرهٔ ۲۰ کیلومتری شمس‌آباد، فقط تا ۲۴ ساعت آینده، با تمرکز روی ۶ ساعت اول: باران کی شروع می‌شود، چقدر، کی تمام می‌شود، بازهٔ خشک بعدی کی است.

- **مرحلهٔ ۱ (همین فایل):** رفع ایرادهای ایمنی و پاکسازی، بدون تغییر ساختار.
- **مرحلهٔ ۲:** موتور جدید — تشخیص نوع سامانه (همرفتی/جبهه‌ای/بدون بارش)، خط زمانی ۰ تا ۲۴ ساعت، خروجی شروع/پایان/بازهٔ خشک، حالت «آرام» و «آماده‌باش»، حذف بخش‌های چندروزه.
- **مرحلهٔ ۳:** گزارش‌های METAR ایستگاه‌های اطراف از Ogimet و تصحیح زمان با ایستگاه‌های بالادست.
- **مرحلهٔ ۴:** نمره‌دهی زمان‌محور (خطای زمان شروع و پایان) و بازطراحی صفحه.

قانون‌های ثابت که از همین مرحله رعایت شوند:
1. «نامشخص» هیچ‌وقت «آرام» نیست.
2. هر مشاهده فقط یک چیز را تغییر می‌دهد و فقط تا وقتی تازه است.
3. هیچ مقدار پیش‌فرض ساختگی؛ دادهٔ ناموجود = `null` = «نامشخص».
4. همهٔ ثابت‌ها در `Tuning.kt`.

---

## ۱. شناسهٔ اپ — `app/build.gradle.kts`

```kotlin
applicationId = "com.aistudio.applet.mozirh"
```
را به این تغییر بده:
```kotlin
applicationId = "com.amin.weather"
```
و `versionCode` را به `2` و `versionName` را به `"2.0-stage1"` ببر. **از این به بعد هرگز `applicationId` را تغییر نده.** (اگر عوض شود، APK جدید کنار نسخهٔ قبلی نصب می‌شود و تنظیمات و آمار از دست می‌رود.)

## ۲. پاکسازی مخزن — `.gitignore`

در ریشهٔ مخزن فایل `.gitignore` بساز:
```
.gradle/
build/
app/build/
local.properties
*.apk
*.aab
.idea/
*.iml
captures/
.externalNativeBuild/
.cxx/
```
پوشهٔ `.gradle/` را از مخزن حذف کن (فقط از مخزن؛ این پوشه هنگام بیلد خودکار دوباره ساخته می‌شود).

## ۳. حذف مجوز بی‌مصرف — `AndroidManifest.xml`

این دو خط را حذف کن (عکس آسمان از گالری با Photo Picker انتخاب می‌شود و دوربین لازم نیست):
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

## ۴. تلاش دوباره در کار پس‌زمینه — `WeatherSyncWorker.kt`

الان `refreshForecast()` خطا را پرتاب نمی‌کند و `Result.failure` برمی‌گرداند، پس `Result.retry()` هیچ‌وقت اجرا نمی‌شود. همچنین `catch (Exception)` لغو کوروتین را هم می‌گیرد. بدنهٔ `doWork` را این‌طور کن:

```kotlin
override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val manager = WeatherNotificationManager(applicationContext)
    val settings = manager.loadSettings()
    if (!settings.isEnabled) return@withContext Result.success()

    try {
        val repo = WeatherRepository(applicationContext)
        val dashboard = repo.refreshForecast().getOrNull()
            ?: return@withContext (if (runAttemptCount < 3) Result.retry() else Result.success())

        val alertEval = manager.evaluateDashboard(dashboard, settings)
        if (alertEval.shouldNotify) {
            manager.sendNotificationWithCooldown(alertEval, force = false)
        }
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}
```

## ۵. کم کردن تأخیر رادار از زمان ورود — `RadarAnalyzer.kt`

زمان ورود الان از لحظهٔ ثبت تصویر رادار حساب می‌شود، نه از الان. اگر تصویر ۲۵ دقیقه پیش ثبت شده باشد، «۳۵ دقیقهٔ دیگر» در واقع «۱۰ دقیقهٔ دیگر» است.

در `analyzeNowcast`، داخل حلقهٔ سلول‌ها، درست بعد از محاسبهٔ `tupleEta` و قبل از `parsedCells.add(...)`، این را اضافه کن و در `RadarCellInfo(...)` به‌جای `tupleEta` از `etaFromNow` استفاده کن (برای `state`، `etaMinLow`، `etaMinHigh`، `minDistanceKm`):

```kotlin
// زمان ورود از «الان» حساب شود، نه از زمان تصویر رادار
val etaFromNow = if (tupleEta.state == CellApproachState.APPROACHING &&
    tupleEta.etaLow != null && tupleEta.etaHigh != null) {
    val lo = tupleEta.etaLow - frameAgeMin
    val hi = tupleEta.etaHigh - frameAgeMin
    if (hi <= 0) {
        // احتمالاً تا الان رسیده است
        TupleEta(CellApproachState.APPROACHING, 0, 0, tupleEta.dMinKm)
    } else {
        TupleEta(CellApproachState.APPROACHING, lo.coerceAtLeast(0), hi, tupleEta.dMinKm)
    }
} else tupleEta
```

در ساخت `statusText`:
- برای حالت `firstArrival != null`: اگر `firstArrival.etaMinHigh == 0` بنویس: «رادار: توده بارشی X dBZ احتمالاً هم‌اکنون به محدوده رسیده است.»؛ وگرنه همان متن فعلی.
- در انتهای متن همیشه اضافه کن: ` (تصویر رادار ${frameAgeMin} دقیقه پیش)`.
- جملهٔ «شعاع پایش ۲۰ کیلومتری شمس‌آباد پاک و بدون لکه بارشی است» را به «رادار: در تصویر آخر، لکهٔ بارشی در دایرهٔ ۲۰ کیلومتری دیده نمی‌شود.» تغییر بده.

## ۶. تشخیص رادار «یخ‌زده» — `RadarAnalyzer.kt`

اگر رادارهای ایران به‌روز نشوند، ممکن است همان تصویر قدیمی با زمان جدید تکرار شود. در `analyzeNowcast`، بعد از شرط فعلی «داده نیست / کهنه است» و قبل از فیلتر کلاتر، اضافه کن:

```kotlin
// اگر چند فریم آخر پیکسل‌به‌پیکسل یکسان‌اند و اکوی بارشی دارند، رادار یخ‌زده است
if (frames.size >= 2) {
    val first = frames.first().dbzGrid
    var wetPixels = 0
    for (idx in first.indices) if (first[idx] >= Tuning.DBZ_RAIN) wetPixels++
    val allIdentical = frames.drop(1).all { it.dbzGrid.contentEquals(first) }
    if (allIdentical && wetPixels >= Tuning.RADAR_FROZEN_MIN_WET_PIXELS) {
        return RadarNowcastResult(
            available = false,
            frameAgeMin = fetch.lastFrameAgeMin,
            coverageOk = fetch.coverageVerified,
            maxDbzInTarget = -1,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0,
            statusText = "رادار به‌روز نمی‌شود (${frames.size} تصویر آخر کاملاً یکسان‌اند)؛ از رادار استفاده نشد.",
            coverageVerified = fetch.coverageVerified
        )
    }
}
```

در `Tuning.kt`:
```kotlin
const val RADAR_FROZEN_MIN_WET_PIXELS = 20   // تصاویر خالیِ یکسان طبیعی‌اند؛ فقط تصاویر دارای اکو بررسی شوند
```

(اگر نوع `dbzGrid` آرایهٔ `IntArray` نیست، معادل `contentEquals` همان نوع را به کار ببر.)

## ۷. تناقض رادار با مشاهدهٔ زمینی — `ForecastEngine.kt`

اگر صفی‌آباد یا کاربر همین الان باران گزارش کرده‌اند ولی رادار روی محدوده هیچ اکویی نمی‌بیند، رادار در این نوبت قابل‌اعتماد نیست.

خط ۲۱۵ را از `val` به `var` تغییر بده و بلافاصله بعد از آن اضافه کن:

```kotlin
val synopRainNow = synop != null &&
    (nowEpochSec - synop.timeUtcEpochSec) < Tuning.SYNOP_FRESH_SEC &&
    (synop.isRainNow || synop.isThunder)
val userRainNow = userObservation != null &&
    (nowEpochSec - userObservation.createdAtEpochMs / 1000L) / 60L < Tuning.USER_REPORT_TTL_MIN &&
    userObservation.phenomenon in Tuning.USER_RAIN_PHENOMENA
if (radarNowcast.available && (synopRainNow || userRainNow) &&
    radarNowcast.maxDbzInTarget < Tuning.DBZ_LIGHT) {
    radarNowcast = radarNowcast.copy(
        available = false,
        statusText = "رادار با مشاهدهٔ زمینی تناقض دارد (روی زمین باران گزارش شده ولی رادار اکویی نمی‌بیند)؛ در این نوبت از رادار استفاده نشد."
    )
}
```

## ۸. وضعیت «نامشخص» — هیچ‌وقت سبزِ دروغین

### ۸-الف. `WeatherModels.kt`
به `AlertTier` اضافه کن (بعد از `GREEN`):
```kotlin
UNKNOWN("unknown", "وضعیت زنده نامشخص", "#9CA3AF"),
```

### ۸-ب. `ForecastEngine.decideTier` — جایگزینی بخش «GREEN default»
بخش پایانی (`// GREEN default` تا آخر تابع) را با این جایگزین کن:

```kotlin
val nowSec = System.currentTimeMillis() / 1000L
val synopFresh = synop != null && (nowSec - synop.timeUtcEpochSec) < Tuning.SYNOP_FRESH_SEC
val modelOk = rows.take(12).any { it.probArea != null }

if (!modelOk || (!radarOk && !synopFresh)) {
    val missing = buildList {
        if (!modelOk) add("مدل‌های پیش‌بینی")
        if (!radarOk) add("رادار")
        if (!synopFresh) add("دیدبانی صفی‌آباد")
    }.joinToString("، ")
    return VerdictInfo(
        AlertTier.UNKNOWN,
        "وضعیت زنده نامشخص است: $missing در دسترس نیست.",
        if (modelOk) "پیش‌بینی مدل‌ها برای ۱۲ ساعت آینده نشانهٔ بارش قابل‌توجهی ندارد، اما مشاهدهٔ زنده‌ای برای تأیید نیست."
        else "به‌محض برقراری اتصال دوباره بررسی می‌شود."
    )
}

val liveSources = buildList {
    if (radarOk) add("رادار")
    if (synopFresh) add("دیدبانی صفی‌آباد")
}.joinToString(" و ")
return VerdictInfo(
    AlertTier.GREEN,
    "در دایرهٔ ۲۰ کیلومتری شمس‌آباد بارشی دیده نمی‌شود و مدل‌ها برای ۱۲ ساعت آینده بارش قابل‌توجهی نشان نمی‌دهند.",
    "مبنا: $liveSources و مدل‌های پیش‌بینی."
)
```

### ۸-ج. جاهای دیگر
- `WeatherRepository.parseDashboard`: در `when (tierStr.lowercase())` اضافه کن `"unknown" -> AlertTier.UNKNOWN`.
- `WeatherNotificationManager.evaluateDashboard`: شرط `if (verdict.tier == AlertTier.GREEN)` را به `if (verdict.tier == AlertTier.GREEN || verdict.tier == AlertTier.UNKNOWN)` تغییر بده (برای «نامشخص» اعلان فرستاده نشود؛ در خروجی همان `verdict.tier` را برگردان).
- `WeatherDashboardScreen.kt` (حدود خط ۶۸۴ تا ۶۹۴): در هر دو `when (tier)` شاخهٔ `AlertTier.UNKNOWN` را اضافه کن: رنگ `Color(0xFF9CA3AF)` و آیکن `Icons.Default.HelpOutline`.
- هر `when` دیگری روی `AlertTier` در کل پروژه را پیدا کن و شاخهٔ `UNKNOWN` را اضافه کن تا کامپایل شود.

## ۹. هشدار زرد فقط برای ابرهایی که واقعاً به سمت محدوده می‌آیند — `ForecastEngine.decideTier`

الان هر پیکسل ≥۳۵ dBZ در غرب و جنوب‌غرب (۵۰ تا ۱۵۰ کیلومتری) هشدار زرد می‌دهد، حتی اگر ابر در حال دور شدن باشد. خط:
```kotlin
val isYellowUpwindEcho = radarOk && radar.upwindStrongEcho
```
را به این تغییر بده:
```kotlin
val isYellowUpwindEcho = radarOk && radar.cells.any {
    it.state == CellApproachState.APPROACHING && it.maxDbz >= Tuning.DBZ_STRONG
}
```
و متن آن را به «هشدار زرد: تودهٔ بارشی قوی در فاصلهٔ دور به سمت محدوده در حرکت است.» تغییر بده. (فیلد `upwindStrongEcho` فعلاً در `RadarNowcastResult` بماند.)

## ۱۰. گزارش کاربر: گزینه‌های ثابت + اثر در سطح هشدار

### ۱۰-الف. `Tuning.kt`
```kotlin
// گزارش کاربر — گزینه‌های ثابت (متن آزاد نه؛ «بدون باران» نباید باران حساب شود)
val USER_PHENOMENA = listOf("باران", "رگبار", "رعدوبرق", "تگرگ", "ابری بدون بارش", "صاف", "گردوخاک")
val USER_RAIN_PHENOMENA = setOf("باران", "رگبار", "رعدوبرق", "تگرگ")
```

### ۱۰-ب. `WeatherModels.kt`
در `SkyObservationReport` مقدار پیش‌فرض `phenomenon` را از `"ابرهای تیره و جوششی"` به `""` تغییر بده.

### ۱۰-ج. `WeatherDashboardScreen.kt` (حدود خط ۱۵۳۲ و ۱۵۵۶)
- `var phenomenon by remember { mutableStateOf("ابرهای تیره و جوششی") }` → `mutableStateOf("")`.
- `OutlinedTextField` مربوط به «پدیده مشهود» را حذف کن و به‌جایش یک ردیف چندخطی (`FlowRow`) از `FilterChip` برای هر مقدار `Tuning.USER_PHENOMENA` بگذار؛ با لمس هر چیپ `phenomenon = it`، و چیپ انتخاب‌شده `selected = true`. عنوان بالای آن: «الان چه می‌بینید؟».
- دکمهٔ ثبت فقط وقتی فعال باشد که `phenomenon.isNotBlank()`.
- `FlowRow` به `@OptIn(ExperimentalLayoutApi::class)` روی تابع Composable نیاز دارد (و `FilterChip` در Material3 به `@OptIn(ExperimentalMaterial3Api::class)`).

### ۱۰-د. `ForecastEngine.kt` — بخش «4.7 User Observation Override»
```kotlin
val isUserRain = userObservation.phenomenon.contains("باران") ||
        userObservation.phenomenon.contains("رگبار") ||
        userObservation.phenomenon.contains("رعدوبرق")
```
را با این جایگزین کن:
```kotlin
val isUserRain = userObservation.phenomenon in Tuning.USER_RAIN_PHENOMENA
```

### ۱۰-ه. `ForecastEngine.decideTier` — پارامتر جدید
به امضای تابع پارامتر آخر اضافه کن:
```kotlin
userObservation: SkyObservationReport? = null
```
و در بخش ORANGE، کنار شرط‌های دیگر:
```kotlin
val userAgeMin = userObservation?.let { (System.currentTimeMillis() - it.createdAtEpochMs) / 60000L }
val isOrangeUser = userObservation != null && userAgeMin != null &&
    userAgeMin < Tuning.USER_REPORT_TTL_MIN &&
    userObservation.phenomenon in Tuning.USER_RAIN_PHENOMENA
```
`isOrangeUser` را به شرط کلی `if (isOrangeRadar || isOrangeApproaching || isOrangeSynop || isOrangeProb || isOrangeUser)` اضافه کن و در `when` متن، **اولین** شاخه باشد:
```kotlin
isOrangeUser -> "هشدار نارنجی: بارش در محل شما (گزارش خودتان: ${userObservation?.phenomenon}، $userAgeMin دقیقه پیش)."
```
(چون شرط‌های قرمز قبل از نارنجی بررسی می‌شوند، گزارش کاربر هیچ‌وقت هشدار قرمز را پایین نمی‌آورد.)

در فراخوانی `decideTier` در `buildDashboardData` (حدود خط ۵۹۳) آرگومان `userObservation = userObservation` را اضافه کن.

## ۱۱. تست‌ها — `app/src/test/...`

تست‌های فعلی را به‌روز کن تا با تغییرات کامپایل و پاس شوند، و این تست‌های جدید را اضافه کن:

1. **ETA از الان** (`RadarTest`): سلولی که با `computeApproachEta` زمان ورود ۴۰ تا ۶۰ دقیقه دارد و `frameAgeMin = 25` → نتیجهٔ نهایی ۱۵ تا ۳۵. با `frameAgeMin = 70` → `etaMinLow == 0 && etaMinHigh == 0`. (اگر لازم است، منطق بخش ۵ را در یک تابع `internal fun adjustEtaForFrameAge(t: TupleEta, frameAgeMin: Int): TupleEta` جدا کن تا تست‌پذیر باشد.)
2. **رادار یخ‌زده**: ۳ فریم یکسان با ۵۰ پیکسل ≥۲۰ dBZ → `available == false`. ۳ فریم یکسان کاملاً خالی → `available == true`.
3. **نامشخص**: `decideTier` با رادار ناموجود، `synop = null` و ردیف‌های مدل با احتمال کم → `UNKNOWN` (نه `GREEN`).
4. **سبز فقط با دادهٔ زنده**: رادار موجود و خالی، احتمال کم → `GREEN`.
5. **گزارش کاربر**: `phenomenon = "باران"` و ۱۰ دقیقه پیش، بقیه آرام → `ORANGE`. `phenomenon = "ابری بدون بارش"` → نه نارنجی. گزارش ۷۰ دقیقه پیش → بی‌اثر.
6. **زرد دوردست**: سلول ۴۰ dBZ با `state = NOT_APPROACHING` → زرد نمی‌دهد؛ با `APPROACHING` (و ETA بیشتر از ۹۰ دقیقه) → زرد.

## ۱۲. گزارش پایانی

در پایان این‌ها را گزارش بده:
- فهرست فایل‌های تغییرکرده.
- نتیجهٔ اجرای تست‌ها (تعداد پاس/رد).
- هر جایی که مجبور شدی از این دستورالعمل منحرف شوی و دلیلش.
- بیلد APK debug با نام `amin-weather-2.0-stage1.apk` (داخل مخزن commit نشود).
