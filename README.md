# AirPlay TV

[![license](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![release](https://img.shields.io/github/v/release/MehdiEskandari/airplay-tv)](https://github.com/MehdiEskandari/airplay-tv/releases/latest)

**رایگان، متن‌باز و بدون تبلیغ.** اندروید تی‌وی، گوگل تی‌وی یا باکس اندرویدی شما (مثل Mi Box S) را به گیرندهٔ **AirPlay** تبدیل می‌کند تا از آیفون، آیپد و مک تصویر، فیلم و موسیقی را روی تلویزیون پخش کنید.

**Free, open-source and ad-free.** Turns your Android TV, Google TV or Android box (e.g. Mi Box S) into an **AirPlay** receiver for iPhone, iPad and Mac.

[⬇️ دانلود آخرین نسخه / Download the latest APK](https://github.com/MehdiEskandari/airplay-tv/releases/latest)

---

## امکانات

- **پخش صفحه (Screen Mirroring)** از آیفون، آیپد و مک با H.264 و H.265
- **پخش فیلم با AirPlay**: فیلم با کیفیت کامل مستقیم روی تلویزیون پخش می‌شود
- **زیرنویس خودکار**: اگر کنار فایل فیلم روی سرور، فایل زیرنویس هم‌اسم باشد (`.vtt`، `.srt`، `.fa.srt` و...) پیدا و نمایش داده می‌شود. زیرنویس‌های فارسی با کدگذاری قدیمی ویندوز (Windows-1256) هم درست نمایش داده می‌شوند.
- **تنظیمات کامل زیرنویس** با پیش‌نمایش زنده: ۶ فونت فارسی (وزیرمتن، ساحل، شبنم، صمیم، استعداد، لاله‌زار)، ضخیم، اندازه، رنگ، دورخط یا سایه، تیرگی پس‌زمینه، ارتفاع از پایین صفحه، و **هماهنگ کردن زمان زیرنویس** (جلو/عقب بردن با گام نیم‌ثانیه، بدون توقف فیلم)
- **زوم با ریموت** هنگام پخش صفحه: دکمهٔ ▲ و ▼ برای حذف حاشیهٔ سیاه دور فیلم
- **تصویر پایدارتر**: فریم‌ها هنگام شلوغی پردازنده دور ریخته نمی‌شوند (تصویر شطرنجی نمی‌شود)، و پیش‌فرض ۳۰ فریم در ثانیه برای کیفیت بهتر در هر فریم
- **پخش از گوشی‌های اندرویدی (DLNA)**: با VLC، BubbleUPnP، Web Video Caster و هر برنامه‌ای که «Cast/DLNA» دارد. زیرنویس خودکار برای DLNA هم کار می‌کند.
- **دستگاه‌های آشنا**: هر دستگاه جدید فقط بار اول روی تلویزیون تأیید می‌خواهد («همیشه اجازه بده»)، دفعه‌های بعد خودکار وصل می‌شود. فهرست دستگاه‌ها در تنظیمات قابل مدیریت است.
- **همیشه آماده در پس‌زمینه**: اگر اندروید برنامه را ببندد دوباره خودش اجرا می‌شود، و بعد از روشن شدن تلویزیون یا به‌روزرسانی هم خودکار شروع می‌شود.
- **به‌روزرسانی خودکار**: برنامه نسخهٔ جدید را از GitHub پیدا می‌کند و با یک دکمه نصب می‌کند.
- پخش موسیقی با نام آهنگ و کاور
- اجرای خودکار با روشن شدن تلویزیون
- کنترل کامل با ریموت تلویزیون
- PIN اختیاری برای امنیت

## نصب

1. فایل `AirPlay-TV-x.y.z.apk` را از صفحهٔ [Releases](https://github.com/MehdiEskandari/airplay-tv/releases/latest) دانلود کنید.
2. یکی از این روش‌ها:
   - **با برنامهٔ Downloader** روی تلویزیون: آدرس فایل APK را وارد کنید و نصب کنید.
   - **با فلش USB**: فایل را روی فلش بریزید و با یک فایل‌منیجر روی تلویزیون نصب کنید.
   - **با ADB** از کامپیوتر:
     ```bash
     adb connect <IP-تلویزیون>:5555
     adb install AirPlay-TV-1.0.0.apk
     ```
3. برنامهٔ **AirPlay TV** را یک بار باز کنید.

## استفاده

- **پخش صفحه:** در آیفون Control Center ← Screen Mirroring ← اسم تلویزیون
- **پخش فیلم:** داخل پخش‌کنندهٔ ویدیو روی آیکون AirPlay بزنید
- گوشی و تلویزیون باید به **یک شبکهٔ وای‌فای** وصل باشند.
- محتوای دارای DRM (مثل برنامهٔ Apple TV) پشتیبانی نمی‌شود.

### تنظیم از طریق ADB

```bash
adb shell "am broadcast -n io.github.mehdieskandari.airplaytv/.SettingsReceiver -a io.github.mehdieskandari.airplaytv.SET_SETTING --es key server_name --es value 'Living Room'"
adb shell am broadcast -n io.github.mehdieskandari.airplaytv/.SettingsReceiver -a io.github.mehdieskandari.airplaytv.LIST_SETTINGS
```

---

## Features (English)

- Screen mirroring (H.264 / H.265), AirPlay video casting and audio streaming
- **Automatic subtitles**: picks up a same-named subtitle file next to the video on the server (`.vtt`, `.srt`, `.fa.srt`, …), including Windows-1256 encoded Persian `.srt`
- **Subtitle settings** with live preview: 6 bundled Persian fonts, bold, size, color, outline/shadow, background, position, and instant timing offset
- **Remote-controlled zoom** while mirroring (▲ / ▼) to crop black bars around a movie
- **More stable picture**: the decoder input waits instead of dropping frames (no blocky artifacts), 30 fps default
- **DLNA receiver** for Android phones (VLC, BubbleUPnP, Web Video Caster, …), with automatic subtitles
- **Trusted devices**: approve a new device once on the TV, it connects automatically afterwards
- **Always on**: restarts itself if Android stops it, and starts after boot and after updates
- **Auto-update** from GitHub Releases
- Full D-pad navigation, optional PIN

## Build

```bash
git clone --recursive https://github.com/MehdiEskandari/airplay-tv.git
cd airplay-tv
./gradlew assembleDebug
# faster, one CPU type only:
./gradlew assembleDebug -Pabis=armeabi-v7a
```

Requires JDK 21, Android SDK 37 and NDK 27.0.12077973. The project path must not contain spaces (an NDK limitation).

## License

GPL-3.0, see [LICENSE](LICENSE).

Bundled fonts (SIL Open Font License 1.1, see [fonts-licenses](fonts-licenses)): [Vazirmatn](https://github.com/rastikerdar/vazirmatn), [Sahel](https://github.com/rastikerdar/sahel-font), [Shabnam](https://github.com/rastikerdar/shabnam-font), [Samim](https://github.com/rastikerdar/samim-font), [Estedad](https://github.com/aminabedi68/Estedad), [Lalezar](https://github.com/google/fonts/tree/main/ofl/lalezar).

Based on [android-airplay-server](https://github.com/jqssun/android-airplay-server) (GPL-3.0). Uses [UxPlay](https://github.com/FDH2/UxPlay), [FFmpeg](https://ffmpeg.org), [libplist](https://github.com/libimobiledevice/libplist) and [OpenSSL](https://www.openssl.org).

Not affiliated with Apple Inc. AirPlay is a trademark of Apple Inc.
