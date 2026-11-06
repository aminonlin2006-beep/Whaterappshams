# دستورالعمل اصلاح کامل اپ «هواشناسی امین» — نسخه دقیق بارش برای شعاع ۲۰ کیلومتری شمس‌آباد دزفول

> این فایل برای مدل Gemini Flash در Google AI Studio نوشته شده است.
> مخزن: `https://github.com/aminonlin2006-beep/wather` — پکیج: `com.amin.weather` — زبان: Kotlin + Jetpack Compose.
> تاریخ تنظیم: آبان ۱۴۰۵ (نوامبر ۲۰۲۶).

> **راهنمای استفاده برای صاحب پروژه:** این فایل را کامل به AI Studio بدهید و بنویسید: «این دستورالعمل را بخوان و فقط فاز ۱ را اجرا کن.» بعد از گزارش مدل و بیلد موفق، بنویسید «فاز ۲» و همین‌طور تا فاز ۷. اگر مدل وسط کار خطای بیلد داد، همان پیام خطا را برایش بفرستید و بگویید «طبق بخش ۰ همین فاز را درست کن».

---

## بخش ۰ — قوانین کار برای مدل (اول این بخش را کامل بخوان)

۱. **کار را فاز به فاز انجام بده.** فازها در بخش ۶ آمده‌اند. در هر نوبت فقط یک فاز را انجام بده، بعد توقف کن و گزارش بده: کدام فایل‌ها تغییر کرد، چه چیزی اضافه/حذف شد، و چه آزمونی اجرا شد. تا کاربر نگفته «فاز بعد»، سراغ فاز بعدی نرو.

۲. **هیچ عدد، داده یا خروجی ساختگی تولید نکن.** اگر منبعی پاسخ نداد، مقدار را `null` بگذار و در رابط کاربری «نامشخص» یا «در دسترس نیست» نشان بده. هرگز به جای داده‌ی واقعی، عدد پیش‌فرض قابل‌قبول‌نما (مثل دمای ۲۴ یا رطوبت ۵۰) نگذار.

۳. **همه آستانه‌ها و ضرایب باید ثابت نام‌دار باشند** و فقط در فایل `core/Tuning.kt` تعریف شوند (بخش ۵). هیچ عدد جادویی داخل منطق نگذار.

۴. **برچسب‌ها صادق باشند.** داده‌ی مدل را «مشاهده» یا «ایستگاه» نام‌گذاری نکن. داده‌ی Open-Meteo `current` خروجی مدل است، نه دیدبانی.

۵. زبان رابط کاربری فارسی و راست‌به‌چپ بماند. نام پکیج، `applicationId`، آیکون و نام اپ تغییر نکند.

۶. پیش از نوشتن کد، اگر به جست‌وجوی گوگل دسترسی داری، شناسه‌های مدل Open-Meteo (`models=`) را در مستندات رسمی (`open-meteo.com/en/docs`) تایید کن. علاوه بر آن، کد باید در زمان اجرا هم با سازوکار «جایگزین» (fallback) که در بخش ۶ آمده، از شناسه‌ی اشتباه جان سالم به در ببرد.

۷. هیچ خطایی را بی‌صدا نبلع. به‌جای `catch (_: Exception) { "" }`، نتیجه را به صورت `SourceResult` (بخش ۶، فاز ۲) با وضعیت و دلیل خطا برگردان تا داشبورد بداند کدام منبع کار نکرد.

۸. تست‌های واحد نباید به اینترنت وصل شوند. از داده‌ی نمونه (fixture) استفاده کن.

۹. اگر جایی از این دستورالعمل با کد فعلی تناقض داشت، این دستورالعمل اولویت دارد؛ ولی تناقض را در گزارش فاز بنویس.

---

## بخش ۱ — هدف

هدف اصلی: **بیشترین دقت ممکن در پیش‌بینی و هشدار بارش برای دایره‌ای به شعاع ۲۰ کیلومتر به مرکز شهر شمس‌آباد** (بخش مرکزی شهرستان دزفول).

خروجی‌های اصلی که باید دقیق باشند:

- احتمال بارش ساعتی در «محدوده» (هر جای دایره‌ی ۲۰ کیلومتری) و در «نقطه‌ی مرکز».
- مقدار بارش محتمل (میانه) و بیشینه‌ی معقول (صدک ۹۰) برای هر ساعت و هر بازه‌ی ۱/۳/۶/۱۲/۲۴ ساعته.
- هشدار کوتاه‌مدت (۰ تا ۳ ساعت) بر پایه‌ی رادار واقعی: آیا توده‌ی بارشی وارد دایره می‌شود، از کدام جهت، و در چه بازه‌ی زمانی.
- سطح هشدار چهارگانه (سبز/زرد/نارنجی/قرمز) با قواعد شفاف.

---

## بخش ۲ — اطلاعات جغرافیایی و اقلیمی منطقه (به‌روز و راستی‌آزمایی‌شده)

### ۲.۱ نقطه‌ی مرکز

| مورد | مقدار |
|---|---|
| مرکز | شهر شمس‌آباد، بخش مرکزی دزفول |
| مختصات | `32.2978 N, 48.4283 E` (منبع: OpenStreetMap از طریق ویکی‌پدیا) |
| شعاع هدف | ۲۰ کیلومتر |
| ارتفاع تقریبی | حدود ۸۰ تا ۱۴۰ متر؛ ایستگاه صفی‌آباد ۸۳ متر و فرودگاه دزفول ۱۴۴ متر. ارتفاع دقیق را از فیلد `elevation` پاسخ Open-Meteo بخوان. |
| منطقه زمانی | `Asia/Tehran`، UTC+03:30، بدون ساعت تابستانی |

نکته: مختصات فعلی کد (`32.3006, 48.4308`) فقط حدود ۰٫۴ کیلومتر با شمس‌آباد فاصله دارد، ولی باید دقیقاً به مقدار بالا تغییر کند و نام آن `SHAMSABAD` شود.

### ۲.۲ نقاط مهم داخل و اطراف دایره (فاصله و سمت از شمس‌آباد، محاسبه‌شده با فرمول هاورساین)

| نقطه | مختصات | فاصله (km) | سمت (درجه از شمال) | نقش در اپ |
|---|---|---|---|---|
| ایستگاه سینوپتیک صفی‌آباد (WMO 40794) | 32.2667, 48.4167 | ۳٫۶ | ۱۹۸ | **مهم‌ترین منبع مشاهده‌ی واقعی** (SYNOP) |
| شهر دزفول (حدود مرکز) | ~32.38, 48.41 | ~۹٫۵ | ~۳۴۷ | داخل دایره |
| فرودگاه دزفول / پایگاه وحدتی (OIAD) | 32.4344, 48.3976 | ۱۵٫۵ | ۳۴۹ | داخل دایره. METAR آن در منابع بین‌المللی معمولاً موجود نیست؛ به آن تکیه نکن. |
| اندیمشک | 32.4578, 48.3567 | ۱۹٫۰ | ۳۳۹ | لبه‌ی شمالی دایره |
| شوش | 32.1942, 48.2436 | ۲۰٫۸ | ۲۳۶ | درست بیرون لبه‌ی جنوب‌غربی |
| گتوند | 32.2472, 48.8164 | ۳۶٫۹ | ۹۹ | بیرون دایره (شرق) |
| شوشتر | 32.0455, 48.8567 | ۴۹٫۱ | ۱۲۵ | بیرون دایره |
| اهواز (محل یکی از رادارهای هواشناسی ایران) | 31.319, 48.684 | ۱۱۱٫۵ | ۱۶۷ | رادار پوشش‌دهنده‌ی احتمالی منطقه |
| بستان | 31.7214, 47.9869 | ۷۶٫۴ | ۲۱۳ | مسیر ورود سامانه‌های جنوب‌غربی |
| دهلران | 32.6941, 47.2679 | ۱۱۷٫۴ | ۲۹۲ | مسیر ورود سامانه‌های غربی |
| پلدختر | 33.1539, 47.7136 | ۱۱۶٫۳ | ۳۲۵ | مسیر ورود از شمال‌غرب |
| خرم‌آباد (OICK، METAR موجود) | ~33.49, 48.36 | ~۱۳۲ | ~۳۵۷ | فقط به عنوان مرجع دوردست |

### ۲.۳ توپوگرافی (اثر بر بارش)

- شمس‌آباد در **دشت آبرفتی هموار دزفول–اندیمشک** است (ارتفاع حدود ۵۰ تا ۱۵۰ متر، شیب ملایم به سمت جنوب و جنوب‌غرب). داخل دایره‌ی ۲۰ کیلومتری عارضه‌ی کوهستانی مهمی وجود ندارد؛ پس **اثر ارتفاع بر توزیع بارش داخل دایره ناچیز است** و تفاوت بارش بین نقاط دایره عمدتاً از ماهیت **همرفتی و تکه‌تکه** بارش‌ها ناشی می‌شود.
- **کوهپایه‌های زاگرس چین‌خورده** در شمال و شمال‌شرق، در فاصله‌ی تقریبی ۲۵ تا ۴۰ کیلومتر شروع می‌شوند و ارتفاع به سرعت به بیش از ۱۰۰۰ متر می‌رسد (منطقه‌ی سد دز و شوی). این کوهپایه‌ها محل شکل‌گیری توده‌های همرفتی بعدازظهر هستند و ممکن است در **رادار به صورت «کلاتر» زمینی ثابت** هم دیده شوند.
- **رودخانه‌ی دز** از کنار دزفول می‌گذرد و به سمت جنوب به کارون می‌پیوندد. اثر مستقیم آن بر بارش در مدل‌سازی این اپ قابل‌اعتنا نیست.
- نتیجه برای الگوریتم: به‌جای «درون‌یابی مثلثی» بین شهرهای اطراف، باید از **روش همسایگی (neighbourhood)** استفاده شود: یعنی نمونه‌گیری از چند نقطه‌ی شبکه‌ی مدل داخل دایره و محاسبه‌ی «احتمال بارش در محدوده».

### ۲.۴ اقلیم بارش (نرمال ۱۹۹۱–۲۰۲۰ ایستگاه صفی‌آباد، منبع: WMO/NOAA)

| ماه | ژانویه | فوریه | مارس | آوریل | مه | ژوئن | ژوئیه | اوت | سپتامبر | اکتبر | نوامبر | دسامبر | سال |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| بارش (mm) | 66.1 | 42.7 | 49.3 | 26.8 | 8.7 | 0.1 | 0.1 | 0.1 | 0.7 | 11.8 | 50.5 | 68.7 | 325.6 |
| روزهای ≥ ۱ mm | 6.2 | 4.9 | 4.8 | 4.0 | 1.3 | 0.0 | 0.0 | 0.1 | 0.1 | 1.3 | 4.0 | 6.2 | 32.9 |
| رطوبت نسبی میانگین (%) | 77 | 69 | 64 | 54 | 35 | 25 | 26 | 32 | 38 | 48 | 65 | 77 | 50.8 |
| نقطه شبنم میانگین (°C) | 7.3 | 7.4 | 9.7 | 12.2 | 10.6 | 9.7 | 12.4 | 14.8 | 13.9 | 12.6 | 10.7 | 8.7 | 10.8 |

برداشت‌های لازم برای الگوریتم:

- **فصل بارش آبان تا فروردین** است (نوامبر تا آوریل). از خرداد تا شهریور (ژوئن تا سپتامبر) بارش عملاً صفر است. پس در این ماه‌ها هر سیگنال ضعیف مدل احتمالاً هشدار کاذب است و باید محافظ اقلیمی (بخش ۶، فاز ۴) اعمال شود.
- بارش سالانه کم (حدود ۳۲۰ میلی‌متر) با تغییرپذیری زیاد سال‌به‌سال (ضریب تغییرات حدود ۰٫۳۳ در ایستگاه سینوپتیک دزفول) است و در حدود ۳۳ روز در سال (≥ ۱ mm) می‌بارد.

### ۲.۵ سامانه‌های بارشی غالب

- عامل اصلی بارش‌های سنگین و همرفتی جنوب‌غرب ایران و حوضه‌ی دز، **سامانه‌ی کم‌فشار سودانی** است (زبانه‌ای از روی دریای سرخ و شمال‌غرب عربستان به سمت خوزستان و جنوب عراق) که معمولاً با ناوه‌ی مدیترانه‌ای در ترازهای بالا ترکیب می‌شود.
- جریان رطوبتی عمدتاً از **جنوب، جنوب‌غرب و غرب** وارد منطقه می‌شود؛ توده‌های بارشی معمولاً از سمت غرب و جنوب‌غرب به شمال‌شرق حرکت می‌کنند. ولی **جهت حرکت باید از رادار و باد تراز میانی اندازه‌گیری شود، نه این‌که فرض شود.**
- بارش‌ها اغلب **همرفتی و نقطه‌ای** هستند: یک نقطه ممکن است چند میلی‌متر بگیرد و چند کیلومتر آن‌طرف‌تر خشک بماند. به همین دلیل اپ باید دو احتمال را جدا نشان دهد: «احتمال بارش در محدوده» و «احتمال بارش در نقطه‌ی مرکز».

---

## بخش ۳ — منابع داده (وضعیت تاییدشده در نوامبر ۲۰۲۶)

### ۳.۱ Open-Meteo Forecast API (مدل‌های قطعی)

- آدرس: `https://api.open-meteo.com/v1/forecast`
- **ECMWF IFS HRES با تفکیک ۹ کیلومتر و گام ساعتی** از اکتبر ۲۰۲۵ در Open-Meteo موجود است. شناسه: `ecmwf_ifs`. این مدل CAPE، رگبار (`showers`)، نقطه شبنم و تندباد را مستقیم دارد، ولی **متغیرهای تراز فشاری ندارد** (برای باد ۷۰۰/۵۰۰ هکتوپاسکال از `ecmwf_ifs025` استفاده کن).
- شناسه‌ی جایگزین ECMWF: `ecmwf_ifs025` (تفکیک ۰٫۲۵ درجه، گام سه‌ساعتی).
- مدل دوم: DWD ICON جهانی — شناسه‌ی پیشنهادی `icon_global`، جایگزین `icon_seamless`.
- مدل سوم (فقط برای `lifted_index`): NOAA GFS — شناسه‌ی پیشنهادی `gfs_seamless`، جایگزین `gfs_global`.
- چند مکان در یک درخواست مجاز است (تا ۱۰۰۰ مکان، با کاما). در این حالت خروجی JSON **آرایه** است، به همان ترتیب مکان‌ها.
- وقتی در یک درخواست چند مدل خواسته شود، نام کلیدهای خروجی پسوند مدل می‌گیرند (مثلاً `precipitation_ecmwf_ifs`). پارسر باید هر دو حالت (با پسوند و بی‌پسوند) را پشتیبانی کند.
- همیشه `timeformat=unixtime` و `timezone=GMT` بفرست تا تبدیل زمان بدون خطا باشد.
- سقف استفاده‌ی رایگان غیرتجاری: حدود ۶۰۰ درخواست در دقیقه، ۵۰۰۰ در ساعت، ۱۰۰۰۰ در روز. درخواست‌های با بیش از ۱۰ متغیر یا چند مکان، وزن بیشتری دارند. طراحی این دستورالعمل با به‌روزرسانی هر ۳۰ دقیقه زیر این سقف می‌ماند.
- شرط مجوز: در رابط کاربری عبارت «داده‌های هواشناسی: Open-Meteo.com» نمایش داده شود.

### ۳.۲ Open-Meteo Ensemble API (احتمال واقعی)

- آدرس: `https://ensemble-api.open-meteo.com/v1/ensemble`
- `ecmwf_ifs025`: ۵۱ عضو، ۰٫۲۵ درجه، گام اصلی سه‌ساعتی (Open-Meteo آن را به ساعتی درون‌یابی می‌کند؛ پس **احتمال را روی پنجره‌های ۳ ساعته حساب کن**، نه ساعت تکی).
- ICON EPS جهانی: ۴۰ عضو، حدود ۱۳ کیلومتر، ساعتی. شناسه‌ی پیشنهادی `icon_seamless`؛ جایگزین‌ها به ترتیب: `icon_global`, `dwd_icon_seamless_eps`.
- نسخه‌ی ۹ کیلومتری ECMWF ensemble فقط اروپا را پوشش می‌دهد؛ برای ایران استفاده نکن.
- برای هر مدل ensemble **یک درخواست جداگانه** بفرست تا نام کلیدها مبهم نشود. کلیدها: `precipitation` (عضو کنترل) و `precipitation_member01` … `precipitation_memberNN`. پارسر باید همه‌ی کلیدهایی را که با `precipitation` شروع می‌شوند و بعدش یا هیچ یا `_member` آمده، به عنوان عضو جمع کند.

### ۳.۳ رادار RainViewer (وضعیت بعد از ۱ ژانویه ۲۰۲۶)

- فهرست فریم‌ها: `https://api.rainviewer.com/public/weather-maps.json` → `host` و `radar.past[]` (حدود ۲ ساعت گذشته، گام ۱۰ دقیقه). **مسیر کاشی را فقط از فیلد `path` بساز**، نه از روی timestamp.
- محدودیت‌های فعلی: **بیشینه‌ی زوم ۷**، فقط طرح رنگ **Universal Blue (شناسه 2)**، حدود ۱۰۰ درخواست در دقیقه برای هر IP، **بدون nowcast آینده** و بدون ماهواره.
- قالب کاشی: `{host}{path}/{size}/{z}/{x}/{y}/{color}/{smooth}_{snow}.png`
  مقدار ثابت در این اپ: `size=256`, `z=7`, `color=2`, `smooth=0`, `snow=0` (یعنی پسوند `/256/7/{x}/{y}/2/0_0.png`). **smooth باید ۰ باشد** تا رنگ پیکسل‌ها دقیقاً با جدول رنگ بخواند.
- کاشی پوشش رادار: `{host}/v2/coverage/0/256/7/{x}/{y}/0/0_0.png` — جای بدون پوشش **سیاه** و جای دارای پوشش **شفاف** است.
- RainViewer برای ایران چند رادار فهرست کرده (از جمله کدی برای اهواز)، ولی کیفیت پوشش باید **در زمان اجرا با کاشی پوشش** سنجیده شود. اگر مرکز پوشش نداشت، رادار را «در دسترس نیست» اعلام کن و هیچ نتیجه‌ی راداری نساز.
- **جدول رنگ Universal Blue (باران) → dBZ**، از فایل رسمی `rainviewer_api_colors_table.csv` (فقط ردیف‌های کاملاً مات، آلفا = FF):

```
15:88DDEE 16:6CD1EB 17:51C5E8 18:36BAE5 19:1BAEE2 20:00A3E0 21:009AD5 22:0091CA 23:0088BF 24:007FB4
25:0077AA 26:0070A3 27:00699C 28:006295 29:005B8E 30:005588 31:005180 32:004E78 33:004A70 34:004768
35:FFEE00 36:FFE000 37:FFD200 38:FFC500 39:FFB700 40:FFAA00 41:FF9F00 42:FF9500 43:FF8B00 44:FF8100
45:FF4400 46:F23600 47:E62800 48:D91B00 49:CD0D00 50:C10000 51:A80000 52:8F0000 53:760000 54:5D0000
55:FFAAFF 56:FF9FFF 57:FF95FF 58:FF8BFF 59:FF81FF 60:FF77FF 61:FF6CFF 62:FF62FF 63:FF58FF 64:FF4EFF
65:FFFFFF
```

  ردیف‌های ۱۰- تا ۱۴ dBZ در این طرح رنگ **نیمه‌شفاف و خاکستری** هستند (آلفا کمتر از FF). هر پیکسل با آلفای کمتر از 255 را «پژواک ضعیف غیر بارشی (کمتر از ۱۵ dBZ)» در نظر بگیر و در محاسبه‌ی بارش وارد نکن.

### ۳.۴ مشاهده‌ی واقعی زمینی: SYNOP ایستگاه صفی‌آباد از Ogimet

- ایستگاه سینوپتیک **صفی‌آباد دزفول، WMO 40794** (32.2667N, 48.4167E، ارتفاع ۸۳ متر) فقط ۳٫۶ کیلومتر با شمس‌آباد فاصله دارد و معتبرترین «حقیقت زمینی» در دسترس است.
- آدرس: `https://www.ogimet.com/cgi-bin/getsynop?block=40794&begin=YYYYMMDDHHmm&end=YYYYMMDDHHmm` (زمان‌ها UTC). خروجی هر خط: `40794,YYYY,MM,DD,HH,mm,AAXX ... =`
- اول `https` را امتحان کن. اندروید درخواست `http` را به‌طور پیش‌فرض مسدود می‌کند؛ اگر `https` کار نکرد، فقط برای دامنه‌ی `www.ogimet.com` یک `network_security_config.xml` با `cleartextTrafficPermitted="true"` اضافه کن و آن را در `AndroidManifest.xml` ثبت کن. اجازه‌ی cleartext را برای همه‌ی دامنه‌ها باز نکن.
- گزارش‌ها معمولاً سه‌ساعتی‌اند (۰۰، ۰۳، ۰۶، … UTC). **حداکثر یک بار در ساعت** درخواست بفرست و نتیجه را کش کن (Ogimet سرویس رایگان و حساس به درخواست زیاد است).
- شناسه‌ی WMO ایستگاه‌های دیگر (مثل فرودگاه دزفول) را **حدس نزن**. فقط اگر کاربر شناسه‌ی تاییدشده داد، به فهرست اضافه کن.
- اگر Ogimet از داخل ایران در دسترس نبود یا خطا داد، اپ باید بدون آن کار کند و در داشبورد بنویسد «مشاهده‌ی ایستگاه صفی‌آباد در دسترس نیست».

---

## بخش ۴ — عیب‌های فعلی که باید رفع شوند (فهرست کامل با محل در کد)

| # | فایل / تابع | عیب | شدت |
|---|---|---|---|
| E1 | `OpenMeteoClient.RADAR_COMPOSITE_GRID` و `scanRainViewerRadar` | هر کاشی در زوم ۷ حدود ۲۶۰×۲۲۰ کیلومتر است. کاشی (81,51) از عرض 31.95 تا 34.31 و طول 47.81 تا 50.62 است و مرکز در گوشه‌ی جنوب‌غربی آن (پیکسل حدود x=56, y=219). کاشی «شمال» (81,50) از عرض 34.31 شروع می‌شود (بیش از ۲۰۰ کیلومتر دور) ولی در کد ۲۸ کیلومتر فرض شده. کاشی «غرب» (80,51) داخل عراق است ولی ۳۲ کیلومتر فرض شده. | بحرانی |
| E2 | همان | وجود ابر از **حجم فایل PNG** (بیش از ۹۰۰ بایت) تشخیص داده می‌شود؛ dBZ، فاصله، سرعت حرکت (۳۰/۳۴/۳۸) و زمان رسیدن همه عدد ثابت‌اند. هر پژواکی هر جای کاشی ۲۶۰ کیلومتری، هشدار قرمز «زیر ۱۵ کیلومتر» می‌سازد. | بحرانی |
| E3 | `fetchPerimeterStationsBatch`, `fetchLiveStationTelemetry` | داده‌ی `current` Open-Meteo خروجی مدل است ولی «ایستگاه»، «تله‌متری فرودگاه» و «شبکه‌ی دیدبانی» نامیده شده. | بحرانی |
| E4 | `WeatherRepository.getSavedSkyObservation` + `ForecastEngine.buildDashboardData` | گزارش کاربر هیچ‌وقت منقضی نمی‌شود؛ یک گزارش «باران» برای همیشه `isRadarEcho=true` می‌سازد (هشدار دائمی نارنجی و احتمال ۹۰٪). دمای کاربر هم دائماً جای دمای مدل را می‌گیرد. عکس base64 در SharedPreferences و در فایل کش JSON ذخیره می‌شود. | بحرانی |
| E5 | `buildDashboardData`، محاسبه‌ی `prob` | منطق برعکس: وقتی SPP ≥ ۶۰ است حداقل ۸۰٪، ولی وقتی SPP کمتر است و فقط اکو هست حداقل ۹۰٪. | زیاد |
| E6 | همان، `spatialEstimate` | مقدار بارش میلی‌متر از روی احتمال ساخته می‌شود (`spp/100*2*boost`). | زیاد |
| E7 | `computeSpatialTriangulation` | درون‌یابی باریسنتریک از روی احتمال‌های پله‌ای ساختگی (۵/۳۰/۵۰/۷۵/۹۵) بر پایه‌ی داده‌ی مدل؛ مقدار پیش‌فرض باد ۱۶ km/h از ۲۷۰ درجه وقتی داده نیست؛ جمع شار انتقال (واحد %/ساعت) با احتمال. ریاضی درست است ولی ورودی بی‌اعتبار است. | زیاد |
| E8 | `computeSevereConvectiveIndex` | پارامتر `dewPointDepression` استفاده نمی‌شود؛ `lifted_index` برای ECMWF تهی است، پس ۲۰ امتیاز هرگز داده نمی‌شود؛ رطوبت ۴۵ تا ۸۵ درصد خودبه‌خود ۱۰ امتیاز می‌دهد. | متوسط |
| E9 | `buildDashboardData` | سطح زرد (`AlertTier.YELLOW`) هرگز انتخاب نمی‌شود. | متوسط |
| E10 | `fetchMinutely15` | داده گرفته می‌شود ولی هیچ‌جا استفاده نمی‌شود. | کم |
| E11 | `buildDashboardData` | مقادیر پیش‌فرض ساختگی: دما ۲۴، ابر ۲۰، رطوبت ۵۰ وقتی داده نیست. | متوسط |
| E12 | `sliceBlock` | فیلد `temp_mean` در واقع بیشینه است؛ `wind_speed` و `cloud_cover` فقط از ردیف اول. | کم |
| E13 | `REGIONAL_STATIONS` | فاصله‌های ثابت با واقعیت نمی‌خواند (اندیمشک ۱۵ به‌جای ۱۹). | کم |
| E14 | `httpGet` | همه‌ی خطاها بی‌صدا به رشته‌ی خالی تبدیل می‌شوند. | متوسط |
| E15 | `ForecastEngineTest` | تست به اینترنت وصل می‌شود؛ شرط‌هایش به وضع هوای لحظه بستگی دارد. | متوسط |
| E16 | `README.md`, `AGENTS.md` | نسخه‌ی قدیمی پایتون/WebView با سه مدل را توضیح می‌دهند. | کم |
| E17 | هشدارها | آستانه‌ها پراکنده و تکراری بین `ForecastEngine` و `WeatherNotificationManager`. | متوسط |

---

## بخش ۵ — معماری جدید

### ۵.۱ ساختار فایل‌ها (بعد از اصلاح)

```
app/src/main/java/com/amin/weather/
├── core/
│   ├── Tuning.kt                ← (جدید) همه‌ی ثابت‌ها و آستانه‌ها
│   ├── RegionConfig.kt          ← (جدید) مرکز، شعاع، نقاط همسایگی، ایستگاه‌ها
│   ├── GeoUtils.kt              ← (جدید) هاورساین، سمت، نقطه‌ی مقصد، ریاضی کاشی
│   ├── SourceResult.kt          ← (جدید) نتیجه‌ی هر منبع با وضعیت
│   ├── Http.kt                  ← (جدید) جایگزین httpGet/httpGetBytes با خطای شفاف
│   ├── OpenMeteoClient.kt       ← (بازنویسی) مدل‌های قطعی + همسایگی + ensemble
│   ├── RadarClient.kt           ← (جدید) RainViewer: فریم، پوشش، موزاییک، رمزگشایی پیکسل
│   ├── RadarAnalyzer.kt         ← (جدید) سلول‌ها، ردیابی حرکت، زمان رسیدن، کلاتر
│   ├── SynopClient.kt           ← (جدید) Ogimet + رمزگشای SYNOP
│   ├── ForecastEngine.kt        ← (بازنویسی) ترکیب نهایی، بازه‌ها، سطح هشدار
│   ├── VerificationStore.kt     ← (جدید) ثبت پیش‌بینی و مشاهده، امتیاز Brier
│   ├── WeatherModels.kt         ← (به‌روزرسانی مدل‌های داده)
│   ├── WeatherRepository.kt     ← (به‌روزرسانی کش و گزارش کاربر)
│   ├── WeatherNotificationManager.kt ← (به‌روزرسانی، آستانه‌ها از Tuning)
│   ├── WeatherAlertReceiver.kt
│   └── JalaliCalendar.kt        ← (بدون تغییر منطق)
└── ui/ ...                      ← (به‌روزرسانی برچسب‌ها و کارت‌ها)
```

فایل `computeSpatialTriangulation` و مدل‌های `TriangulationVertex`/`SpatialTriangulationResult` **حذف** و با «همسایگی» (`NeighbourhoodResult`) جایگزین شوند.

### ۵.۲ جریان داده در هر به‌روزرسانی

```
                ┌──────────── موازی (Executor یا coroutine) ───────────┐
 Open-Meteo A: مدل‌های قطعی در ۱۷ نقطه‌ی همسایگی (۴ متغیر بارشی)
 Open-Meteo B: مرکز، همه‌ی متغیرها، ۳ مدل (ecmwf_ifs, icon_global, gfs_seamless)
 Open-Meteo C: مرکز، باد ۷۰۰ و ۵۰۰ هکتوپاسکال از ecmwf_ifs025
 Ensemble D1: ecmwf_ifs025 (۵۱ عضو)      Ensemble D2: icon_seamless (۴۰ عضو)
 RainViewer E: فهرست فریم + کاشی پوشش + ۴ کاشی × چند فریم آخر
 Ogimet F: SYNOP صفی‌آباد (۲۴ ساعت گذشته؛ حداکثر یک بار در ساعت)
                └──────────────────────────────────────────────────────┘
                                   ↓
 RadarAnalyzer: سلول‌ها، حرکت، زمان ورود به دایره‌ی ۲۰ km
                                   ↓
 ForecastEngine: احتمال محدوده/نقطه، مقدار P50/P90، ترکیب nowcast،
                 محافظ اقلیمی، ویرگا، پتانسیل همرفتی، سطح هشدار، بازه‌ها
                                   ↓
 VerificationStore (ثبت)  →  Repository (کش)  →  UI و اعلان
```

### ۵.۳ فایل `Tuning.kt` (کامل؛ همین مقادیر را بگذار)

```kotlin
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
    const val CLUTTER_MIN_FRAMES = 6     // پیکسلی که در ۶ فریم پیاپی ثابت است
    const val CLUTTER_MAX_DBZ_STD = 2.0

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

    // فراخوانی منابع
    const val REFRESH_INTERVAL_MIN = 30
    const val SYNOP_MIN_INTERVAL_MIN = 60
    const val HTTP_TIMEOUT_MS = 12_000
}
```

---

## بخش ۶ — فازهای اجرا

### فاز ۱ — ثابت‌ها، ابزار جغرافیایی، پیکربندی منطقه

**کارها:**

۱. فایل `Tuning.kt` را دقیقاً مثل بخش ۵.۳ بساز.

۲. فایل `RegionConfig.kt`:

```kotlin
package com.amin.weather.core

data class GeoPoint(val id: String, val nameFa: String, val lat: Double, val lon: Double)

object RegionConfig {
    val CENTER = GeoPoint("shamsabad", "شمس‌آباد", 32.2978, 48.4283)

    // ۱۷ نقطه‌ی همسایگی: مرکز + ۸ نقطه در ۱۰ km + ۸ نقطه در ۲۰ km (سمت‌های ۰، ۴۵، …، ۳۱۵)
    // مقادیر از GeoUtils.destination محاسبه می‌شوند؛ جدول زیر برای آزمون است.
    val NEIGHBOURHOOD: List<GeoPoint> by lazy {
        val list = mutableListOf(CENTER)
        val dirs = listOf("N","NE","E","SE","S","SW","W","NW")
        for (ring in listOf(10.0, 20.0)) {
            dirs.forEachIndexed { i, d ->
                val p = GeoUtils.destination(CENTER.lat, CENTER.lon, i * 45.0, ring)
                list += GeoPoint("r${ring.toInt()}_$d", "${ring.toInt()}km-$d", p.first, p.second)
            }
        }
        list
    }

    val SYNOP_STATIONS = listOf(
        GeoPoint("40794", "صفی‌آباد دزفول", 32.2667, 48.4167)
    )
}
```

مقادیر مورد انتظار نقاط همسایگی (برای آزمون، با دقت ۰٫۰۰۰۱ درجه):

```
10km: N 32.3877,48.4283  NE 32.3614,48.5036  E 32.2978,48.5347  SE 32.2342,48.5035
      S 32.2079,48.4283  SW 32.2342,48.3531  W 32.2978,48.3219  NW 32.3614,48.3530
20km: N 32.4777,48.4283  NE 32.4249,48.5790  E 32.2976,48.6411  SE 32.1705,48.5786
      S 32.1179,48.4283  SW 32.1705,48.2780  W 32.2976,48.2155  NW 32.4249,48.2776
```

۳. فایل `GeoUtils.kt` (کد مرجع؛ همین را پیاده کن):

```kotlin
package com.amin.weather.core

import kotlin.math.*

object GeoUtils {
    const val EARTH_R_KM = 6371.0088

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_R_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** سمت از نقطه‌ی ۱ به ۲، درجه از شمال جغرافیایی، ساعتگرد، 0..360 */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destination(lat: Double, lon: Double, bearingDeg: Double, distKm: Double): Pair<Double, Double> {
        val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
        val b = Math.toRadians(bearingDeg); val dr = distKm / EARTH_R_KM
        val p2 = asin(sin(p1) * cos(dr) + cos(p1) * sin(dr) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(dr) * cos(p1), cos(dr) - sin(p1) * sin(p2))
        return Pair(Math.toDegrees(p2), Math.toDegrees(l2))
    }

    /** مختصات محلی تخت (km): x به شرق، y به شمال، نسبت به مرکز. فقط تا ~۲۰۰ km دقت کافی دارد. */
    fun toLocalKm(lat0: Double, lon0: Double, lat: Double, lon: Double): Pair<Double, Double> {
        val d = haversineKm(lat0, lon0, lat, lon)
        val b = Math.toRadians(bearingDeg(lat0, lon0, lat, lon))
        return Pair(d * sin(b), d * cos(b))
    }

    // ریاضی کاشی Web-Mercator (Slippy map)
    fun lonToTileX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)
    fun latToTileY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
    }
    fun tileXToLon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0
    fun tileYToLat(y: Double, z: Int): Double =
        Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl z)))))

    /** برچسب فارسی ۱۶ جهته؛ ورودی سمت «به سوی» یا «از سوی» است؛ فراخواننده معنا را مشخص کند. */
    private val DIRS16 = listOf(
        "شمال", "شمال‌شمال‌شرق", "شمال‌شرق", "شرق‌شمال‌شرق", "شرق", "شرق‌جنوب‌شرق", "جنوب‌شرق", "جنوب‌جنوب‌شرق",
        "جنوب", "جنوب‌جنوب‌غرب", "جنوب‌غرب", "غرب‌جنوب‌غرب", "غرب", "غرب‌شمال‌غرب", "شمال‌غرب", "شمال‌شمال‌غرب")
    fun dir16Fa(deg: Double): String = DIRS16[(((deg % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16]
}
```

**آزمون‌های واحد فاز ۱** (فایل `app/src/test/java/com/amin/weather/GeoUtilsTest.kt`):

| آزمون | مقدار مورد انتظار |
|---|---|
| `haversineKm(شمس‌آباد → صفی‌آباد 32.2667,48.4167)` | 3.6 ± 0.1 |
| `bearingDeg(همان)` | 198 ± 1 |
| `haversineKm(شمس‌آباد → اندیمشک 32.4578,48.3567)` | 19.0 ± 0.1 |
| `haversineKm(شمس‌آباد → شوش 32.1942,48.2436)` | 20.8 ± 0.1 |
| `haversineKm(شمس‌آباد → اهواز 31.319,48.684)` | 111.5 ± 0.5 |
| `lonToTileX(48.4283, 7)` | 81.219 ± 0.001 |
| `latToTileY(32.2978, 7)` | 51.855 ± 0.001 |
| `tileYToLat(52.0, 7)` | 31.952 ± 0.001 |
| `tileXToLon(81.0, 7)` | 47.8125 |
| همه‌ی ۱۷ نقطه‌ی `NEIGHBOURHOOD` | مطابق جدول بالا، فاصله از مرکز ۰/۱۰/۲۰ km با خطای کمتر از ۰٫۰۵ km |

---

### فاز ۲ — کلاینت‌های داده (Open-Meteo، Ensemble، SYNOP) و حذف کدهای بی‌اعتبار

**۲.۱ `SourceResult.kt`:**

```kotlin
package com.amin.weather.core

enum class SourceStatus { OK, PARTIAL, FAILED, UNAVAILABLE }

data class SourceResult<T>(
    val source: String,          // مثلاً "open-meteo/ecmwf_ifs"
    val status: SourceStatus,
    val data: T?,
    val message: String? = null, // دلیل خطا یا مدل جایگزین استفاده‌شده
    val fetchedAtEpochSec: Long = System.currentTimeMillis() / 1000
)
```

**۲.۲ `Http.kt`:** تابع `fun get(url: String, timeoutMs: Int): HttpResponse` که `code`، `body` (ByteArray) و `error` را برگرداند. User-Agent: `AminWeather/5.0 (Android)`. هیچ استثنایی را نبلع؛ پیام آن را در `error` بگذار. کد ۴۲۹ را جدا تشخیص بده تا تلاش دوباره تا ۳۰ دقیقه انجام نشود.

**۲.۳ `OpenMeteoClient.kt` را بازنویسی کن.** موارد زیر را حذف کن: `REGIONAL_STATIONS`، `fetchPerimeterStationsBatch`، `fetchLiveStationTelemetry` (به شکل فعلی)، `fetchMinutely15`، همه‌ی بخش رادار (به `RadarClient` منتقل می‌شود)، `fallbackPerimeterStations`.

تابع‌های جدید:

(الف) `fetchNeighbourhood(): SourceResult<Map<String, ModelGrid>>`
- یک درخواست با هر ۱۷ نقطه (`latitude=...&longitude=...` با کاما، به ترتیب `RegionConfig.NEIGHBOURHOOD`).
- `hourly=precipitation,showers,cape,cloud_cover`
- `models=ecmwf_ifs,icon_global` و `forecast_days=3`, `timezone=GMT`, `timeformat=unixtime`.
- خروجی: برای هر مدل یک `ModelGrid` شامل `times: LongArray` و `precip[pointIndex][hour]`، `showers`، `cape`، `cloud`.
- **سازوکار جایگزین:** اگر پاسخ `{"error":true,"reason":...}` داد یا آرایه‌ی یک مدل کاملاً `null` بود، همان درخواست را با شناسه‌ی جایگزین تکرار کن (`ecmwf_ifs` → `ecmwf_ifs025`، `icon_global` → `icon_seamless`). مدل جایگزین را در `message` ثبت کن.

(ب) `fetchCenterDetail(): SourceResult<CenterDetail>`
- فقط نقطه‌ی مرکز.
- `hourly=temperature_2m,dew_point_2m,relative_humidity_2m,precipitation,rain,showers,weather_code,cape,convective_inhibition,lifted_index,cloud_cover,cloud_cover_low,cloud_cover_mid,wind_speed_10m,wind_direction_10m,wind_gusts_10m`
- `models=ecmwf_ifs,icon_global,gfs_seamless` (جایگزین GFS: `gfs_global`).
- `current=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m` (این `current` **خروجی مدل** است و در UI با برچسب «وضعیت فعلی (برآورد مدل)» نمایش داده شود).
- پارسر: برای هر متغیر، کلید `var_model` را بخوان؛ اگر نبود، کلید `var` را بخوان.

(ج) `fetchSteeringWind(): SourceResult<SteeringWind>`
- مرکز، `models=ecmwf_ifs025`، `hourly=wind_speed_700hPa,wind_direction_700hPa,wind_speed_500hPa,wind_direction_500hPa`.
- «باد هدایت‌کننده» = میانگین برداری باد ۷۰۰ و ۵۰۰. برای هر ساعت: `u = -spd*sin(dir)`, `v = -spd*cos(dir)` (چون جهت باد «از سوی» است)؛ میانگین `u,v` دو تراز؛ سرعت = `hypot(u,v)`؛ سمت حرکت «به سوی» = `(atan2(u, v) in deg + 360) % 360`.

(د) `fetchEnsemble(model: String): SourceResult<EnsembleSet>`
- آدرس ensemble، فقط مرکز، `hourly=precipitation`، `forecast_days=3`, `timezone=GMT`, `timeformat=unixtime`.
- برای `ecmwf_ifs025` و `icon_seamless` **دو درخواست جدا** بفرست (جایگزین‌های ICON: `icon_global`، بعد `dwd_icon_seamless_eps`).
- `EnsembleSet(times: LongArray, members: Array<DoubleArray>)` — هر عضو یک آرایه‌ی ساعتی. اعضای تماماً `null` را دور بریز. مقدار `null` تکی داخل یک عضو را `NaN` بگذار و در شمارش احتمال، آن ساعت از آن عضو را نادیده بگیر.

**۲.۴ `SynopClient.kt`:**

- درخواست: `block=40794`، `begin` = ۲۴ ساعت قبل، `end` = اکنون، به UTC با قالب `yyyyMMddHHmm`.
- هر خط را با اولین `,` تا ششمین `,` جدا کن: `id, yyyy, MM, dd, HH, mm`، باقی‌مانده متن SYNOP است. خطوط حاوی `NIL` را دور بریز.
- رمزگشایی FM-12 SYNOP (فقط بخش ۱ و گروه‌های ۶ و ۷ بخش ۳):
  - گروه‌ها با فاصله جدا می‌شوند؛ `=` آخر را حذف کن. ترتیب: `AAXX`، `YYGGiw`، `IIiii`، `iRiXhVV`، `Nddff`، سپس گروه‌های عددی.
  - `iR` (رقم اول `iRiXhVV`): `0` یا `1` یعنی گروه بارش در بخش ۱ هست؛ `2` یعنی در بخش ۳ هست؛ `3` یعنی **بارش صفر** (گروه حذف شده چون نبارید)؛ `4` یعنی اندازه‌گیری نشده (`null`).
  - `iX` (رقم دوم): `1`، `4` یا `7` یعنی گروه `7wwW1W2` موجود است (`7` یعنی ایستگاه خودکار با جدول کد `wawa`؛ در این حالت فقط «بارش دارد/ندارد» را با `wawa` در بازه‌ی ۴۰ تا ۹۹ تشخیص بده). `2`، `3`، `5`، `6` یعنی گروه نیست → `ww = null` ولی اگر `iX=2` یا `5` بود «پدیده‌ی مهمی نبوده» (`isRainNow = false`).
  - `1snTTT`: دما؛ `sn=0` مثبت، `sn=1` منفی؛ `TTT` به دهم درجه.
  - `2snTdTdTd`: نقطه شبنم با همان قاعده (اگر `sn=9` بود، رطوبت نسبی است: `29UUU`).
  - `6RRRtR`: `RRR` = میلی‌متر (`001` تا `988`)، `990` = اثر (trace، بگذار ۰٫۰۵)، `991` تا `999` = ۰٫۱ تا ۰٫۹ mm، `000` = صفر. `tR`: `1`=۶ساعت، `2`=۱۲، `3`=۱۸، `4`=۲۴، `5`=۱، `6`=۲، `7`=۳، `8`=۹، `9`=۱۵ ساعت، `0` یا `/` = نامشخص.
  - `7wwW1W2`: `ww` = هوای حاضر. `50..59` نم‌نم، `60..69` باران، `80..84` رگبار باران، `91..99` رعدوبرق با/بدون بارش، `17` رعد بدون بارش، `13` برق، `18` تندباد (squall)، `20..29` بارش/رعد در ساعت گذشته ولی نه اکنون، `06..09`، `30..35` گردوخاک.
  - بعد از گروه `333`: گروه‌های `6RRRtR` را هم بخوان (اگر `iR=2`).
- خروجی: `SynopObs(timeUtcEpochSec, tempC, dewC, rh, precipMm, precipPeriodH, ww, isRainNow, isThunder, isRecentPrecip)`.
- کش در حافظه و فایل؛ اگر کمتر از `SYNOP_MIN_INTERVAL_MIN` از آخرین درخواست گذشته، از کش بخوان.

**آزمون واحد SYNOP** (رشته‌ی نمونه‌ی ساختگی برای تست رمزگشا):

```
40794,2026,11,06,09,00,AAXX 06091 40794 11970 81205 10183 20155 30012 40098 52010 60052 76166 83530=
```

| فیلد | مقدار مورد انتظار |
|---|---|
| زمان | 2026-11-06 09:00 UTC |
| `iR` / `iX` | 1 / 1 |
| دما | 18.3 |
| نقطه شبنم | 15.5 |
| بارش | 5.0 mm در ۱۲ ساعت |
| `ww` | 61 → `isRainNow = true`, `isThunder = false` |

و برای `... 41/// ...` (یعنی `iR=4`) باید `precipMm = null` باشد؛ و برای `iR=3` باید `precipMm = 0.0` باشد.

---

### فاز ۳ — رادار واقعی (RainViewer) با رمزگشایی پیکسل

**۳.۱ کاشی‌های لازم.** مرکز در زوم ۷ روی `x=81.219, y=51.855` است. برای شعاع پایش ۱۵۰ km، چهار کاشی کافی است و یک موزاییک ۵۱۲×۵۱۲ می‌سازند:

```
(80,51) (81,51)      ← ردیف بالا (عرض 31.952 تا 34.307)
(80,52) (81,52)      ← ردیف پایین (عرض 29.535 تا 31.952)
مرز طولی: x=80 → 45.000 تا 47.8125 ؛ x=81 → 47.8125 تا 50.625
```

مرکز در موزاییک: پیکسل `(312, 219)` (یعنی `(81.219-80)*256 = 312`، `(51.855-51)*256 = 219`). اندازه‌ی هر پیکسل در این عرض حدود ۱٫۰۳ km است. این کاشی‌ها را **از روی مختصات محاسبه کن** (`floor(lonToTileX)`، …) نه به صورت ثابت، تا اگر مرکز عوض شد کد درست بماند.

**۳.۲ `RadarClient.kt`:**

۱. `weather-maps.json` را بگیر. اگر `radar.past` خالی بود → `UNAVAILABLE`.
۲. سن آخرین فریم: `now - past.last().time`. اگر بیشتر از `RADAR_MAX_FRAME_AGE_MIN` دقیقه بود → وضعیت `PARTIAL` با پیام «رادار به‌روز نیست» و در ترکیب nowcast وزن صفر.
۳. کاشی پوشش مرکز را بگیر (`/v2/coverage/0/256/7/81/51/0/0_0.png`). پیکسل مرکز (56,219) و ۲۵ پیکسل اطرافش را بخوان. اگر اکثر آن‌ها **سیاه و مات** بودند (آلفا > 200 و RGB نزدیک صفر) → `UNAVAILABLE` با پیام «منطقه زیر پوشش رادار نیست». نتیجه‌ی پوشش را ۲۴ ساعت کش کن.
۴. برای حداکثر ۶ فریم آخر (۶۰ دقیقه)، چهار کاشی را بگیر. **کاشی‌ها را بر اساس `path` در دیسک کش کن** (فریم‌ها تغییر نمی‌کنند؛ هر به‌روزرسانی فقط فریم‌های جدید را دانلود می‌کند). جمع درخواست‌ها در یک به‌روزرسانی معمولی کمتر از ۱۰ است.
۵. رمزگشایی PNG در اندروید:

```kotlin
val opts = BitmapFactory.Options().apply {
    inPremultiplied = false
    inPreferredConfig = Bitmap.Config.ARGB_8888
}
val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) // اگر null → خطای رمزگشایی
val pixels = IntArray(256 * 256)
bmp.getPixels(pixels, 0, 256, 0, 0, 256, 256)
```

۶. تبدیل پیکسل به dBZ:

```kotlin
object UniversalBlue {
    // RGB رنگ‌های مات باران از ۱۵ تا ۶۵ dBZ (منبع: rainviewer_api_colors_table.csv)
    private val RGB = intArrayOf(
        0x88DDEE,0x6CD1EB,0x51C5E8,0x36BAE5,0x1BAEE2,0x00A3E0,0x009AD5,0x0091CA,0x0088BF,0x007FB4,
        0x0077AA,0x0070A3,0x00699C,0x006295,0x005B8E,0x005588,0x005180,0x004E78,0x004A70,0x004768,
        0xFFEE00,0xFFE000,0xFFD200,0xFFC500,0xFFB700,0xFFAA00,0xFF9F00,0xFF9500,0xFF8B00,0xFF8100,
        0xFF4400,0xF23600,0xE62800,0xD91B00,0xCD0D00,0xC10000,0xA80000,0x8F0000,0x760000,0x5D0000,
        0xFFAAFF,0xFF9FFF,0xFF95FF,0xFF8BFF,0xFF81FF,0xFF77FF,0xFF6CFF,0xFF62FF,0xFF58FF,0xFF4EFF,
        0xFFFFFF)
    private const val FIRST_DBZ = 15
    private val cache = HashMap<Int, Int>()

    /** برمی‌گرداند: dBZ (15..65) یا -1 برای «بدون بارش/پژواک ضعیف/شفاف» */
    fun dbz(argb: Int): Int {
        val a = argb ushr 24
        if (a < 255) return -1
        val rgb = argb and 0xFFFFFF
        return cache.getOrPut(rgb) {
            var best = -1; var bestD = Int.MAX_VALUE
            for (i in RGB.indices) {
                val c = RGB[i]
                val dr = ((c shr 16) and 255) - ((rgb shr 16) and 255)
                val dg = ((c shr 8) and 255) - ((rgb shr 8) and 255)
                val db = (c and 255) - (rgb and 255)
                val d = dr*dr + dg*dg + db*db
                if (d < bestD) { bestD = d; best = i }
            }
            if (bestD > 30 * 30 * 3) -1 else FIRST_DBZ + best   // رنگ ناشناخته → نادیده
        }
    }
}
```

۷. نتیجه‌ی هر فریم: `RadarFrame(timeEpochSec, dbz: IntArray(512*512))` (مقدار -1 برای بدون بارش).

**۳.۳ `RadarAnalyzer.kt`:**

(الف) **نقشه‌ی فاصله و سمت هر پیکسل** را یک بار حساب کن: برای پیکسل موزاییک `(px, py)`:
`lon = tileXToLon(80 + (px + 0.5)/256, 7)` و `lat = tileYToLat(51 + (py + 0.5)/256, 7)`، سپس `distKm` و `bearing` نسبت به مرکز. فقط پیکسل‌های با `distKm ≤ RADAR_WATCH_RADIUS_KM` را بررسی کن.

(ب) **فیلتر کلاتر (پژواک زمینی ثابت):** پیکسلی که در همه‌ی `CLUTTER_MIN_FRAMES` فریم آخر `dBZ ≥ DBZ_RAIN` بوده و انحراف معیار dBZ آن کمتر از `CLUTTER_MAX_DBZ_STD` است، و هم‌زمان **همه‌ی** مدل‌ها در ساعت جاری برای نزدیک‌ترین نقطه بارش صفر می‌دهند → به عنوان کلاتر علامت بزن و حذف کن. فهرست پیکسل‌های کلاتر را در فایل ذخیره کن و هر روز بازسازی کن.

(ج) **آمار داخل دایره‌ی هدف** (۲۰ km) برای فریم آخر:
`maxDbzInTarget`، `wetFractionInTarget` (سهم پیکسل‌های ≥ `DBZ_RAIN`)، `meanRainRateInTarget` (mm/h).

(د) **آمار حلقه‌ها:** برای حلقه‌های ۲۰–۵۰، ۵۰–۱۰۰، ۱۰۰–۱۵۰ km و ۸ قطاع (N, NE, …) بیشینه‌ی dBZ و سهم پیکسل‌های بارانی.

(ه) **شناسایی سلول‌ها:** مؤلفه‌های همبند ۸-همسایه روی پیکسل‌های `dBZ ≥ DBZ_RAIN`. سلول‌های کوچک‌تر از `MIN_CELL_PIXELS` را دور بریز. برای هر سلول: تعداد پیکسل، مساحت (km² ≈ تعداد × مساحت پیکسل محلی)، `maxDbz`، مرکز ثقل وزن‌دار با `Z = 10^(dBZ/10)`، شعاع معادل `sqrt(area/π)`، و `minDistToCenterKm`.

(و) **ردیابی حرکت:** سلول‌های فریم آخر را با سلول‌های فریم قبلی (۱۰ دقیقه قبل) بر اساس نزدیک‌ترین مرکز ثقل جفت کن، به شرط فاصله کمتر از `TRACK_MATCH_MAX_KM`. سرعت = جابه‌جایی / اختلاف زمان. اگر سه فریم در دسترس بود، میانگین دو بردار را بگیر. سرعت خارج از بازه‌ی `[CELL_SPEED_MIN_KMH, CELL_SPEED_MAX_KMH]` را رد کن. اگر سلول جفت نشد، از **باد هدایت‌کننده** (فاز ۲، بخش ج) برای ساعت جاری استفاده کن و منبع را «باد مدل» علامت بزن.

(ز) **زمان ورود به دایره (Closest approach).** مختصات محلی مرکز ثقل سلول `P=(x,y)` km و سرعت `V=(vx,vy)` km/h. شعاع مؤثر `R = TARGET_RADIUS_KM + cellRadiusKm`:

```kotlin
val pv = x*vx + y*vy
val vv = vx*vx + vy*vy
val pp = x*x + y*y
when {
    pp <= R*R -> state = INSIDE_NOW                          // هم‌اکنون روی محدوده است
    vv < 1e-6 || pv >= 0 -> state = NOT_APPROACHING          // ساکن یا در حال دور شدن
    else -> {
        val disc = pv*pv - vv*(pp - R*R)
        if (disc < 0) state = WILL_MISS                      // از کنار محدوده رد می‌شود
        else {
            val tEnterH = (-pv - sqrt(disc)) / vv              // ساعت
            val dMin = sqrt(max(0.0, pp - pv*pv/vv))           // نزدیک‌ترین فاصله
            etaMin = tEnterH * 60
            etaLow = etaMin * (1 - ETA_UNCERTAINTY_FRAC)
            etaHigh = etaMin * (1 + ETA_UNCERTAINTY_FRAC)
            state = APPROACHING
        }
    }
}
```

مثال آزمون: سلول در فاصله‌ی ۶۰ km با سمت ۲۵۰ درجه، حرکت به سوی ۷۰ درجه با سرعت ۳۶ km/h، `R=25` → `tEnter ≈ 58.3 دقیقه`، `dMin ≈ 0`. همان سلول با حرکت به سوی ۱۰۰ درجه → `WILL_MISS` و `dMin ≈ 30 km`. همان سلول با حرکت به سوی ۲۵۰ درجه → `NOT_APPROACHING`.

(ح) **تبدیل dBZ به شدت بارش:** `Z = 10^(min(dBZ, DBZ_CAP_FOR_RAINRATE)/10)`. برای `dBZ < DBZ_STRONG` از مارشال–پالمر `R = (Z/200)^(1/1.6)` و برای `dBZ ≥ DBZ_STRONG` از رابطه‌ی همرفتی `R = (Z/300)^(1/1.4)` استفاده کن. مقادیر آزمون: 20 dBZ → 0.65، 30 → 2.73، 40 → 12.24، 50 → 63.4 mm/h (±۲٪).

(ی) **خروجی `RadarNowcast`:**
`available`, `frameAgeMin`, `coverageOk`, `maxDbzInTarget`, `wetFractionInTarget`, `rainRateInTargetMmH`, `cells` (۵ سلول نزدیک‌تر با فاصله، سمت، `maxDbz`، سرعت، جهت حرکت، `state`، `etaLow..etaHigh`، `motionSource`)، `sectorSummary` و `firstArrival` (زودترین سلول `APPROACHING` با `maxDbz ≥ DBZ_RAIN`).

---

### فاز ۴ — بازنویسی `ForecastEngine` (هسته‌ی دقت)

برای هر ساعت `h` از ۰ تا ۴۷ (UTC، شروع از ساعت جاری)، مقادیر زیر را حساب کن.

**۴.۱ احتمال همسایگی مدل قطعی** (برای هر مدل m از ECMWF و ICON):
`Pneigh_m(h) = (تعداد نقاط از ۱۷ نقطه با precip ≥ WET_HOUR_MM) / (تعداد نقاط دارای داده)`.
این عدد «احتمال این‌که جایی در محدوده ببارد» را از نگاه مدل نشان می‌دهد.

**۴.۲ احتمال ensemble در مرکز:**
- ECMWF ENS: پنجره‌ی ۳ ساعته‌ی منتهی به ساعت h: سهم اعضایی که `sum(precip[h-2..h]) ≥ WET_3H_MM`.
- ICON EPS: سهم اعضایی که `precip[h] ≥ WET_HOUR_MM`.
- `Pens(h)` = میانگین وزن‌دار با تعداد اعضا: `(51·Pecmwf + 40·Picon) / 91`. اگر یکی در دسترس نبود، فقط دیگری.

**۴.۳ احتمال مدل برای محدوده:**
`Pmodel = W_ENSEMBLE·Pens + W_NEIGH_ECMWF·Pneigh_ecmwf + W_NEIGH_ICON·Pneigh_icon`. اگر منبعی موجود نبود، وزن‌ها را دوباره نرمال کن (بر جمع وزن‌های موجود تقسیم کن). اگر هیچ منبع مدلی نبود، `Pmodel = null` و UI «نامشخص».

**۴.۴ احتمال نقطه‌ی مرکز:** همان فرمول ولی به‌جای `Pneigh` از مقدار تک‌نقطه‌ی مرکز (۰ یا ۱ برای هر مدل) استفاده کن. انتظار طبیعی: `Ppoint ≤ Parea`. اگر نتیجه بیشتر شد، `Ppoint = Parea`.

**۴.۵ احتمال nowcast رادار (فقط h = 0, 1, 2)،** فقط اگر `radar.available` و `frameAgeMin ≤ RADAR_MAX_FRAME_AGE_MIN`:

| شرط | `Pnow` |
|---|---|
| `wetFractionInTarget ≥ 0.05` و ساعت h = 0 | `min(0.95, 0.6 + 4·wetFraction)` |
| یک سلول `APPROACHING` با زمان ورود داخل پنجره‌ی ساعت h و `maxDbz ≥ DBZ_STRONG` | 0.85 |
| همان با `DBZ_RAIN ≤ maxDbz < DBZ_STRONG` | 0.65 |
| سلول `WILL_MISS` با `dMin ≤ TARGET_RADIUS_KM + 15` | 0.25 |
| هیچ پژواکی در ۵۰ km و هیچ سلول نزدیک‌شونده | 0.03 |
| در غیر این صورت | برابر `Pmodel` (رادار اطلاعات اضافه ندارد) |

ترکیب: `Parea = w·Pnow + (1−w)·Pmodel` با `w = NOWCAST_WEIGHT[h]`. اگر رادار در دسترس نبود، `w = 0`.

برای نقطه‌ی مرکز: `PnowPoint(h=0) = 0.9` اگر هر پیکسل در شعاع ۳ km از مرکز `dBZ ≥ DBZ_RAIN` دارد؛ در غیر این صورت `PnowPoint = 0.6·Pnow`. سپس `Ppoint = w·PnowPoint + (1−w)·PpointModel` و در پایان قاعده‌ی `Ppoint ≤ Parea` اعمال شود.

**۴.۶ مشاهده‌ی صفی‌آباد (SYNOP):** اگر آخرین گزارش کمتر از ۹۰ دقیقه پیش است و `isRainNow` یا `isThunder` بود → برای h = 0: `Parea = max(Parea, 0.9)` و `Ppoint = max(Ppoint, 0.8)`. اگر گزارش تازه `ww < 20` (بدون بارش) و رادار هم هیچ پژواکی داخل دایره ندارد → برای h = 0: `Ppoint = min(Ppoint, 0.15)`. **هرگز SYNOP قدیمی‌تر از ۹۰ دقیقه را برای nowcast به کار نبر.**

**۴.۷ گزارش کاربر:** فقط اگر کمتر از `USER_REPORT_TTL_MIN` دقیقه پیش ثبت شده. اثر آن با زمان خطی کم شود: `k = 1 − age/TTL`. اگر پدیده «باران/رگبار/رعدوبرق» بود: `Parea(h=0) = max(Parea, k·USER_REPORT_MAX_PROB)`. دمای کاربر فقط برای نمایش «دمای گزارش‌شده توسط شما» است و **جایگزین هیچ داده‌ای نمی‌شود**.

**۴.۸ محافظ اقلیمی:** اگر ماه جاری (به وقت ایران) در `DRY_MONTHS` است و `Pmodel < DRY_MONTH_STRONG_SIGNAL` و رادار پژواک ≥ `DBZ_RAIN` در ۵۰ km نمی‌بیند → `Parea = min(Parea, DRY_MONTH_PROB_CAP)`.

**۴.۹ ویرگا:** اگر برای ساعت h اختلاف دما و نقطه‌ی شبنم مدل ECMWF (`T − Td`) ≥ `VIRGA_DEW_DEPRESSION_C` و رطوبت نسبی ≤ `VIRGA_RH_MAX` است، و منبع بارش فقط رادار یا ابر میانی است: `Ppoint *= VIRGA_PROB_FACTOR` و پرچم `virgaRisk = true` (هشدار تندباد موضعی). برای h = 0 اگر SYNOP تازه موجود است، از دما و نقطه‌ی شبنم SYNOP استفاده کن.

**۴.۱۰ مقدار بارش (mm):**
- `amountP50(h)` = میانه‌ی مقادیر: اعضای ICON EPS در ساعت h + اعضای ECMWF ENS (مجموع ۳ ساعته تقسیم بر ۳) + ECMWF HRES و ICON مرکز.
- `amountP90(h)` = صدک ۹۰ همان مجموعه، و حداقل برابر بیشینه‌ی مقدار ECMWF HRES در ۱۷ نقطه‌ی همسایگی.
- nowcast (h = 0..2، با همان وزن w): `amountNow = rainRate(maxDbz سلول) × زمان ماندگاری`. زمان ماندگاری = `min(1h, 2·cellRadiusKm / cellSpeedKmh)`. برای `INSIDE_NOW` از `rainRateInTargetMmH` استفاده کن. ترکیب: `P50 = w·amountNow·Pnow + (1−w)·P50model`. `P90 = max(P90model, amountNow)`.
- **هرگز از احتمال، مقدار بارش نساز.**

**۴.۱۱ پتانسیل همرفتی (۰ تا ۱۰۰، شاخص توضیحی، نه احتمال):**

```
cape = ECMWF HRES cape (J/kg)؛ cin = convective_inhibition (J/kg، مثبت)؛ li = GFS lifted_index
score  = 45 · clamp(cape / 1500, 0, 1)
score += 15 · clamp((−li) / 6, 0, 1)                     // اگر li موجود نیست، این جمله صفر
score += 25 · Pens(h)
score += 15 · clamp((Td − 5) / 10, 0, 1)                 // رطوبت لایه‌ی سطحی
if (abs(cin) > 100) score *= 0.6                          // لایه‌ی بازدارنده‌ی قوی (علامت CIN را نادیده بگیر)
// هر جمله‌ای که ورودی‌اش null است صفر حساب شود؛ اگر cape هم null بود، کل score = null
```

برچسب: `score ≥ 60` و `Parea ≥ 0.4` → «احتمال رگبار همرفتی و رعدوبرق»؛ `score ≥ 35` → «ناپایداری متوسط»؛ در غیر این صورت «پایدار».

**۴.۱۲ اطمینان (Confidence، ۰ تا ۱۰۰):** `100 × (1 − 2·|Pens − Pneigh_ecmwf|)` محدود به ۰ تا ۱۰۰، و اگر رادار به‌روز و پوشش‌دار است برای h ≤ ۲، ۱۵ امتیاز اضافه (حداکثر ۱۰۰). در UI به صورت «توافق منابع: خوب / متوسط / ضعیف» (≥ ۷۰ / ≥ ۴۰ / کمتر).

**۴.۱۳ بازه‌ها (۱، ۳، ۶، ۱۲، ۲۴ ساعت):**
- احتمال بارش در بازه را **با ضرب احتمال‌های ساعتی حساب نکن** (ساعت‌ها به هم وابسته‌اند و ضرب، احتمال را بیش از واقع بالا می‌برد). به‌جای آن: `PensBlock` = سهم اعضای ensemble که مجموع بارش آن‌ها در کل بازه ≥ `WET_HOUR_MM` است؛ و `Pblock = max(PensBlock, بیشینه‌ی Parea(h) در ساعت‌های بازه)`.
- `amountP50Block` = میانه‌ی مجموع بارش اعضا در بازه؛ `amountP90Block` = صدک ۹۰.
- دمای بازه: کمینه و بیشینه جدا (`tempMin`, `tempMax`)، نه «میانگین» با مقدار بیشینه.
- باد: بیشینه‌ی تندباد و جهت غالب (میانگین برداری)، نه فقط ردیف اول.

**۴.۱۴ سطح هشدار (یک تابع واحد `decideTier()` که هم داشبورد و هم اعلان از آن استفاده کنند):**

| سطح | شرط (کافی است یکی برقرار باشد) |
|---|---|
| قرمز | رادار: `maxDbzInTarget ≥ DBZ_SEVERE`؛ یا سلول `APPROACHING` با `maxDbz ≥ DBZ_STRONG` و `etaLow ≤ RED_ETA_MIN`؛ یا SYNOP تازه با رعدوبرق؛ یا `P(≥ HEAVY_6H_MM در ۶ ساعت آینده) ≥ RED_PROB_HEAVY_6H` از ensemble |
| نارنجی | رادار: `maxDbzInTarget ≥ DBZ_RAIN + 5` یا `wetFractionInTarget ≥ 0.1`؛ یا سلول `APPROACHING` با `etaLow ≤ ORANGE_ETA_MIN`؛ یا SYNOP تازه با باران؛ یا `Pblock(6h) ≥ ORANGE_PROB_WET_6H` |
| زرد | `Pblock(12h) ≥ YELLOW_PROB_WET_12H`؛ یا پتانسیل همرفتی ≥ ۶۰ در ۱۲ ساعت آینده؛ یا سلول `WILL_MISS` نزدیک؛ یا پژواک ≥ `DBZ_STRONG` در حلقه‌ی ۵۰–۱۵۰ km در قطاع‌های غرب/جنوب‌غرب/شمال‌غرب |
| سبز | هیچ‌کدام |

متن هر سطح باید **دلیل واقعی** را بگوید (مثلاً «سلول بارشی ۳۸ dBZ در ۴۲ km جنوب‌غرب، در حال حرکت به سوی شمال‌شرق با سرعت ۳۵ km/h؛ ورود به محدوده بین ۵۰ تا ۹۰ دقیقه دیگر»). متن «رگبار شدید و صاعقه» فقط وقتی نوشته شود که شرط قرمز مربوط به همان پدیده برقرار است.

**۴.۱۵ خروجی:** ساختار JSON و مدل‌های داده را مطابق فاز ۵ به‌روز کن. همه‌ی اعداد خروجی با گردکردن: احتمال به عدد صحیح درصد، بارش به ۰٫۱ mm، دما به ۰٫۱ درجه.

**آزمون‌های واحد فاز ۴** (با داده‌ی ساختگی، بدون اینترنت):

۱. همه‌ی ۱۷ نقطه و همه‌ی اعضا بارش صفر، رادار بی‌پژواک → `Parea < 0.05`، سطح سبز.
۲. همان، ولی رادار سلول ۴۰ dBZ با `APPROACHING` و `etaLow = 20` → سطح قرمز.
۳. ماه ژوئیه، `Pmodel = 0.2`، رادار خالی → `Parea ≤ 0.05`.
۴. گزارش کاربر «باران» با عمر ۹۰ دقیقه → بی‌اثر (`TTL = 60`).
۵. ensemble: ۲۶ عضو از ۵۱ با مجموع ۳ ساعته ≥ ۰٫۳ → `Pecmwf = 0.51`.
۶. `Ppoint` هیچ‌وقت از `Parea` بیشتر نشود.
۷. وقتی همه‌ی منابع مدل `FAILED` هستند، `Parea = null` و UI «نامشخص» (نه ۱۰٪).

---

### فاز ۵ — مدل‌های داده، مخزن، گزارش کاربر، اعلان‌ها

**۵.۱ `WeatherModels.kt`:**
- `ForecastRow` را به این شکل تغییر بده (فیلدهای قدیمی وابسته به مثلث را حذف کن):
  `timeEpochSec, timeIran, probArea: Double?, probPoint: Double?, amountP50: Double?, amountP90: Double?, tempC: Double?, dewC: Double?, rh: Double?, cloud: Double?, gustKmh: Double?, windDirFromDeg: Double?, convScore: Double?, convLabel, virgaRisk, confidence: Int?, sources: List<String>, eventLabel`
- `RadarNowcastInfo` را با خروجی فاز ۳ (ی) جایگزین کن.
- `PerimeterStationInfo`, `PerimeterNetworkSummary`, `TriangulationVertex`, `SpatialTriangulationResult` حذف؛ به‌جای آن `NeighbourhoodSummary(pointsWithRainNow: Int, totalPoints: Int, wettestDirectionFa: String?)` و `SourceHealth(list of SourceResult status for UI)`.
- `StationTelemetryInfo` به `ObservedSynop?` (از SYNOP) و `ModelCurrent` (از Open-Meteo current) **جدا** شود.
- `AlertTier.ORANGE.title` را به «توده‌ی بارشی در راه» و `RED.title` را به «هشدار فوری بارش/رعدوبرق در محدوده» تغییر بده (بدون ذکر عدد کیلومتر ثابت).

**۵.۲ گزارش کاربر (`SkyObservationReport`):**
- فیلد `createdAtEpochMs` اضافه کن.
- عکس را به صورت فایل JPEG (حداکثر ۶۰۰ پیکسل، کیفیت ۷۰) در `filesDir/obs/` ذخیره کن و فقط مسیر را در SharedPreferences نگه دار. base64 را از کش JSON داشبورد حذف کن.
- `getSavedSkyObservation()` اگر عمر گزارش بیشتر از `USER_REPORT_TTL_MIN` است، `null` برگرداند (ولی فایل را برای تاریخچه نگه دارد).

**۵.۳ مخزن (`WeatherRepository`):**
- کش JSON نسخه‌دار شود (`"schema": 5`)؛ اگر نسخه‌ی کش متفاوت بود، آن را نادیده بگیر.
- هنگام نمایش کش، زمان تولید آن در UI بیاید («آخرین به‌روزرسانی موفق: …») و اگر بیشتر از ۲ ساعت قدیمی است، هشدار رادار و nowcast از کش **نمایش داده نشود** (فقط پیش‌بینی مدل).

**۵.۴ اعلان‌ها (`WeatherNotificationManager`):**
- `evaluateDashboard` فقط از `decideTier()` و دلیل‌های آن استفاده کند؛ آستانه‌های تکراری حذف شوند.
- اعلان فقط وقتی ارسال شود که سطح **بالاتر رفته** باشد یا سطح قرمز ۶۰ دقیقه ادامه پیدا کرده باشد (cooldown از `Tuning`).
- زمان‌بندی پس‌زمینه با `REFRESH_INTERVAL_MIN` بماند. در صفحه‌ی تنظیمات هشدار یک خط راهنما اضافه شود: «برای دریافت هشدار در گوشی‌های شیائومی (HyperOS)، در تنظیمات باتری این اپ گزینه‌ی بدون محدودیت (No restrictions) را انتخاب کنید.»

---

### فاز ۶ — ثبت و ارزیابی (Verification) و کالیبراسیون تدریجی

**۶.۱ `VerificationStore.kt`:** در هر به‌روزرسانی، برای ساعت‌های آینده‌ی ۱، ۳، ۶ و ۱۲، مقدار `probPoint` و `amountP50` را با زمان هدف در فایل `filesDir/verify.jsonl` ذخیره کن (هر خط یک JSON). داده‌ی بیش از ۱۸۰ روز را پاک کن.

**۶.۲ تطبیق با SYNOP صفی‌آباد:** هر بار SYNOP جدیدی رسید، برای بازه‌ی گزارش (`precipPeriodH`) «باریدن یا نباریدن» واقعی (`precipMm ≥ 0.2`) را با پیش‌بینی‌های ذخیره‌شده‌ی همان بازه مقایسه کن.

**۶.۳ امتیازها:** برای هر افق پیش‌بینی: امتیاز Brier = `mean((p − o)²)`، نرخ کشف (POD)، نرخ هشدار کاذب (FAR) با آستانه‌ی ۵۰٪، و نسبت بایاس مقدار بارش `sum(forecast)/sum(observed)` در روزهای بارانی.

**۶.۴ کالیبراسیون (فقط بعد از حداقل ۳۰ رویداد بارانی ثبت‌شده):** یک ضریب `probScale` در بازه‌ی [0.7, 1.3] و یک `amountScale` در بازه‌ی [0.5, 1.5] محاسبه و در `ForecastEngine` اعمال کن. قبل از ۳۰ رویداد، هر دو برابر ۱ باشند. این ضرایب و تعداد رویدادها در یک کارت «کیفیت پیش‌بینی» نمایش داده شوند.

---

### فاز ۷ — رابط کاربری، مستندات و تست نهایی

**۷.۱ UI (`WeatherDashboardScreen.kt`):**
- کارت مثلث‌بندی و فهرست «ایستگاه‌های پیرامونی» حذف شود. به‌جای آن:
  - کارت **«رادار — شعاع ۲۰ km شمس‌آباد»**: وضعیت (در دسترس / به‌روز نیست / بدون پوشش)، سن فریم، بیشینه dBZ داخل دایره، تا ۳ سلول نزدیک با فاصله، سمت، جهت حرکت، و بازه‌ی زمان ورود. یک نقشه‌ی ساده‌ی Canvas (دایره‌ی ۲۰ km، حلقه‌های ۵۰ و ۱۰۰ km، نقطه‌ی سلول‌ها با رنگ dBZ، فلش حرکت).
  - کارت **«مشاهده‌ی ایستگاه صفی‌آباد (WMO 40794)»**: زمان گزارش، دما، نقطه شبنم، بارش و دوره‌ی آن، هوای حاضر به فارسی.
  - کارت **«سلامت منابع»**: وضعیت هر منبع و مدل جایگزین استفاده‌شده.
- در جدول ساعتی، دو ستون «احتمال در محدوده» و «احتمال در شمس‌آباد» جدا نمایش داده شوند؛ ستون بارش به شکل «میانه (تا صدک ۹۰)».
- همه‌ی ستون‌ها و برچسب‌های «ایستگاه مرکزی»، «تله‌متری فرودگاه»، «شبکه‌ی دیدبانی پنج‌ضلعی» و «مدل برتر» حذف یا با برچسب صادق جایگزین شوند.
- عبارت «داده‌های هواشناسی: Open-Meteo.com · رادار: RainViewer · مشاهده: Ogimet/WMO» در پایین صفحه.

**۷.۲ مستندات:** `README.md` و `AGENTS.md` را بازنویسی کن تا معماری جدید (بخش ۵)، منابع (بخش ۳)، و ثابت‌های `Tuning.kt` را توضیح دهند. ارجاع به پوشه‌ی `weather/` پایتون و WebView را حذف کن.

**۷.۳ تست‌ها:** `ForecastEngineTest` فعلی (که آنلاین است) را حذف یا به `@Ignore` با توضیح «تست دستی آنلاین» تبدیل کن. تست‌های فاز ۱، ۲ (SYNOP)، ۳ (رنگ، Z-R، زمان ورود) و ۴ را با fixtureهای JSON در `app/src/test/resources/` بنویس. نمونه‌ی fixture برای پاسخ چندمکانی Open-Meteo (آرایه‌ی ۱۷ عضوی) و پاسخ ensemble را خودت بساز.

**۷.۴ آزمون رنگ رادار (فاز ۳):**

| ورودی ARGB | خروجی `UniversalBlue.dbz` |
|---|---|
| `0xFF00A3E0` | 20 |
| `0xFFFFAA00` | 40 |
| `0xFFC10000` | 50 |
| `0xBEDED097` (نیمه‌شفاف، ۱۴ dBZ) | -1 |
| `0x00000000` | -1 |
| `0xFF00FF00` (سبز خالص، رنگ ناشناخته) | -1 |

---

## بخش ۷ — کارهای ممنوع

- ساختن dBZ، فاصله، سرعت یا زمان رسیدن از روی اندازه‌ی فایل یا عدد ثابت.
- نامیدن خروجی مدل به عنوان «ایستگاه»، «دیدبانی» یا «تله‌متری».
- ساختن مقدار بارش (mm) از احتمال.
- استفاده از مقدار پیش‌فرض ساختگی برای داده‌ی ناموجود.
- حدس زدن شناسه‌ی WMO یا شناسه‌ی مدل بدون سازوکار جایگزین.
- درخواست بیش از حد: RainViewer بیش از ۱۰۰ در دقیقه، Ogimet بیش از یک بار در ساعت، Open-Meteo خارج از سقف رایگان.
- تغییر نام پکیج یا حذف پشتیبانی RTL.

---

## بخش ۸ — چک‌لیست پذیرش نهایی

- [ ] همه‌ی آزمون‌های واحد فازهای ۱ تا ۴ و ۷.۴ بدون اینترنت پاس می‌شوند.
- [ ] با رادار بدون پژواک و مدل خشک، هیچ هشدار نارنجی/قرمزی ساخته نمی‌شود.
- [ ] با قطع کامل اینترنت، اپ کش را با زمان آن نشان می‌دهد و nowcast قدیمی را نشان نمی‌دهد.
- [ ] اگر Ogimet یا RainViewer در دسترس نباشد، بقیه‌ی اپ کار می‌کند و کارت «سلامت منابع» علت را نشان می‌دهد.
- [ ] گزارش کاربر بعد از ۶۰ دقیقه هیچ اثری ندارد.
- [ ] سطح زرد در شرایط مربوطه ساخته می‌شود.
- [ ] هیچ عدد جادویی خارج از `Tuning.kt` در منطق پیش‌بینی وجود ندارد.
- [ ] تعداد درخواست‌های هر به‌روزرسانی ثبت (log) می‌شود و در حالت عادی: Open-Meteo ≤ ۵، RainViewer ≤ ۱۰، Ogimet ≤ ۱.
- [ ] README و AGENTS.md با کد جدید هم‌خوان‌اند.

---

## پیوست الف — منابع اطلاعات این دستورالعمل

- مختصات شمس‌آباد و صفی‌آباد: OpenStreetMap از طریق صفحات ویکی‌پدیا (Shamsabad, Dezful؛ Safiabad, Khuzestan).
- نرمال‌های اقلیمی صفی‌آباد ۱۹۹۱–۲۰۲۰: WMO Climate Normals (NOAA NCEI) از طریق ویکی‌پدیا. ارتفاع و شناسه‌ی ایستگاه 40794: Berkeley Earth، weatherandclimate.eu، tutiempo.
- میانگین بارش ۳۲۱ mm و ضریب تغییرات ۰٫۳۳ ایستگاه سینوپتیک دزفول (دوره‌ی ۱۳۶۶–۱۳۸۹): مقاله‌ی «بررسی آمار بارندگی و تغییرات آب و هوایی در کشاورزی دزفول» (سیویلیکا).
- نقش کم‌فشار سودانی در بارش‌های سنگین و همرفتی جنوب‌غرب ایران و حوضه‌ی دز: مقالات مجلات «پژوهش‌های جغرافیای طبیعی» (دانشگاه تهران) و «مخاطرات محیط طبیعی» (دانشگاه محقق اردبیلی).
- فرودگاه دزفول (OIAD): ویکی‌پدیا، SkyVector (ارتفاع ۱۴۴ متر).
- ECMWF IFS HRES ۹ کیلومتری در Open-Meteo از ۱ اکتبر ۲۰۲۵: وبلاگ و مستندات Open-Meteo.
- مدل‌های Ensemble API: مستندات Open-Meteo.
- محدودیت‌های RainViewer از ۱ ژانویه ۲۰۲۶، قالب آدرس کاشی و کاشی پوشش، جدول رنگ Universal Blue: مستندات و فایل CSV رسمی RainViewer.
- قالب درخواست Ogimet getsynop: مستندات بسته‌های R ‏`climate` و `synopR`.
