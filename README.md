# Abu-Zahra Admin v3.8.2

## نظرة عامة
تطبيق إدارة ومراقبة أجهزة Android عن بعد مع بوت Telegram.

## المميزات
- **42 صلاحية** متقدمة للتحكم الكامل
- **بث مباشر** للكاميرا والشاشة والصوت
- **تحكم عن بعد** عبر Telegram Bot
- **تتبع الموقع** في الوقت الحقيقي
- **إدارة الملفات** والجهات الاتصال والرسائل

## البنية
```
Abu-Zahra-Final/
├── app/                    # تطبيق Android
│   └── src/main/
│       ├── java/com/abuzahra/admin/
│       │   ├── manager/    # إدارة الصلاحيات والإعدادات
│       │   ├── service/    # خدمات البث والأوامر
│       │   ├── network/    # اتصالات API و WebSocket
│       │   ├── receiver/   # مستقبلات البث
│       │   └── utils/      # أدوات مساعدة
│       └── res/            # موارد الواجهة
├── Server/                 # خادم Python
│   ├── server.py          # الكود الرئيسي
│   └── requirements.txt   # المتطلبات
└── .github/workflows/      # GitHub Actions
```

## الصلاحيات (42)
### الكاميرا والميكروفون
- CAMERA, RECORD_AUDIO, CAPTURE_AUDIO_OUTPUT, MODIFY_AUDIO_SETTINGS

### الموقع
- ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION

### التخزين
- READ/WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO

### جهات الاتصال
- READ_CONTACTS, WRITE_CONTACTS

### سجل المكالمات
- READ_CALL_LOG, WRITE_CALL_LOG

### الهاتف
- READ_PHONE_STATE, READ_PHONE_NUMBERS, ANSWER_PHONE_CALLS
- MANAGE_OWN_CALLS, CALL_PHONE

### الرسائل
- SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH

### التقويم
- READ_CALENDAR, WRITE_CALENDAR

### الشبكة و WiFi
- INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE
- ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE

### Bluetooth
- BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN

### الخدمات
- FOREGROUND_SERVICE (الكل)
- SYSTEM_ALERT_WINDOW, WRITE_SETTINGS
- BIND_NOTIFICATION_LISTENER_SERVICE
- BIND_ACCESSIBILITY_SERVICE
- BIND_VPN_SERVICE

### أخرى
- REQUEST_INSTALL_PACKAGES, QUERY_ALL_PACKAGES
- RECEIVE_BOOT_COMPLETED, WAKE_LOCK
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- USE_FINGERPRINT, USE_BIOMETRIC, VIBRATE, FLASHLIGHT

## خدمات البث
1. **CameraStreamService** - بث الكاميرا
2. **ScreenStreamService** - بث الشاشة
3. **AudioStreamService** - بث الصوت
4. **LocationService** - تتبع الموقع

## البناء
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## الإصدارات
- **v3.8.2** - الإصدار الحالي مع 42 صلاحية + البث المباشر
- **v3.8.0** - إضافة خدمات البث
- **v3.7.0** - تحسينات الأداء

## المتطلبات
- Android 7.0+ (API 24)
- JDK 17
- Gradle 8.5
- Android Gradle Plugin 8.2.2

## المطور
Abu-Zahra Admin Team
