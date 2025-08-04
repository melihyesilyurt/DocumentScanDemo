# 📄 DocumentScanDemo - Android Belge Tarayıcı

Google ML Kit ile güçlendirilmiş profesyonel Android belge tarayıcı uygulaması.

## ✨ Özellikler

- **📷 Kamera ile Canlı Tarama**: Google ML Kit Document Scanner ile profesyonel belge tarama
- **🖼️ Galeri Entegrasyonu**: Mevcut fotoğraflardan otomatik belge tespiti
- **🤖 AI Destekli Tespit**: Google ML Kit ile akıllı belge köşe tespiti
- **🔧 Akıllı Perspektif Düzeltme**: Otomatik perspektif düzeltme ve kırpma
- **✋ Manuel Ayarlama**: Otomatik tespit başarısız olduğunda manuel kırpma seçeneği
- **💾 Kaydetme ve Paylaşma**: Taranmış belgeleri kaydetme ve paylaşma

## 🛠️ Teknolojiler

- **Kotlin** - Modern Android geliştirme dili
- **Jetpack Compose** - Modern UI toolkit
- **Google ML Kit Document Scanner** - AI destekli belge tarama
- **CameraX** - Kamera işlemleri
- **Material Design 3** - Modern UI tasarım sistemi

## 📱 Ekran Görüntüleri

### Ana Ekran
- Kamera ile tarama butonu
- Galeriden seçim butonu
- Uygulama özellikleri listesi
- Geliştirici bilgileri

### Tarama Süreci
1. **Kamera Tarama**: ML Kit Document Scanner ile canlı tarama
2. **Galeri Seçimi**: Otomatik belge tespiti ve kırpma
3. **Manuel Ayarlama**: Gerektiğinde manuel köşe ayarlama
4. **Sonuç**: Taranmış belgeyi görüntüleme, kaydetme ve paylaşma

## 🚀 Kurulum

### Gereksinimler
- Android Studio Arctic Fox veya üzeri
- Android SDK 24 (Android 7.0) veya üzeri
- Kotlin 2.0.21
- Gradle 8.13

### Proje Kurulumu
```bash
git clone [repository-url]
cd DocumentScanDemo
./gradlew build
```

### Bağımlılıklar
```kotlin
// ML Kit Document Scanner
implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
```

## 📋 İzinler

Uygulama aşağıdaki izinleri gerektirir:

```xml
<!-- Kamera izinleri -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />

<!-- Galeri erişimi -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## 🏗️ Proje Yapısı

```
app/src/main/java/com/example/documentscandemo/
├── MainActivity.kt              # Ana aktivite ve tarama mantığı
├── ResultActivity.kt           # Sonuç görüntüleme ve işlemler
├── CropActivity.kt            # Manuel kırpma ekranı
├── SimpleDocumentDetector.kt  # Belge tespit algoritması
├── DocumentScanApplication.kt # Uygulama sınıfı
└── ui/theme/                 # Compose tema dosyaları
```

## 🔧 Kullanım

### Kamera ile Tarama
1. Ana ekranda "📷 Kamera ile Tara" butonuna tıklayın
2. ML Kit Document Scanner açılır
3. Belgeyi kamera ile tarayın
4. Otomatik olarak işlenir ve sonuç ekranına yönlendirilir

### Galeriden Seçim
1. Ana ekranda "🖼️ Galeriden Seç" butonuna tıklayın
2. Galeri açılır, bir fotoğraf seçin
3. Otomatik belge tespiti çalışır
4. Başarılı ise otomatik kırpılır, değilse manuel ayarlama ekranı açılır

## 🎯 Özellik Detayları

### Otomatik Belge Tespiti
- Görüntüdeki belge köşelerini otomatik tespit eder
- Tespit kalitesini değerlendirir
- Yüksek kaliteli tespitlerde otomatik kırpma yapar
- Düşük kaliteli tespitlerde manuel ayarlama önerir

### Akıllı Perspektif Düzeltme
- EXIF verilerini kullanarak görüntü yönünü düzeltir
- Perspektif dönüşümü ile belgeyi düzleştirir
- Yüksek kaliteli JPEG çıktısı (90% kalite)

## 👨‍💻 Geliştirici

**Melih Yeşilyurt**  
Android Geliştirici

## 📄 Lisans

Bu proje MIT lisansı altında lisanslanmıştır.

## 🤝 Katkıda Bulunma

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add amazing feature'`)
4. Branch'inizi push edin (`git push origin feature/amazing-feature`)
5. Pull Request oluşturun

## 📞 İletişim

Sorularınız için issue açabilir veya doğrudan iletişime geçebilirsiniz.
