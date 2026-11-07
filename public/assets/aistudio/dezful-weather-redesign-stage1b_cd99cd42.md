# بازطراحی اپ «هواشناسی امین» — مرحلهٔ ۱-ب: کارت صفی‌آباد نباید گزارش کهنه را «الان» نشان دهد

> برای Gemini در Google AI Studio. مخزن: `aminonlin2006-beep/Whaterappshams` (کامیت `0384012`).
> اصلاح کوچک و فوری. **فقط همین موارد.** پیش از ویرایش هر فایل نسخهٔ فعلی را بخوان. در پایان تست‌ها را اجرا کن و گزارش بده.

## مشکل (رخداد واقعی ۱۷ آبان ۱۴۰۵)

چند ساعت در شمس‌آباد باران آمد ولی کارت صفی‌آباد «هوای حاضر: بدون بارش ☀️» نشان داد. علت در کد:

1. `SynopClient.parseOgimetResponse` گزارش‌های `NIL` را کنار می‌گذارد و **آخرین گزارش سالم ۲۴ ساعت گذشته** را برمی‌دارد. این گزارش ممکن است ساعت‌ها قبل از شروع باران باشد.
2. کارت `SynopObsCard` **زمان گزارش را نشان نمی‌دهد** و تازگی آن را بررسی نمی‌کند؛ گزارش کهنه را به‌عنوان «هوای حاضر» نمایش می‌دهد.
3. وقتی گروه ۷ (کد هوای حاضر `ww`) در گزارش نیست، `isRainNow = false` است و کارت «بدون بارش» می‌نویسد؛ یعنی «گزارش نشده» به‌صورت «آرام» نمایش داده می‌شود.
4. «بارش (میلی‌متر)» مقدار انباشتهٔ چند ساعت گذشته تا لحظهٔ گزارش است، نه بارش الان؛ ولی کارت این را روشن نمی‌گوید.

(موتور هشدار، یعنی `decideTier` و بخش 4.6، از قبل فقط گزارش تازه‌تر از ۹۰ دقیقه را استفاده می‌کند؛ مشکل فقط در نمایش است. این را تغییر نده.)

## ۱. `SynopClient.kt` — نگه داشتن وضعیت گروه هوای حاضر

به `SynopObs` یک فیلد اضافه کن (آخرین فیلد، با مقدار پیش‌فرض تا بقیهٔ کد کامپایل شود):
```kotlin
val iX: Int? = null   // شاخص گزارش هوای حاضر: 1/4/7 = گروه ۷ آمده؛ 2/5 = پدیدهٔ قابل‌گزارشی نبود؛ 3/6 = مشاهده نشده
```
در `parseSynopLine` هنگام ساخت `SynopObs` مقدار `iX = iX` را بده. در `writeDiskCache` فیلد `"iX"` را ذخیره کن (`obs.iX ?: JSONObject.NULL`) و در `readDiskCache` بخوان (`if (json.isNull("iX") || !json.has("iX")) null else json.getInt("iX")`).

## ۲. `Tuning.kt`
```kotlin
const val SYNOP_DISPLAY_FRESH_MIN = 90   // گزارش صفی‌آباد قدیمی‌تر از این، «هوای حاضر» حساب نمی‌شود (هم‌اندازهٔ SYNOP_FRESH_SEC)
```

## ۳. `WeatherDashboardScreen.kt` — کارت `SynopObsCard`

داخل `if (synop != null) { ... }`، در ابتدای بلوک این را حساب کن:
```kotlin
val nowSec = System.currentTimeMillis() / 1000L
val ageMin = ((nowSec - synop.timeUtcEpochSec) / 60L).toInt()
val isFresh = ageMin <= Tuning.SYNOP_DISPLAY_FRESH_MIN
val obsTimeIran = ForecastEngine.epochToIran(synop.timeUtcEpochSec)
```

و تغییرات:

**الف) خط زمان گزارش**، بالای ردیف دما، همیشه نمایش داده شود:
```kotlin
Text(
    text = if (isFresh) "گزارش ساعت $obsTimeIran (${JalaliCalendar.formatNumber(ageMin.toDouble())} دقیقه پیش)"
           else "⚠️ آخرین گزارش موجود: ساعت $obsTimeIran (${formatAge(ageMin)} پیش) — کهنه است و برای وضعیت فعلی استفاده نمی‌شود",
    style = MaterialTheme.typography.labelSmall,
    color = if (isFresh) TextSecondary else AmberStorm
)
```
یک تابع کمکی ساده بنویس: `formatAge(min)` که زیر ۶۰ دقیقه «X دقیقه» و بالاتر «X ساعت و Y دقیقه» برگرداند (با ارقام فارسی از `JalaliCalendar.formatNumber`).

**ب) بارش انباشته:** متن‌های «بارش:» و «دوره:» را به این تغییر بده:
- اگر `precipMm != null && precipPeriodH != null`: «بارش انباشته: X mm در $precipPeriodH ساعت منتهی به ساعت $obsTimeIran»
- اگر `precipMm != null && precipPeriodH == null`: «بارش انباشته: X mm (دوره نامشخص، تا ساعت $obsTimeIran)»
- اگر `precipMm == null`: «بارش انباشته: گزارش نشده»

**ج) هوای حاضر:** خط «هوای حاضر» را با این منطق جایگزین کن:
```kotlin
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
```
(توجه: علامت ☀️ دیگر استفاده نمی‌شود؛ «بدون بارش در لحظهٔ گزارش» آفتابی بودن را ادعا نمی‌کند.)

## ۴. کارت «سلامت منابع»

اگر در کارت سلامت منابع برای `ogimet/synop` پیام نشان داده می‌شود، در `ForecastEngine.buildDashboardData` هنگام ساختن `SourceHealth("ogimet/synop", ...)`، اگر `synopRes.data != null` است، به پیام عمر گزارش را اضافه کن:
```kotlin
val synopAgeMin = synopRes.data?.let { ((nowEpochSec - it.timeUtcEpochSec) / 60L).toInt() }
```
(اگر `nowEpochSec` آن‌جا هنوز تعریف نشده، `System.currentTimeMillis() / 1000L` را استفاده کن.) پیام: «آخرین گزارش: X دقیقه پیش» و اگر بیش از `Tuning.SYNOP_DISPLAY_FRESH_MIN` بود، وضعیت را `SourceStatus.PARTIAL` بگذار با پیام «آخرین گزارش X دقیقه پیش — کهنه».

## ۵. تست‌ها — `SynopClientTest.kt`

1. گزارشی با `iX = 3` و بدون گروه ۷ → `ww == null` و `iX == 3`.
2. گزارشی با `iX = 1` و گروه `76166` (باران متوسط پیوسته؛ ww=61) → `isRainNow == true` و `iX == 1`.
3. پاسخ Ogimet با دو خط: خط اول سالم با ww بدون بارش در ساعت 12 UTC، خط دوم `NIL` در ساعت 18 UTC → خروجی همان گزارش ساعت 12 است (رفتار فعلی حفظ شود؛ فقط زمانش درست برگردد تا کارت کهنه بودن را نشان دهد).

## ۶. نسخه

`versionCode = 3`، `versionName = "2.0-stage1b"`. **`applicationId` را تغییر نده.**

## گزارش پایانی
فایل‌های تغییرکرده، نتیجهٔ تست‌ها، و هر انحراف از این دستورالعمل.
