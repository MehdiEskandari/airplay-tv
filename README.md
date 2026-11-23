<p align="center"><img src="docs/art/icon.png" width="112" alt="AirPlay TV for LG webOS"></p>

# ایر پلی تی وی - AirPlay TV | LG webOS

نسخه آزمایشی ال جی با رابط تلی تیره و روشن، فارسی پیش فرض و کنترل با ریموت.

**نسخه 0.2.0 هسته واقعی UxPlay را به WebAssembly و سرویس JavaScript ال جی منتقل می کند.** این نسخه هنوز برای انتشار در فروشگاه یا اعلام سازگاری با تلویزیون های ال جی آماده نیست. هدف فعلی هسته **webOS 22 و جدیدتر** است و تست روی دستگاه واقعی انجام نشده است.

| تلی دارک | تلی لایت |
| --- | --- |
| ![رابط تلویزیونی webOS با تم تلی دارک](docs/screenshots/webos/simulator-teli-dark.jpg) | ![رابط تلویزیونی webOS با تم تلی لایت](docs/screenshots/webos/simulator-teli-light.jpg) |

این دو تصویر از اجرای همین برنامه داخل شبیه ساز رسمی ال جی گرفته شده اند؛ عکس تلویزیون واقعی نیستند. تصاویر نسخه اندروید و موبایل در معرفی این برنچ استفاده نشده اند.

## وضعیت دریافت تصویر و صدا

| بخش | نتیجه بررسی |
| --- | --- |
| زبان فارسی، انگلیسی و ترکیبی، تم ها و کنترل ریموت | آماده و تست شده |
| رابط داخل شبیه ساز رسمی LG webOS TV 26 نسخه 1.5.0 | اجرا شده |
| هسته WASM، سوکت TCP و UDP، کشف شبکه و پاسخ واقعی RTSP | تست مستقل روی مک پاس شده |
| تبدیل AAC LC و ALAC به PCM با دیکودر واقعی WASM | تست صدای غیرساکت پاس شده |
| اتصال زنده Screen Mirroring مک به هسته آزمایشی | بسته های تصویر و صدای زنده دریافت شده |
| پلیر در شبیه ساز با فایل ساختگی H264 و PCM | ۲۴۰ فریم نمایش داده شد؛ ۳۵۲۸۰۰ نمونه صدا به Web Audio رسید |
| نمایش نهایی تصویر و خروجی صدای زنده AirPlay | هنوز تایید نشده؛ دریافت بسته به تنهایی اثبات پخش نیست |
| سرویس اختصاصی Luna، هسته WASM و سوکت دریافت داخل خود شبیه ساز | اجرا شده؛ ابزار میزبان خاموش است |
| اجرای هسته روی خود تلویزیون ال جی | تایید نشده؛ تلویزیون در دسترس نیست |

هسته اکنون از سرویس خود شبیه ساز ال جی اجرا می شود. یک ابزار میزبان جداگانه هم برای عیب یابی باقی مانده است؛ دریافت بسته های زنده مک ابتدا با آن بررسی شد. این ابزار در IPK قرار نمی گیرد. اجرای شبیه ساز، سازگاری با سخت افزار و محدودیت های خود تلویزیون را ثابت نمی کند.

## ساخت

نیازمندی ها: Node.js 20 یا بالاتر، Python 3.12، Git، make، Perl و Emscripten 3.1.45.

```sh
git clone --recurse-submodules --branch codex/webos-receiver https://github.com/MehdiEskandari/airplay-tv.git
cd airplay-tv
npm --prefix tv-web ci
git clone --depth 1 --branch 3.1.45 https://github.com/emscripten-core/emsdk.git platforms/webos/.cache/emsdk
platforms/webos/.cache/emsdk/emsdk install 3.1.45
platforms/webos/.cache/emsdk/emsdk activate 3.1.45
python3 platforms/webos/scripts/build-core.py --decoder-test --jobs 4
npm --prefix tv-web test
node platforms/webos/tests/core-smoke.cjs
node platforms/webos/tests/decoder.cjs
npm --prefix tv-web run package:webos
python3 platforms/webos/tests/package.py
```

خروجی توسعه:

```text
platforms/webos/output/com.mehdiskan.airplaytv_0.2.0_all.ipk
```

تست core-smoke از یک پورت موقت آزاد استفاده می کند؛ پورت پیش فرض گیرنده برنامه 7002 است. [GitHub Actions این برنچ](https://github.com/MehdiEskandari/airplay-tv/actions?query=branch%3Acodex%2Fwebos-receiver) همین هسته، تست ها و بسته را می سازد.

برای دیدن رابط بدون هسته، `npm --prefix tv-web run dev` را اجرا و `http://127.0.0.1:4180` را باز کنید. مرورگر معمولی گیرنده AirPlay نیست.

[گزارش دقیق تست ها و محدودیت نتایج](platforms/webos/tests/RESULTS.md).

## ساختار

| مسیر | محتوا |
| --- | --- |
| [tv-web/src](tv-web/src) | رابط مشترک تلویزیون، تم ها، زبان ها و کنترل ریموت |
| [آداپتور webOS](tv-web/src/platforms/webos.js) | فراخوانی Luna و اتصال به پلیر |
| [پلیر webOS](tv-web/src/webos-media.js) | تبدیل H264 به MediaSource و پخش PCM با Web Audio |
| [هسته](platforms/webos/native) | UxPlay، کشف شبکه، دیکودر صدا و پل سوکت WASM |
| [سرویس](platforms/webos/service) | شبکه واقعی Node، مدیریت هسته و کانال محلی رسانه |
| [راهنمای فنی و نصب](platforms/webos/README.md) | نصب توسعه، شبیه ساز و محدودیت ها |

در این نسخه، پخش مستقیم URL و محتوای DRM پشتیبانی تاییدشده ندارند. بررسی عملکرد، تاخیر، ریموت و سازگاری مدل های واقعی باقی مانده است.

## مجوز و سازنده

[GPL-3.0](LICENSE). مجوز وابستگی های هسته، JMuxer، ws و فونت ها همراه بسته قرار می گیرد.

سازنده: [مهدی اسکندری](https://github.com/MehdiEskandari). این پروژه وابسته به Apple یا LG نیست. AirPlay نشان تجاری Apple است.
