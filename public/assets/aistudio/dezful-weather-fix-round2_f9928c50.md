# اصلاحیهٔ دوم اپ «هواشناسی امین» — رفع ۱۴ اشکال بازبینی

> برای مدل Gemini Flash در Google AI Studio.
> مخزن فعلی: `https://github.com/aminonlin2006-beep/Whaterappshams` — پکیج `com.amin.weather`.
> این فایل **ادامهٔ** «دستورالعمل اصلاح کامل» قبلی است. هر جا این فایل چیزی را صریحاً تغییر داد، این فایل اولویت دارد؛ بقیهٔ قواعد دستورالعمل قبلی (مخصوصاً بخش ۰ و بخش ۷) همچنان معتبرند.

> **راهنمای صاحب پروژه:** فایل را به AI Studio بدهید و بنویسید: «این اصلاحیه را بخوان و فقط مرحلهٔ A را اجرا کن.» بعد از بیلد موفق و پاس شدن تست‌ها، بنویسید «مرحلهٔ B» و همین‌طور تا مرحلهٔ E.

---

## قوانین کار (اول بخوان)

۱. **فقط یک مرحله در هر نوبت.** بعد از هر مرحله توقف کن و گزارش بده: فایل‌های تغییرکرده، تست‌های اضافه‌شده، و نتیجهٔ اجرای `./gradlew testDebugUnitTest`.
۲. **پیش از ویرایش هر فایل، نسخهٔ فعلی آن را کامل بخوان.** فقط بخش‌های گفته‌شده را عوض کن؛ رابط کاربری و ساختار بقیه را بازنویسی نکن.
۳. کد مرجع این فایل را **عیناً** پیاده کن، مگر این‌که با نام‌های موجود در پروژه تطبیق لازم باشد. منطق را «بهینه» یا «ساده» نکن.
۴. هیچ عدد جادویی جدید خارج از `Tuning.kt` نگذار. ثابت‌های جدید این فایل را به `Tuning.kt` اضافه کن.
۵. **دادهٔ ناموجود = `null` یا `Double.NaN`، هرگز صفر یا عدد پیش‌فرض.** در UI به صورت «نامشخص».
۶. اگر نمی‌توانی چیزی را دقیق انجام دهی، آن را انجام نده و در گزارش بنویس؛ جایگزین حدسی نساز.

### ثابت‌های جدید برای `Tuning.kt`

```kotlin
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
```

و ثابت `CLUTTER_MIN_FRAMES` و `CLUTTER_MAX_DBZ_STD` را حذف کن (اگر جایی استفاده شده، با دو ثابت جدید جایگزین کن).

---

## مرحلهٔ A — خطاهای بحرانی داده (اشکال‌های ۱، ۳، ۶، ۷)

### A1. حذف شرط CAPE از «نقطهٔ بارانی» (`ForecastEngine.kt`)

در محاسبهٔ `pNeighEcmwf`، شرط فعلی این است:
`if (pr >= Tuning.WET_HOUR_MM || sh >= Tuning.WET_HOUR_MM || cp >= 250.0) wetCount++`
آن را حذف کن. **یک نقطه فقط وقتی بارانی است که `precipitation ≥ WET_HOUR_MM` باشد** (رگبار، یعنی `showers`، خودش جزء `precipitation` است و نباید جدا شمرده شود). همان تابع مشترک زیر را برای ECMWF و ICON به کار ببر:

```kotlin
/** سهم نقاط بارانی در ساعت gIdx؛ اگر نقاط معتبر کمتر از حد بود null */
private fun wetFraction(grid: ModelGrid?, gIdx: Int): Double? {
    if (grid == null || gIdx < 0) return null
    var valid = 0; var wet = 0
    for (p in 0 until grid.pointCount) {
        val v = grid.precip[p].getOrNull(gIdx) ?: continue
        if (v.isNaN()) continue
        valid++
        if (v >= Tuning.WET_HOUR_MM) wet++
    }
    return if (valid >= Tuning.NEIGH_MIN_VALID_POINTS) wet.toDouble() / valid else null
}
```

### A2. مقدار ناموجود = NaN (`OpenMeteoClient.kt`)

- در `tryFetchNeighbourhood`، به جای `pArr?.optDouble(h, 0.0) ?: 0.0` بنویس:
  `if (pArr == null || pArr.isNull(h)) Double.NaN else pArr.optDouble(h, Double.NaN)` — همین برای `showers`, `cape`, `cloud`.
- `parseDoubleArray` را حذف کن و همه جا از `parseNullableDoubleArray` استفاده کن (خروجی `DoubleArray?` با NaN برای خانه‌های خالی). فیلدهای `CenterDetail` را به `DoubleArray?` تبدیل کن.
- `parseIntArray`: خانهٔ خالی = `-1` (و در مصرف، `-1` یعنی نامعلوم).
- به درخواست‌های `fetchNeighbourhood` و `fetchCenterDetail` پارامتر `&past_days=1` اضافه کن (برای پنجره‌های ۶ ساعتهٔ کالیبره در مرحلهٔ D). موتور همه جا اندیس را **با زمان** پیدا می‌کند، پس این تغییر چیزی را نمی‌شکند؛ ولی بررسی کن که هیچ‌جا «اندیس ۰ = اکنون» فرض نشده باشد.
- در `fetchSteeringWind` و `fetchEnsemble`: اگر بدنهٔ پاسخ `"error":true` داشت، `FAILED` با `reason` برگردان.

### A3. حذف همهٔ پیش‌فرض‌های ساختگی (`ForecastEngine.kt`)

یک کمک‌تابع اضافه کن و همهٔ خواندن‌ها را با آن انجام بده:

```kotlin
private fun DoubleArray?.at(i: Int): Double? = this?.getOrNull(i)?.takeIf { !it.isNaN() }
```

سپس این پیش‌فرض‌ها را حذف کن (هر کدام `null` بماند):
`rh ?: 50.0`، `temp ?: 24.0`، `dpDepression = ... else 10.0`، `cloudMid ?: 0.0`، `td ?: 10.0`، `windDir ?: 270.0`، `cin ?: 0.0`، `centerRawPrecip ?: 0.0`.

- `dpDepression` فقط وقتی حساب شود که `temp` و `dewPoint` هر دو موجودند؛ وگرنه `null`. نقطهٔ شبنم از `calculateDewPoint` فقط وقتی ساخته شود که `temp` و `rh` واقعی موجود باشند.
- ویرگا فقط وقتی بررسی شود که `dpDepression` و `rh` هر دو غیر `null` باشند.
- `windDirLabel = windDir?.let { GeoUtils.dir16Fa(it) }` (نوع را `String?` کن).
- در `ForecastRow`: `probArea = pArea?.let { r1(it * 100.0) }` و `probPoint = pPoint?.let { r1(it * 100.0) }` — **نه** `(pArea ?: 0.0)`.
- `amountP50`/`amountP90` اگر هیچ عضوی موجود نبود `null` باشند.
- در `pattern` و `NeighbourhoodSummary` هم مقادیر ناموجود `null` یا «—» باشند، نه صفر. `totalPoints` را از تعداد واقعی نقاط معتبر بگیر، نه عدد ثابت ۱۷.

**UI (`WeatherDashboardScreen.kt`):** همه جا که `?.toInt() ?: 0` برای احتمال یا مقدار بارش آمده (مثلاً خطوط حدود ۸۸۱–۹۸۲)، به این شکل تغییر بده:
`row.probArea?.let { "${it.toInt()}٪" } ?: "نامشخص"` — و منطق رنگ/«isRain» برای `null` حالت خاکستری «نامشخص» بگیرد.

### A4. حذف وابستگی کل جدول به یک درخواست (`OpenMeteoClient.kt` + `ForecastEngine.kt`)

۱. `fetchCenterDetail` فقط **یک مدل** بخواهد: `models=ecmwf_ifs`؛ اگر خطا یا همهٔ مقادیر `precipitation` خالی بود، دوباره با `ecmwf_ifs025`. پارسر با `getHourlyKey` (هم کلید بی‌پسوند، هم با پسوند) کار کند.
۲. تابع جدید `fetchLiftedIndex(): SourceResult<Pair<LongArray, DoubleArray>>` با `models=gfs_seamless&hourly=lifted_index` و جایگزین `gfs_global`. شکست آن فقط `li = null` می‌کند و وضعیت منبع `PARTIAL` می‌شود.
۳. مقدار مرکز ICON را از **نقطهٔ ۰ شبکهٔ همسایگی ICON** بخوان (`iconGrid.precip[0]`)؛ درخواست جداگانه لازم نیست.
۴. محور زمان جدول ساعتی را از اولین منبع موجود بساز:

```kotlin
val baseTimes: LongArray = centerDetail?.times
    ?: ecmwfGrid?.times ?: iconGrid?.times ?: ensIcon?.times ?: ensEcmwf?.times
    ?: throw java.io.IOException("هیچ منبع مدل در دسترس نیست")
```
(این استثنا را `WeatherRepository.refreshForecast` می‌گیرد و کش را نشان می‌دهد — رفتار فعلی درست است.)
۵. در هر ساعت، اندیس هر منبع را جداگانه با زمان پیدا کن (`indexOfFirst { abs(it - hEpoch) < 1800 }`)، نه با `idx` مشترک. این شامل `steeringWind.moistureInflow` هم می‌شود (الان با `idx` جدول مرکز خوانده می‌شود).
۶. `buildBlock`: اگر `rows` خالی بود `null` برگردان و آن بازه را در `rangesMap` نگذار. **هرگز `rows.first()` روی فهرست خالی صدا زده نشود.**

### A5. بازنویسی پارسر SYNOP به شکل موقعیتی (`SynopClient.kt`)

اشکال فعلی: حلقه هر گروه پنج‌رقمی را که دو رقم اولش شبیه `iRiX` باشد به‌عنوان `iRiXhVV` می‌پذیرد. گروه ابر و باد `Nddff` (مثلاً `31205`) جای آن را می‌گیرد و بارش در هوای نیمه‌ابری صفر یا گم می‌شود. همچنین گروه `11970` به‌عنوان دما (−۹۷ درجه) خوانده می‌شود اگر گروه دما نباشد. تابع `parseSynopLine` را **کامل** با این جایگزین کن:

```kotlin
fun parseSynopLine(line: String): SynopObs? {
    val head = line.split(",", limit = 7)
    if (head.size < 7) return null
    val y = head[1].trim().toIntOrNull() ?: return null
    val mo = head[2].trim().toIntOrNull() ?: return null
    val d = head[3].trim().toIntOrNull() ?: return null
    val hh = head[4].trim().toIntOrNull() ?: return null
    val mi = head[5].trim().toIntOrNull() ?: return null
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(y, mo - 1, d, hh, mi, 0)
    }
    val timeSec = cal.timeInMillis / 1000

    val toks = head[6].replace("=", " ").trim().split(Regex("\\s+"))
    if (toks.size < 5 || toks[0] != "AAXX" || toks.any { it == "NIL" }) return null
    // toks[1]=YYGGiw  toks[2]=IIiii  toks[3]=iRiXhVV  toks[4]=Nddff
    val irix = toks[3]
    if (irix.length != 5) return null
    val iR = irix[0].digitToIntOrNull()
    val iX = irix[1].digitToIntOrNull()

    var i = 5
    if (toks[4].length == 5 && toks[4].substring(3, 5) == "99" && i < toks.size && toks[i].startsWith("00")) i++  // 00fff

    var section = 1
    var tempC: Double? = null; var dewC: Double? = null; var rh: Double? = null
    var p1: Pair<Double?, Int?>? = null; var p3: Pair<Double?, Int?>? = null
    var ww: Int? = null

    while (i < toks.size) {
        val g = toks[i]; i++
        if (g == "333") { section = 3; continue }
        if (g == "555" || g == "444" || g.startsWith("222")) { section = 9; continue }
        if (section == 9 || g.length != 5) continue
        if (section == 1) {
            when (g[0]) {
                '1' -> if (g[1] == '0' || g[1] == '1') g.substring(2).toIntOrNull()?.let {
                    tempC = (if (g[1] == '0') it else -it) / 10.0 }
                '2' -> when (g[1]) {
                    '0', '1' -> g.substring(2).toIntOrNull()?.let { dewC = (if (g[1] == '0') it else -it) / 10.0 }
                    '9' -> g.substring(2).toIntOrNull()?.let { rh = it.toDouble() }
                }
                '6' -> p1 = parseGroup6(g)
                '7' -> if (iX == 1 || iX == 4 || iX == 7) ww = g.substring(1, 3).toIntOrNull()
            }
        } else if (section == 3 && g[0] == '6') {
            p3 = parseGroup6(g)
        }
    }

    val (precipMm, periodH) = when (iR) {
        0 -> (p1 ?: p3) ?: Pair(null, null)
        1 -> p1 ?: Pair(null, null)
        2 -> p3 ?: Pair(null, null)
        3 -> Pair(0.0, null)      // نباریده؛ دوره در گزارش نیامده
        else -> Pair(null, null)  // 4 یا نامعلوم: اندازه‌گیری نشده
    }

    val code = ww
    val isAuto = iX == 7          // جدول wawa
    val isRainNow = code != null && if (isAuto) (code in 40..69 || code in 80..89)
                                   else (code in 50..69 || code in 80..84 || code in 91..99)
    val isThunder = code != null && if (isAuto) code in 90..99 else (code in 91..99 || code == 17)
    val isRecentPrecip = code != null && !isAuto && code in 20..29

    if (rh == null && tempC != null && dewC != null) {
        val e = 6.112 * exp(17.67 * tempC!! / (tempC!! + 243.5))
        val ew = 6.112 * exp(17.67 * dewC!! / (dewC!! + 243.5))
        rh = (ew / e * 100.0).coerceIn(0.0, 100.0)
    }
    return SynopObs(timeSec, tempC, dewC, rh, precipMm, periodH, ww, isRainNow, isThunder, isRecentPrecip)
}
```

در `parseGroup6`، مقدار `rrr` بزرگ‌تر از ۹۸۸ و کمتر از ۹۹۰ (یعنی `989`) را `null` برگردان و `"///"` را هم `null`.

**کش فایلی SYNOP:** آخرین `SynopObs` و زمان دریافت را در `filesDir/synop_cache.json` ذخیره کن و در شروع `fetchLatestSynop` از آن بخوان، تا اجرای پس‌زمینه (که در پروسهٔ تازه اجرا می‌شود) هم قاعدهٔ «حداکثر یک بار در ساعت» را رعایت کند.

### تست‌های مرحلهٔ A (به `SynopClientTest.kt` و یک فایل تازه `NullHandlingTest.kt` اضافه کن)

| ورودی | انتظار |
|---|---|
| `40794,2026,11,06,09,00,AAXX 06091 40794 11970 81205 10183 20155 30012 40098 52010 60052 76166 83530=` | دما 18.3، شبنم 15.5، بارش 5.0 در 12 ساعت، ww=61، `isRainNow` |
| `40794,2026,11,06,12,00,AAXX 06121 40794 11970 31205 10183 20155 30012 40198 52010 60082 78062 83530=` (**نیمه‌ابری**) | `iR=1`، بارش **8.0** در 12 ساعت، ww=80، `isRainNow=true` |
| `40794,2026,11,06,12,00,AAXX 06121 40794 11970 31205 20155 60082=` | `tempC == null` (نه −97)، بارش 8.0 |
| `40794,2026,11,06,15,00,AAXX 06151 40794 32970 31205 10221 20101=` | بارش 0.0، دوره `null` |
| `40794,2026,11,06,18,00,AAXX 06181 40794 21970 41208 10150 20120 333 60031=` | `iR=2`، بارش 3.0 در 6 ساعت |
| `40794,2026,11,06,21,00,AAXX 06211 40794 41/// 80000 10221=` | بارش `null` |
| `wetFraction` با ۱۷ نقطه که ۶ تای آن NaN است | `null` (کمتر از ۱۲ نقطهٔ معتبر) |
| `wetFraction` با ۱۷ نقطهٔ معتبر که ۳ تا ≥ 0.2 | `3/17` |
| ردیف با `pArea == null` | `probArea == null` |

---

## مرحلهٔ B — رادار درست و به‌روز (اشکال‌های ۴، ۵، ۱۱، ۱۳)

### B1. دریافت فریم‌ها با کش دیسکی (`RadarClient.kt`)

- فقط `Tuning.RADAR_FRAMES_USED` (۳) فریم آخر را بگیر، نه ۶.
- هر کاشی را در `cacheDir/radar/` با نام `"${path.trim('/').replace('/', '_')}_${tx}_${ty}.png"` ذخیره کن. اگر فایل موجود بود، از دیسک بخوان. فایل‌های قدیمی‌تر از `RADAR_TILE_CACHE_MAX_AGE_MIN` را در هر اجرا پاک کن. (فریم‌های رادار تغییر نمی‌کنند؛ پس در هر به‌روزرسانی فقط کاشی‌های فریم‌های تازه دانلود می‌شوند.)
- اگر **هر کدام** از ۴ کاشی یک فریم دانلود یا رمزگشایی نشد، آن فریم را دور بریز (خانه‌های آن را `-1` = «بی‌باران» فرض نکن). اگر فریم آخر کامل نبود → `PARTIAL` با پیام «کاشی رادار ناقص است» و `frames` بدون آن فریم.
- نتیجهٔ کاشی پوشش را با زمان در `cacheDir/radar/coverage.json` ذخیره کن (۲۴ ساعت). اگر دریافت پوشش شکست خورد و کش هم نبود، فیلد جدید `coverageVerified = false` را در خروجی بگذار (رادار قابل استفاده است ولی در UI بنویسد «پوشش رادار تایید نشده»).
- خروجی را به `SourceResult<RadarFetch>` تغییر بده:
  `data class RadarFetch(val frames: List<RadarFrame>, val lastFrameAgeMin: Int, val coverageVerified: Boolean)`.

### B2. بازنویسی `RadarAnalyzer.analyzeNowcast`

امضای جدید:
```kotlin
fun analyzeNowcast(fetch: RadarFetch?, steering: SteeringWind?, modelDryNow: Boolean?, nowSec: Long): RadarNowcastResult
```
`modelDryNow` = همهٔ نقاط معتبر شبکهٔ ECMWF در ساعت جاری بارش کمتر از `WET_HOUR_MM` دارند (`null` اگر شبکه نیست).

**مراحل به ترتیب:**

۱. **کهنگی:** اگر `fetch == null` یا فریمی نیست یا `lastFrameAgeMin > RADAR_MAX_FRAME_AGE_MIN` → `available = false` با متن «رادار به‌روز نیست (عمر فریم: X دقیقه)». **در این حالت هیچ سلول، ETA یا آماری برنگردان.**

۲. **پیش‌محاسبهٔ فاصله و سمت هر پیکسل** (یک بار، در `lazy` داخل `object`): آرایه‌های `distKm[512*512]` و `bearing[512*512]` با فرمول فعلی. بعد از آن در حلقه‌ها از `haversineKm` استفاده نکن.

۳. **فیلتر کلاتر:** اگر حداقل `CLUTTER_FRAMES` فریم داری و `modelDryNow == true`: پیکسلی که در همهٔ آن فریم‌ها `dBZ ≥ DBZ_RAIN` است و `max − min ≤ CLUTTER_MAX_DBZ_RANGE` → در کپی فریم آخر `-1` شود. تعداد پیکسل‌های حذف‌شده را در `statusText` بیاور.

۴. **فقط پیکسل‌های `distKm ≤ RADAR_WATCH_RADIUS_KM`** وارد شناسایی سلول شوند.

۵. **آمار هدف:** مثل الان، به‌علاوه‌ی:
   - `maxDbzNearPoint` = بیشینهٔ dBZ در `distKm ≤ POINT_RADIUS_KM`.
   - `upwindStrongEcho` = آیا پیکسلی با `dBZ ≥ DBZ_STRONG` و `UPWIND_RING_MIN_KM ≤ distKm ≤ RADAR_WATCH_RADIUS_KM` و `UPWIND_BEARING_MIN ≤ bearing ≤ UPWIND_BEARING_MAX` وجود دارد.

۶. **شناسایی سلول** را به یک تابع جدا ببر تا روی هر فریم قابل اجرا باشد:
```kotlin
data class RawCell(val xKm: Double, val yKm: Double, val pixels: Int, val maxDbz: Int, val radiusKm: Double)
fun detectCells(grid: IntArray): List<RawCell>   // همان BFS فعلی + مرکز ثقل وزن‌دار با Z + فیلتر فاصله‌ی ۱۵۰ km
```

۷. **ردیابی حرکت (جدید):**
```kotlin
// cur = سلول‌های فریم آخر، prev = سلول‌های فریم قبلی، dtMin = اختلاف زمان دو فریم به دقیقه
fun matchVelocity(c: RawCell, prev: List<RawCell>, dtMin: Double): Pair<Double, Double>? {
    if (dtMin < Tuning.TRACK_FRAME_GAP_MIN_MIN || dtMin > Tuning.TRACK_FRAME_GAP_MAX_MIN) return null
    val maxMove = Tuning.TRACK_MATCH_MAX_KM * dtMin / 10.0
    val best = prev.minByOrNull { hypot(it.xKm - c.xKm, it.yKm - c.yKm) } ?: return null
    val d = hypot(best.xKm - c.xKm, best.yKm - c.yKm)
    if (d > maxMove) return null
    val dtH = dtMin / 60.0
    val vx = (c.xKm - best.xKm) / dtH; val vy = (c.yKm - best.yKm) / dtH
    val spd = hypot(vx, vy)
    return if (spd in Tuning.CELL_SPEED_MIN_KMH..Tuning.CELL_SPEED_MAX_KMH) Pair(vx, vy) else null
}
```
   - اگر سه فریم داری: سرعت را از (آخر، قبلی) و (قبلی، دو‌تا‌قبل) حساب کن (برای دومی، سلول قبلی متناظر را با همان تابع پیدا کن) و اگر هر دو موجود بودند میانگین بگیر. `motionSource = "radar_track"`.
   - اگر جفت نشد: باد هدایت‌کنندهٔ **ساعت جاری**: `val sIdx = steering.times.indexOfLast { it <= nowSec }` (اگر −۱ بود، `null`). `motionSource = "steering_wind"`.
   - اگر هیچ‌کدام نبود: **حرکت نامعلوم**. به `CellApproachState` مقدار `UNKNOWN_MOTION` اضافه کن؛ ETA و `minDistanceKm` را `null` بگذار. **مقدار پیش‌فرض ۲۵ km/h و ۶۰ درجه را کامل حذف کن.**

۸. `computeApproachEta` همان بماند (درست است). فقط وقتی `INSIDE_NOW` است `etaLow = etaHigh = 0`.

۹. `firstArrival` = نزدیک‌ترین سلول با `state == APPROACHING`، `maxDbz ≥ DBZ_RAIN` و **کمترین `etaLow`** (نه کمترین فاصله).

۱۰. خروجی `RadarNowcastResult` این فیلدهای تازه را داشته باشد: `maxDbzNearPoint: Int`، `upwindStrongEcho: Boolean`، `coverageVerified: Boolean`، `clutterPixelsRemoved: Int`. در `RadarCellInfo` فیلدهای `etaMinLow`, `etaMinHigh`, `minDistanceKm`, `speedKmh`, `moveDirectionToDeg` را nullable کن و ویژگی‌های `etaLowMin`/`etaHighMin` که `?: 0` برمی‌گردانند را **حذف کن** (هر جا استفاده شده، `null` را صریح بررسی کن).

### B3. استفاده در موتور و هشدار

- در `ForecastEngine`، nowcast فقط وقتی `radarNowcast.available == true` اعمال شود (چون حالا رادار کهنه `available=false` است، شرط `frameAgeMin` جداگانه لازم نیست ولی ضرری ندارد).
- `pNowPoint` (احتمال نقطه):
  `if (step == 0 && radar.maxDbzNearPoint >= DBZ_RAIN) 0.9 else 0.6 * pNow`.
- `decideTier`: در ابتدای تابع `val radarOk = radar.available`؛ همهٔ شرط‌های راداری قرمز/نارنجی/زرد با `radarOk &&` شروع شوند. ETA فقط وقتی `firstArrival.etaMinLow != null`.
- `WeatherRepository.getCachedForecast`: اگر کش بیش از ۲ ساعت قدیمی است، علاوه بر غیرفعال‌کردن رادار، `verdict` را هم دوباره با `decideTier` و رادارِ غیرفعال حساب کن تا هشدار راداری قدیمی نشان داده نشود.

### تست‌های مرحلهٔ B (`RadarTest.kt`)

| آزمون | انتظار |
|---|---|
| `analyzeNowcast` با `lastFrameAgeMin = 45` | `available = false`، `cells` خالی |
| شبکهٔ ساختگی: یک بلوک ۵×۵ پیکسل ۴۰ dBZ در فریم قبلی، همان بلوک ۶ پیکسل جابه‌جا به شرق در فریم آخر، فاصلهٔ ۱۰ دقیقه | سرعت ≈ ۳۷ km/h (±۳)، جهت ≈ ۹۰ درجه (±۵)، `motionSource = "radar_track"` |
| همان سلول بدون فریم قبلی و بدون `steering` | `state = UNKNOWN_MOTION`، `etaMinLow == null` |
| پیکسل ثابت ۳۰ dBZ در ۳ فریم + `modelDryNow = true` | حذف به‌عنوان کلاتر |
| همان پیکسل + `modelDryNow = false` | حذف **نشود** |
| `decideTier` با `available = false` و `maxDbzInTarget = 50` | سطح **سبز** (یا سطح مدل)، نه قرمز |

(برای جابه‌جایی ۶ پیکسلی: هر پیکسل ≈ ۱٫۰۳ km، پس ۶٫۲ km در ۱۰ دقیقه ≈ ۳۷ km/h.)

---

## مرحلهٔ C — مقدار بارش، بازه‌ها و سطح هشدار (اشکال‌های ۸، ۹، ۱۰)

### C1. مجموعهٔ اعضا برای مقدار ساعتی

برای هر ساعت h:

```kotlin
val members = ArrayList<Double>()
// ICON EPS: مقدار ساعتی هر عضو
ensIcon?.let { e -> val k = e.idxOf(hEpoch); if (k >= 0) e.members.forEach { m -> m.at(k)?.let(members::add) } }
// ECMWF ENS: مجموع ۳ ساعت منتهی به h تقسیم بر ۳ (چون گام اصلی آن ۳ ساعتی است)
ensEcmwf?.let { e -> val k = e.idxOf(hEpoch); if (k >= 2) e.members.forEach { m ->
    val a = m.at(k); val b = m.at(k - 1); val c = m.at(k - 2)
    if (a != null && b != null && c != null) members.add((a + b + c) / 3.0) } }
// ECMWF HRES مرکز (کالیبره در مرحلهٔ D) و ICON مرکز (نقطهٔ ۰ شبکهٔ ICON)
hresCenter?.let(members::add); iconCenter?.let(members::add)

val p50 = if (members.isEmpty()) null else percentile(members, 0.5)
var p90 = if (members.isEmpty()) null else percentile(members, 0.9)
val hresNeighMax = (0 until (ecmwfGrid?.pointCount ?: 0)).mapNotNull { ecmwfGrid!!.precip[it].at(gIdx) }.maxOrNull()
if (p90 != null && hresNeighMax != null) p90 = max(p90, hresNeighMax)
```

`percentile` با درون‌یابی خطی روی فهرست مرتب: `pos = q * (n − 1)`.

### C2. nowcast مقدار — فقط وقتی رادار اطلاعات دارد

```kotlin
if (step < 3 && radar.available && pNow != null && p50 != null) {
    val w = Tuning.NOWCAST_WEIGHT[step]
    val amountNow: Double = when {
        radar.wetFractionInTarget >= 0.05 && step == 0 -> radar.rainRateInTargetMmH
        arrival != null && arrival.etaMinLow != null && arrival.speedKmh != null &&
            arrival.etaMinLow <= (step + 1) * 60 ->
            RadarAnalyzer.dbzToRainRate(arrival.maxDbz) *
                min(1.0, 2.0 * arrival.radiusKm / max(Tuning.CELL_SPEED_MIN_KMH, arrival.speedKmh))
        else -> 0.0
    }
    p50 = w * amountNow * pNow + (1 - w) * p50
    p90 = max(p90 ?: 0.0, amountNow)
}
```
(یعنی اگر `pNow == null` بود، مقدار مدل دست نخورده می‌ماند.)

### C3. بازه‌ها (H1…H24) از مجموع اعضا

تابع جدید:

```kotlin
data class BlockStats(val pWet: Double?, val p50: Double?, val p90: Double?, val pHeavy6h: Double?)

/** مجموع بارش هر عضو در ساعت‌های (startEpoch, endEpoch] */
fun blockStats(ensList: List<EnsembleSet?>, startEpoch: Long, endEpoch: Long, wetMm: Double, heavyMm: Double): BlockStats {
    val sums = ArrayList<Double>()
    for (e in ensList) {
        if (e == null) continue
        val idx = e.times.indices.filter { e.times[it] > startEpoch && e.times[it] <= endEpoch }
        if (idx.isEmpty()) continue
        for (m in e.members) {
            var s = 0.0; var ok = true
            for (k in idx) { val v = m.getOrNull(k); if (v == null || v.isNaN()) { ok = false; break }; s += v }
            if (ok) sums.add(s)
        }
    }
    if (sums.isEmpty()) return BlockStats(null, null, null, null)
    sums.sort()
    return BlockStats(
        pWet = sums.count { it >= wetMm }.toDouble() / sums.size,
        p50 = percentile(sums, 0.5), p90 = percentile(sums, 0.9),
        pHeavy6h = sums.count { it >= heavyMm }.toDouble() / sums.size)
}
```

در `buildBlock(count)`:
- `start = rows.first().timeEpochSec − 3600`, `end = rows[count−1].timeEpochSec`.
- `probArea = max(blockStats.pWet, بیشینهٔ Parea ساعت‌های بازه)` (هر دو کسر ۰..۱؛ در خروجی ×۱۰۰). اگر هر دو `null` → `null`.
- `amountP50 = blockStats.p50` ؛ `amountP90 = max(blockStats.p90, جمع p50 ساعتی)` — **دیگر جمع P90های ساعتی نه.** در صورت نبود ensemble، `amountP50 = جمع p50 ساعتی` و `amountP90 = null`.
- به `ForecastRow` دو فیلد `tempMinC: Double?` و `tempMaxC: Double?` اضافه کن؛ در ردیف ساعتی هر دو برابر `tempC`؛ در بازه، کمینه و بیشینهٔ واقعی. UI بازه‌ها «کمینه/بیشینه» نشان دهد.
- باد بازه: بیشینهٔ تندباد؛ جهت غالب = میانگین برداری جهت‌ها (`atan2` میانگین سینوس و کسینوس). اگر داده نبود `null`.

### C4. شرط‌های جاافتادهٔ `decideTier`

امضا را عوض کن: `decideTier(radar, synop, rows, block6: BlockStats?, block12: BlockStats?, moistureInflowNext12h: Boolean)`.
- **قرمز**، شرط اضافه: `block6?.pHeavy6h != null && block6.pHeavy6h >= Tuning.RED_PROB_HEAVY_6H` (با `heavyMm = Tuning.HEAVY_6H_MM`). متن: «X٪ اعضای پیش‌بینی گروهی بیش از ۱۰ میلی‌متر در ۶ ساعت آینده می‌دهند.»
- **نارنجی**، شرط احتمال: به جای بیشینهٔ ساعتی، `Pblock(6h)` از C3 با آستانهٔ `ORANGE_PROB_WET_6H`.
- **زرد**: `Pblock(12h) ≥ YELLOW_PROB_WET_12H`؛ یا `moistureInflowNext12h && Pblock(12h) ≥ YELLOW_MOIST_MIN_PROB`؛ یا `radarOk && radar.upwindStrongEcho` (متن: «پژواک قوی در غرب/جنوب‌غرب در فاصلهٔ ۵۰ تا ۱۵۰ کیلومتر»)؛ بقیهٔ شرط‌های فعلی بمانند.
- پارامتر بی‌استفادهٔ `ensEcmwf` را حذف کن.
- واحدها: داخل `decideTier` همه چیز را کسر ۰..۱ مقایسه کن؛ اگر از `row.probArea` (درصد) استفاده می‌کنی، صریحاً `/ 100.0` کن.

### تست‌های مرحلهٔ C (`ForecastEngineTest.kt`)

| آزمون | انتظار |
|---|---|
| `percentile([1,2,3,4], 0.5)` | 2.5 |
| `blockStats`: ۱۰ عضو با مجموع ۰، ۰، ۰، ۰، ۰، ۰، ۰٫۵، ۱، ۱۲، ۱۵ | `pWet = 0.4`، `pHeavy6h = 0.2`، `p50 = 0.0` |
| `decideTier` با `block6.pHeavy6h = 0.6` و رادار خالی | قرمز |
| `decideTier` با `moistureInflow = true` و `Pblock12 = 0.25` | زرد |
| ساعت ۰ با `radar.available = true` ولی `pNow = null` | `p50` برابر مقدار مدل (بدون کاهش) |

---

## مرحلهٔ D — کالیبراسیون و ارزیابی (اشکال‌های ۲، ۱۲، ۱۴)

### D1. اعتبارسنجی کامل فایل (`Calibration.kt`)

به `loadFromJson` اضافه کن (هر کدام نقض شد → `enabled = false`):
- `model` باید `ecmwf_ifs` یا `ecmwf_ifs025` باشد.
- `prob_table.x` اکیداً صعودی، از ۰ تا ۱؛ `y` غیرنزولی و بین ۰ و ۱.
- `amount_qm.fc` اکیداً صعودی؛ `obs` غیرنزولی؛ اگر `amount_qm` نبود، فقط کالیبرهٔ مقدار خاموش شود (`qmEnabled = false`)، نه کل جدول.
- `months` را بخوان و تابع `fun appliesTo(month: Int) = enabled && month in months` بساز.
- `windowHours` بین ۱ و ۲۴؛ `wetThresholdMm` را `public` کن.
- `brier_*` اگر در JSON `null` بود → `enabled = false`.
- `load()` را فقط یک بار (در `Application` یا با پرچم `loaded`) صدا بزن، نه در هر به‌روزرسانی.

### D2. اعمال درست احتمال (جایگزین خط فعلی `Calibration.calibrateProb(pNeighEcmwf)`)

```kotlin
/** سهم نقاط شبکهٔ ECMWF که مجموع بارش W ساعتِ منتهی به اندیس endIdx آن‌ها ≥ آستانه است */
private fun windowWetFraction(grid: ModelGrid, endIdx: Int, w: Int, thr: Double): Double? {
    val start = endIdx - w + 1
    if (start < 0) return null
    var valid = 0; var wet = 0
    for (p in 0 until grid.pointCount) {
        var s = 0.0; var ok = true
        for (k in start..endIdx) { val v = grid.precip[p].getOrNull(k); if (v == null || v.isNaN()) { ok = false; break }; s += v }
        if (ok) { valid++; if (s >= thr) wet++ }
    }
    return if (valid >= Tuning.NEIGH_MIN_VALID_POINTS) wet.toDouble() / valid else null
}

// در حلقهٔ ساعتی:
val pNeighEcmwfCal: Double? = if (pNeighEcmwf != null && ecmwfGrid != null && Calibration.appliesTo(currentMonth)) {
    val W = Calibration.windowHours
    val p6 = windowWetFraction(ecmwfGrid, gIdxEcmwf, W, Calibration.wetThresholdMm)
    if (p6 == null) pNeighEcmwf
    else {
        val c6 = Calibration.calibrateProb(p6)
        val floor = Calibration.probAtZero() / W            // y[0] / W
        if (p6 <= 0.0) floor else max(floor, min(c6, pNeighEcmwf * c6 / p6))
    }
} else pNeighEcmwf
```
`Calibration.probAtZero()` = `probY.first()`.
**نکته:** `past_days=1` (مرحلهٔ A2) لازم است تا برای ساعت‌های اول روز هم ۶ ساعت گذشته موجود باشد.

برای بازهٔ ۶ ساعته، جزء ECMWF را مستقیم `c6` ساعت پایانی بگذار؛ برای ۱۲ و ۲۴ ساعت، بیشینهٔ `c6` پنجره‌های ۶ ساعتهٔ پیاپی داخل بازه. (این را در `buildBlock` به صورت `max(blockStats.pWet, maxParea, c6Block)` وارد کن.)

### D3. اعمال درست مقدار

```kotlin
val hresCenter: Double? = centerPrecip.at(idxCenter)?.let { raw ->
    if (!Calibration.qmEnabled || !Calibration.appliesTo(currentMonth)) raw
    else {
        val W = Calibration.windowHours
        val start = idxCenter - W + 1
        val window = if (start >= 0) (start..idxCenter).map { centerPrecip.at(it) } else null
        if (window == null || window.any { it == null }) raw
        else {
            val aW = window.sumOf { it!! }
            if (aW < 0.1) raw else raw * (Calibration.calibrateAmount(aW) / aW)
        }
    }
}
```

### D4. بازنویسی `VerificationStore` (سبک و درست)

مدل جدید: به‌جای ثبت ۴۸ ساعت در هر به‌روزرسانی، فقط **پنجره‌های استاندارد SYNOP** ثبت شوند.

- در هر به‌روزرسانی: پنجره‌های ۶ ساعته‌ای را که پایانشان ساعت‌های ۰۰، ۰۶، ۱۲، ۱۸ UTC است و پایانشان بین ۱ تا ۲۴ ساعت آینده است پیدا کن (معمولاً ۴ پنجره). برای هر کدام:
  `p = Pblock` پنجره (مثل C3، ترکیب ensemble و بیشینهٔ `Ppoint` ساعت‌های پنجره)، `amount = p50` پنجره، `leadH = (windowEnd − now)/3600` گرد به پایین.
- کلید یکتا: `(windowEndEpoch, leadBucket)` که `leadBucket ∈ {6, 12, 24}` (کوچک‌ترین مقدار ≥ `leadH`). اگر رکورد با همین کلید هست، **جایگزین کن** (آخرین پیش‌بینی هر افق بماند).
- فایل را یک بار در هر به‌روزرسانی بخوان، در حافظه تغییر بده، یک بار بنویس. حداکثر `VERIFY_MAX_LINES` خط؛ قدیمی‌ترها حذف شوند.
- ارزیابی: وقتی SYNOP با `precipPeriodH == VERIFY_WINDOW_H` و `precipMm != null` رسید، رکوردهای با `windowEndEpoch == synop.timeUtcEpochSec` را با `observedWet = precipMm >= 0.2` و `observedAmount = precipMm` به‌روز کن. اگر `precipMm == null` بود، **هیچ چیزی ثبت نشود** (عدد ساختگی ۰٫۵ حذف). اگر دورهٔ گزارش ۱۲ ساعت بود، دو پنجرهٔ ۶ ساعتهٔ آن را ارزیابی نکن (دادهٔ تفکیکی نداریم).
- `computeStats` به تفکیک `leadBucket` (۶/۱۲/۲۴): Brier، POD، FAR، نسبت بایاس مقدار، و تعداد رویدادهای بارانی. کارت «کیفیت پیش‌بینی» این سه ستون را نشان دهد، به‌علاوهٔ وضعیت جدول کالیبره (فعال/غیرفعال و دلیل).

### تست‌های مرحلهٔ D (`CalibrationTest.kt` تازه، با JSON در `src/test/resources`)

| آزمون | انتظار |
|---|---|
| جدول `x=[0,0.5,1]`, `y=[0.02,0.3,0.8]`, `W=6`؛ `p6=0.25`, `pNeigh=0.1` | `0.064` |
| همان، `p6=0.25`, `pNeigh=0.0` | `0.02/6 ≈ 0.00333` |
| همان، `p6=0`، `pNeigh=0` | `0.00333` |
| `fc=[0.5,1,2]`, `obs=[1,2,5]`: `aW=1.5` | `3.5` |
| همان: `aW=4` | `8.0` (ضریب ۲٫۵ محدود به ۲) |
| `model = "icon_global"` | `enabled = false` |
| `x` نزولی | `enabled = false` |
| ماه ۷ با `months=[1..5,10..12]` | `appliesTo(7) = false` |
| `VerificationStore`: دو ثبت برای یک پنجره و یک افق | فقط یک رکورد (آخری) |
| SYNOP با `precipMm = null` | هیچ رکوردی ارزیابی نشود |

---

## مرحلهٔ E — UI، مستندات و پاک‌سازی

۱. **راهنمای HyperOS** در صفحهٔ تنظیمات هشدار (`WeatherAlertBottomSheetContent` یا معادل آن): «برای دریافت هشدار در گوشی‌های شیائومی (HyperOS)، در تنظیمات باتری این اپ گزینهٔ بدون محدودیت (No restrictions) را انتخاب کنید.»
۲. کارت رادار: برای سلول‌های `UNKNOWN_MOTION` بنویس «جهت حرکت نامعلوم»؛ منبع حرکت را نشان بده («ردیابی رادار» یا «باد مدل»)؛ اگر `coverageVerified == false` بنویس «پوشش رادار تایید نشده»؛ تعداد پیکسل‌های کلاتر حذف‌شده.
۳. کارت «سلامت منابع»: منبع جدید `open-meteo/lifted_index` و وضعیت کش SYNOP.
۴. **README.md و AGENTS.md:** فقط آن‌چه واقعاً پیاده شده را بنویس. پیش از این اصلاحیه README ادعای «ردیابی سلول‌ها» داشت که وجود نداشت؛ بعد از مرحلهٔ B درست است، ولی هر ادعای دیگری را با کد تطبیق بده. محدودیت‌های بخش ۶.۷ دستورالعمل قبلی را اضافه کن.
۵. `UniversalBlue.cache` را `java.util.concurrent.ConcurrentHashMap` کن.
۶. فایل‌های `public/assets/aistudio/dezful-weather-fix-instructions_*.md` را دست نزن.
۷. همهٔ تست‌ها را اجرا کن و فهرست نهایی تست‌ها با وضعیت پاس/رد را گزارش بده.

---

## چک‌لیست نهایی پذیرش

- [ ] هیچ‌جای کد `?: 0.0`، `?: 50.0`، `?: 24.0`، `?: 270.0`، `?: 25.0`، `?: 60.0` برای دادهٔ هواشناسی وجود ندارد (با جست‌وجوی متنی بررسی کن و نتیجه را گزارش بده).
- [ ] شکست درخواست GFS یا ICON جدول ساعتی را خالی نمی‌کند.
- [ ] گزارش SYNOP نیمه‌ابری بارش را درست می‌خواند.
- [ ] رادار کهنه (بیش از ۳۰ دقیقه) هیچ هشداری نمی‌سازد.
- [ ] سلول بدون ردیابی و بدون باد مدل، ETA ندارد.
- [ ] هر به‌روزرسانی حداکثر ۱۲ کاشی رادار دانلود می‌کند و کاشی تکراری از دیسک خوانده می‌شود.
- [ ] جدول کالیبره روی پنجرهٔ ۶ ساعته اعمال می‌شود و در ماه‌های خشک خاموش است.
- [ ] فایل `verify.jsonl` بیش از ۵۰۰۰ خط نمی‌شود.
- [ ] همهٔ تست‌های مراحل A تا D پاس می‌شوند.
