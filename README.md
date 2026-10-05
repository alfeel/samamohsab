# سماء المحاسبين الشامل (Sama Mohsab) 📱

تطبيق محاسبي متكامل لنظام أندرويد مع دعم المزامنة السحابية عبر Firebase وميزة البناء السحابي المؤتمت (Cloud Build via GitHub Actions).

## 🚀 البناء السحابي (Cloud Build)
تم إعداد سير عمل **GitHub Actions** للبناء السحابي التلقائي:
1. عند كل `push` أو تحديث، أو عبر الضغط يدوياً على **Run workflow** في تبويب **Actions** داخل مستودع GitHub.
2. يتم بناء ملف الـ APK تلقائياً على خوادم GitHub السحابية.
3. يمكنك تنزيل ملف الـ APK الجاهز مباشرة من قسم **Artifacts** باسم:
   `SamaMohsab-Debug-APK`

## 📦 تفاصيل التطبيق
- **اسم الحزمة (Package Name):** `com.samamohsab.alfeel`
- **مشروع Firebase:** `samamohsab`
- **التوافق:** Android 7.0 (API 24) فما فوق
- **اللغة:** Kotlin
- **قاعدة البيانات المحلية:** SQLite
- **المزامنة السحابية:** Cloud Firestore & Firebase Authentication
- **واجهة المستخدم:** تدعم اللغة العربية والاتجاه من اليمين لليسار (RTL) بالكامل مع Material Design 3.

## 🛠️ البناء المحلي (اختياري)
```bash
./gradlew assembleDebug
```
ملف الـ APK الناتج سيكون في المسار:
`app/build/outputs/apk/debug/app-debug.apk`
