# App Lock — اپ لاک

<div dir="rtl">

اپلیکیشن اندروید برای قفل کردن اپ‌های دیگر گوشی. ترتیب باز کردن:

1. **چهره‌ی خودِ گوشی** (Face Unlock ثبت‌شده در تنظیمات گوشی)
2. **اثر انگشت**
3. **PIN یا پترن** خودِ اپ (همیشه آخر، همیشه کار می‌کند)

ترتیب چهره و اثر انگشت از **تنظیمات اپ ← اولویت باز کردن** قابل تغییر است و هر کدام را می‌شود خاموش کرد.

## نصب

1. در GitHub به بخش **Actions** این ریپو بروید، آخرین اجرای موفق «Build APK» را باز کنید و فایل `AppLock-debug-apk` را دانلود کنید.
2. فایل zip را باز کنید و `app-debug.apk` را روی گوشی نصب کنید.
3. اپ را باز کنید و یک PIN یا پترن بسازید.
4. دکمه‌ی «باز کردن تنظیمات دسترسی‌پذیری» را بزنید و «اپ لاک» را روشن کنید.
   - در اندروید ۱۳ به بالا اگر خاکستری بود: Settings ← Apps ← اپ لاک ← ⋮ (بالا راست) ← **Allow restricted settings**، بعد دوباره امتحان کنید.
5. اپ‌هایی را که می‌خواهید قفل شوند روشن کنید. پیشنهاد: اپ **Settings** را هم قفل کنید تا کسی نتواند اپ لاک را خاموش یا حذف کند.

هر نسخه‌ی APK که GitHub می‌سازد با کلید متفاوتی امضا می‌شود؛ برای نصب نسخه‌ی جدید، اول نسخه‌ی قبلی را حذف کنید.

## درباره‌ی چهره — محدودیت اندروید

اندروید به هیچ اپی اجازه نمی‌دهد مستقیم به سنسور چهره دستور بدهد. اپ فقط می‌تواند پنجره‌ی بیومتریک سیستم را باز کند:

- اگر چهره در گوشی ثبت باشد و گوشی آن را به اپ‌ها بدهد (مثلاً Pixel و بیشتر Samsungها)، در مرحله‌ی اول با چهره باز می‌شود.
- اگر گوشی چهره را به اپ‌ها ندهد (بعضی مدل‌های Xiaomi، Oppo، Vivo، Huawei و اندروید ۹ و پایین‌تر)، مرحله‌ی چهره خودکار رد می‌شود و در تنظیمات اپ نوشته می‌شود «این گوشی اجازه‌ی استفاده از چهره را به اپ‌ها نمی‌دهد».
- اگر گوشی سخت‌افزار چهره دارد ولی چهره‌ای ثبت نشده، پنجره‌ی سیستم ممکن است همان‌جا اثر انگشت بخواهد.

مرحله‌ی اثر انگشت مستقیم با سنسور انگشت کار می‌کند و چهره در آن دخالتی ندارد.

## چک‌لیست تست روی گوشی

- [ ] ساخت PIN / پترن و روشن کردن سرویس دسترسی‌پذیری
- [ ] قفل کردن یک اپ و باز کردن آن با چهره
- [ ] زدن «روش بعدی» و باز کردن با اثر انگشت
- [ ] رد کردن هر دو و باز کردن با PIN / پترن
- [ ] ۵ بار PIN اشتباه ← ۳۰ ثانیه انتظار
- [ ] تغییر ترتیب در تنظیمات (انگشت اول) و امتحان دوباره
- [ ] خاموش و روشن کردن صفحه ← اپ دوباره قفل می‌شود

## حریم خصوصی

اپ هیچ مجوز اینترنت یا دوربین ندارد. PIN / پترن فقط به‌صورت هش (PBKDF2 با salt) ذخیره می‌شود. سرویس دسترسی‌پذیری فقط نام اپ بازشده را می‌بیند و محتوای صفحه را نمی‌خواند.

</div>

---

## English

Android app that locks other apps. Unlock order: the **phone's own face unlock** → **fingerprint** → the app's **PIN or pattern** (always last). The order of face and fingerprint can be changed, and either can be switched off, in *Settings → Unlock priority*.

**Android limitation:** apps cannot command the face sensor directly; the face step opens the system biometric prompt (`BIOMETRIC_WEAK`) and is only offered on phones that expose face hardware to apps (Android 10+ with `FEATURE_FACE`). The fingerprint step talks to the fingerprint sensor only.

### Build

```
./gradlew assembleDebug testDebugUnitTest
```

Requires JDK 17+ and the Android SDK (platform 35). Every push also builds the debug APK in GitHub Actions (artifact `AppLock-debug-apk`).

### Structure

| Path | Role |
|---|---|
| `LockActivity.kt` | Lock screen; runs the unlock chain |
| `auth/UnlockChain.kt` | Ordering / fallback state machine (unit-tested) |
| `auth/BiometricSupport.kt` | Which methods this phone offers |
| `service/AppLockAccessibilityService.kt` | Detects the foreground app, shows the lock |
| `data/` | Locked apps, priority order, hashed PIN/pattern |
| `ui/` | PIN pad, pattern grid, setup and settings screens |
