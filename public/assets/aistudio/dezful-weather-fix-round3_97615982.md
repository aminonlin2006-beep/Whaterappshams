# اصلاحیهٔ سوم اپ «هواشناسی امین» — ۱۱ اصلاح کوچک ولی مهم

> برای Gemini Flash در Google AI Studio. مخزن: `aminonlin2006-beep/Whaterappshams` (کامیت `912df38`).
> قواعد اصلاحیه‌های قبلی همچنان معتبرند. **همهٔ این اصلاح‌ها را در یک نوبت انجام بده**، چون کوچک‌اند؛ بعد تست‌ها را اجرا کن و گزارش بده.
> پیش از ویرایش هر فایل، نسخهٔ فعلی را بخوان و فقط همان خطوط گفته‌شده را تغییر بده.

---

## ۱. (بحرانی) خطای گردکردن که مقدار بارش را ۱۰ برابر می‌کند — `ForecastEngine.kt`

خط فعلی:
```kotlin
private fun r2(v: Double?): Double? = v?.let { (it * 100.0).roundToInt() / 10.0 }
```
اصلاح:
```kotlin
internal fun r2(v: Double?): Double? = v?.let { (it * 100.0).roundToInt() / 100.0 }
```
(الان ۱٫۲۳ میلی‌متر به‌صورت ۱۲٫۳ نمایش داده می‌شود و همهٔ مقادیر بارش، بازه‌ها، اوج و جمع کل ۱۰ برابرند.)

تست: `r2(1.234) == 1.23`، `r2(12.5) == 12.5`، `r2(null) == null`.

## ۲. (بحرانی) پرچم «ورود رطوبت ۱۲ ساعت آینده» از دیروز خوانده می‌شود — `ForecastEngine.kt`

چون `past_days=1` اضافه شده، اندیس ۰ تا ۱۱ مربوط به دیروز است. اصلاح:
```kotlin
val moistureInflowNext12h = steeringWind?.let { s ->
    s.times.indices.any { k ->
        s.times[k] > nowEpochSec - 1800L && s.times[k] <= nowEpochSec + 12 * 3600L &&
            s.moistureInflow.getOrNull(k) == true
    }
} ?: false
```
تست: `SteeringWind` ساختگی با ۴۸ ساعت از دیروز ۰۰ UTC که فقط ساعت‌های دیروز `moistureInflow=true` دارند → نتیجه `false`.

## ۳. شروع جدول ساعتی وقتی ساعت جاری پیدا نشود — `ForecastEngine.kt`

خط فعلی با `coerceAtLeast(0)` در صورت پیدا نشدن، از ساعت ۰۰ دیروز شروع می‌کند. اصلاح:
```kotlin
var nowHourIndex = baseTimes.indexOfFirst { abs(it - nowEpochSec) < 1800L }
if (nowHourIndex < 0) nowHourIndex = baseTimes.indexOfFirst { it >= nowEpochSec - 1800L }
if (nowHourIndex < 0) throw java.io.IOException("داده‌های مدل برای ساعت جاری موجود نیست")
```

## ۴. جمع‌های «۲۴ ساعت» در خلاصه، روی ۴۸ ساعت و با جمع P90 ساعتی حساب می‌شوند — `ForecastEngine.kt`

در ساخت `PatternSummary`:
```kotlin
val h24 = rangesMap[ForecastRange.H24]?.firstOrNull()
totalPrecip24hLikely = h24?.amountP50 ?: 0.0,
totalPrecip24hMax = h24?.amountP90 ?: 0.0,
maxProb24h = rows.take(24).mapNotNull { it.probArea }.maxOrNull() ?: 0.0,
maxGust24h = rows.take(24).mapNotNull { it.gustKmh }.maxOrNull() ?: 0.0,
```
`peakPrecipMm`/`peakPrecipTime`/`peakSci`/`maxCape` را هم فقط روی ۲۴ ساعت اول حساب کن (`if (step < 24)`). متن `precipSummaryText` به جای `sumPrecipP50` از `h24?.amountP50` استفاده کند. متغیرهای `sumPrecipP50` و `sumPrecipP90` را حذف کن.

## ۵. پیش‌فرض ساختگی باقی‌مانده — `ForecastEngine.kt`

در پتانسیل همرفتی: `val td = dewPoint ?: 10.0` را حذف کن؛ جملهٔ رطوبت فقط وقتی اضافه شود که `dewPoint != null`:
```kotlin
if (dewPoint != null) sc += 15.0 * ((dewPoint - 5.0) / 10.0).coerceIn(0.0, 1.0)
```
در UI (`WeatherDashboardScreen.kt` حدود خط ۱۲۵۵) بارش SYNOP:
`synop.precipMm?.let { "${JalaliCalendar.formatNumber(it)} mm" } ?: "نامشخص"` و اگر `precipPeriodH` موجود است «در X ساعت» کنارش بیاید.

## ۶. سن رادار از فریمی که واقعاً استفاده شده — `RadarClient.kt`

اگر فریم آخر ناقص بود و حذف شد، سن باید از آخرین فریم **کامل** حساب شود:
```kotlin
val usedAgeMin = if (frames.isNotEmpty()) ((nowSec - frames.last().timeEpochSec) / 60).toInt().coerceAtLeast(0) else frameAgeMin
val fetchResult = RadarFetch(frames, usedAgeMin, coverageVerified)
```
و شرط `PARTIAL`/پیام کهنگی هم با `usedAgeMin` باشد.

## ۷. کش پوشش رادار هر ۳ ساعت پاک می‌شود — `RadarClient.kt`

در `cleanTileCache` فایل `coverage.json` را مستثنا کن:
`if (file.isFile && file.name != "coverage.json" && (nowMs - file.lastModified()) > maxAgeMs) file.delete()`

## ۸. «اولین ورود» فقط بین ۵ سلول نزدیک جست‌وجو می‌شود — `RadarAnalyzer.kt`

سلول دورتری که سریع‌تر می‌رسد جا می‌افتد. اصلاح: `firstArrival` را از `parsedCells` (همه) انتخاب کن، نه `topCells`؛ و اگر در `topCells` نبود، به ابتدای فهرست نمایشی اضافه کن:
```kotlin
val firstArrival = parsedCells
    .filter { it.state == CellApproachState.APPROACHING && it.maxDbz >= Tuning.DBZ_RAIN && it.etaMinLow != null }
    .minByOrNull { it.etaMinLow!! }
val shownCells = if (firstArrival != null && firstArrival !in topCells) listOf(firstArrival) + topCells.take(4) else topCells
```
و `cells = shownCells` برگردان.

## ۹. جایگزین شاخص صعود — `OpenMeteoClient.fetchLiftedIndex`

اگر `gfs_seamless` خطا داد یا داده نداشت، یک بار با `gfs_global` تکرار کن (با همان پارسر؛ پسوند کلید را با نام مدل جدید بخوان).

## ۱۰. کش قدیمی: هشدار و فیلدهای تازه — `WeatherRepository.kt`

(الف) در `serializeDashboard` برای هر ردیف این‌ها را هم ذخیره کن: `tempMinC`, `tempMaxC`, و `sources` (آرایهٔ رشته)، و در `parseDashboard` بخوان.
(ب) در `getCachedForecast`، اگر کش بیش از ۲ ساعت قدیمی است، علاوه بر غیرفعال‌کردن رادار، سطح هشدار را دوباره حساب کن تا هشدار راداریِ قدیمی نمایش داده نشود:
```kotlin
val staleRadar = dashboard.radarNowcast.copy(available = false, statusText = "داده‌های رادار به علت قدمت کش (بیش از ۲ ساعت) نمایش داده نمی‌شوند.")
val hourly = dashboard.ranges[ForecastRange.HOURLY] ?: emptyList()
val newVerdict = ForecastEngine.decideTier(staleRadar, null, hourly, null, null, false)
dashboard.copy(radarNowcast = staleRadar, verdict = newVerdict)
```
(ج) سن کش را از `cachedAtMs` داخل JSON بخوان (نه `lastModified` فایل)؛ اگر نبود، از `lastModified`.

## ۱۱. ارزیابی با گزارش‌های ۱۲ ساعته هم کار کند — `VerificationStore.kt`

ایستگاه‌های ایران ممکن است بارش را در برخی ساعت‌ها ۱۲ ساعته گزارش کنند؛ در آن حالت با کد فعلی هیچ ارزیابی‌ای انجام نمی‌شود.
- در `recordStandardWindows` علاوه بر پنجره‌های ۶ ساعته، پنجره‌های **۱۲ ساعته** که پایانشان ۰۶ یا ۱۸ UTC است را هم ثبت کن. کلید: `"$windowEndSec:$periodH:$leadBucket"` و فیلد `periodH` در رکورد.
- در `evaluateWithSynop` شرط را به این تغییر بده: `synop.precipMm != null && synop.precipPeriodH in setOf(6, 12)`؛ و فقط رکوردهایی را به‌روز کن که `windowEndEpochSec == synop.timeUtcEpochSec && periodH == synop.precipPeriodH`.
- رکوردهای قدیمی بدون `periodH` را `6` فرض کن.
- `Tuning.VERIFY_WINDOW_H` را به `val VERIFY_WINDOWS_H = setOf(6, 12)` تغییر بده.
- آمار کارت «کیفیت پیش‌بینی» همچنان به تفکیک افق (۶/۱۲/۲۴) باشد؛ پنجره‌های ۶ و ۱۲ ساعته با هم در یک افق جمع شوند.

تست: یک پنجرهٔ ۱۲ ساعته ثبت‌شده + SYNOP با `precipPeriodH=12` و `precipMm=3.0` در همان زمان → `observedWet=true`. همان با `precipPeriodH=6` → ارزیابی نشود.

---

## پس از انجام

۱. همهٔ تست‌ها را اجرا کن (`./gradlew testDebugUnitTest`) و فهرست پاس/رد را گزارش بده.
۲. با جست‌وجوی متنی گزارش کن که عبارت‌های `/ 10.0`، `?: 10.0`، `?: 0.0)` در محاسبات هواشناسی `ForecastEngine.kt` باقی نمانده‌اند (به‌جز شمارنده‌ها و مقایسه‌های بولی).
