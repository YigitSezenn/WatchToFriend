# WatchToFriend

Arkadaşlarınızla birlikte video izlemenizi sağlayan **Android** uygulaması. Windows masaüstü sürümü ayrı repodadır.

🌐 **Web sitesi:** [watchtofriend.web.app](https://watchtofriend.web.app)  
💻 **Desktop sürümü:** [WatchToFriendDesktop](../WatchToFriendDesktop) (ayrı repo)

## Özellikler

- Senkron video izleme (YouTube vb.)
- Sesli sohbet, ekran paylaşımı, oda chat
- Oda oluşturma, davet linki, şifreli odalar
- Türkçe & İngilizce, sistem teması desteği
- Material 3 arayüz (Jetpack Compose)

## Gereksinimler

- Android Studio Ladybug veya üzeri
- JDK 17+
- Firebase projesi (Auth, Firestore, Realtime Database, FCM)

## Kurulum (geliştirme)

1. Repoyu klonlayın:

```bash
git clone https://github.com/YOUR_USERNAME/WatchToFriend.git
cd WatchToFriend
```

2. Firebase Android yapılandırması:

```bash
copy app\google-services.json.example app\google-services.json
```

[Firebase Console](https://console.firebase.google.com/) → Proje ayarları → Android uygulaması ekleyin (`com.watch.watchtofriend`) → indirilen `google-services.json` dosyasını `app/` klasörüne koyun.

3. Derleyin:

```bash
.\gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Release imzalama (opsiyonel)

```bash
copy keystore.properties.example keystore.properties
```

`keystore.properties` dosyasını kendi keystore bilgilerinizle doldurun. Bu dosya **asla** repoya eklenmemelidir.

```bash
.\gradlew :app:assembleRelease
```

## Firestore kuralları

`firestore.rules` dosyası proje kökündedir. Firebase Console veya Firebase CLI ile deploy edin.

## Repoya dahil edilmeyenler

- `app/google-services.json` — Firebase istemci yapılandırması (örnek: `google-services.json.example`)
- `keystore.properties`, `*.jks` — imzalama anahtarları
- `app/build/`, `.gradle/` — build çıktıları
- `local.properties` — yerel SDK yolu

## Lisans

MIT — detaylar için [LICENSE](LICENSE) dosyasına bakın.
