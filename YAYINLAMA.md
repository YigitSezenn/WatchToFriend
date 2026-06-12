# Google Play Yayınlama Rehberi — Watch with Friends

Uygulama artık **production formatında**: R8 küçültme + kaynak küçültme açık, AAB üretiliyor,
release imzalama yapılandırıldı, ProGuard kuralları Firestore modellerini/JS köprüsünü koruyor.

## 1) Release imza anahtarı (keystore) oluştur — BİR KEZ
Terminalde (JDK'nın `keytool`'u ile):

```bash
keytool -genkeypair -v -keystore watchtofriend-release.jks ^
  -keyalg RSA -keysize 2048 -validity 10000 -alias watchtofriend
```
- Sorulan parolaları belirle ve **güvenle sakla** (kaybedersen aynı uygulamayı güncelleyemezsin).
- `watchtofriend-release.jks` dosyasını proje dışında güvenli bir yerde tut (git'e girmez).

## 2) keystore.properties oluştur (proje kök dizininde)
`D:\MobilUygulamaProjeleri\WatchToFriend\keystore.properties`:

```properties
storeFile=C:/güvenli/yol/watchtofriend-release.jks
storePassword=KEYSTORE_PAROLAN
keyAlias=watchtofriend
keyPassword=ANAHTAR_PAROLAN
```
> Bu dosya `.gitignore`'da — asla commit etme. Yoksa derleme imzasız üretir (sorun değil, test için).

## 3) Yükleme paketini üret
```bash
./gradlew bundleRelease
```
Çıktı: `app/build/outputs/bundle/release/app-release.aab` → Play Console'a **bunu** yükle.
(APK gerekirse: `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.)

## 4) Sürüm yönetimi (her güncellemede)
`app/build.gradle.kts` içinde:
- `versionCode` her yüklemede **artırılmalı** (1 → 2 → 3 …).
- `versionName` kullanıcıya görünen sürüm ("1.0", "1.1" …).

## 5) Play Console kontrol listesi
- [ ] **Play App Signing**'i etkinleştir (Google imza anahtarını yönetir; sen "upload key" ile imzalarsın).
- [ ] **Gizlilik Politikası URL'si** (zorunlu — `PRIVACY.md`'yi bir yere host et: GitHub Pages, Notion, kendi siten).
- [ ] **Veri Güvenliği (Data safety)** formu: topladığın veriler (e-posta, ad, profil foto, mesajlar) + Firebase kullanımı.
- [ ] **İçerik derecelendirmesi** anketi.
- [ ] **Ekran görüntüleri** (telefon: en az 2), uygulama ikonu (512×512), özellik grafiği (1024×500).
- [ ] **Kısa/uzun açıklama** (Türkçe + İngilizce önerilir).
- [ ] Hedef kitle/yaş, kategori (Sosyal / Eğlence).

## 6) Firebase tarafı
- [ ] **Firestore kurallarını yayınla** (`firestore.rules` — şu an gevşek; en azından `requests` sıkılaştırması yapıldı).
- [ ] **YouTube Data API anahtarını** Google Cloud'da **paket adı + SHA-1** ile kısıtla
      (release SHA-1: `keytool -list -v -keystore watchtofriend-release.jks -alias watchtofriend`).
      Play App Signing kullanıyorsan Console'daki **App signing SHA-1**'i de ekle.
- [ ] Auth → yetkili alan adları/SHA-1 (Google ile giriş için release SHA-1 eklenmeli).

## 7) Önemli notlar
- **Korsan film siteleri / telifli içerik:** Uygulama yalnızca kullanıcı linki sağlar; telifli
  içerik dağıtımı Play politikasına aykırıdır. Tanıtım/açıklamada bunu vurgulama.
- `usesCleartextTraffic=true` açık (http video kaynakları için). Yalnızca HTTPS hedefliyorsan
  kapatman daha güvenli olur.
- Minify açık olduğundan crash stack'leri obfuscate olabilir → Play Console'a **mapping.txt**
  yükle: `app/build/outputs/mapping/release/mapping.txt`.
