# یادداشت‌های پروژه (AGENTS.md)

## برنامه اندرویدی (Kotlin + Jetpack Compose)
- پروژه کامل Kotlin + Jetpack Compose در `com.amin.weather`.
- **منطق کامل پیش‌بینی پایش شعاع ۲۰ کیلومتری شمس‌آباد دزفول** (`32.2978N, 48.4283E`):
  - `Tuning.kt`: تمام آستانه‌ها و ثوابت منطق پیش‌بینی و رادار.
  - `RegionConfig.kt`: مختصات مرکز شمس‌آباد و شبکه ۱۷ نقطه‌ای همسایگی.
  - `GeoUtils.kt`: فرمول هاورساین، سمت، نقطه‌ی مقصد و تبدیل مختصات کاشی Slippy Web-Mercator.
  - `OpenMeteoClient.kt`: مدل‌های قطعی ECMWF IFS / ICON و اعضای انسمبل.
  - `SynopClient.kt`: دریافت و رمزگشایی کدهای SYNOP ایستگاه صفی‌آباد (WMO 40794) از Ogimet.
  - `RadarClient.kt` & `RadarAnalyzer.kt`: دانلود کاشی‌های RainViewer، استخراج dBZ با Universal Blue، ردیابی سلول‌ها و محاسبه زمان ورود (ETA).
  - `ForecastEngine.kt`: محاسبه احتمال محدوده ($P_{area}$) و شمس‌آباد ($P_{point}$)، مقادیر بارش $P50$ و $P90$، محافظ اقلیمی و سطح هشدار ۴گانه.
  - `Calibration.kt` & `VerificationStore.kt`: کالیبراسیون MOS و ثبت ارزیابی دقت روی دستگاه (Brier score).
  - `WeatherDashboardScreen.kt`: رابط کاربری Material 3 با نقشه Canvas رادار و دیدبانی صفی‌آباد.
