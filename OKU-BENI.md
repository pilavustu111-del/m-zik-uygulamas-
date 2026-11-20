# Cep Çalar (Android)

Telefondaki müzikleri ve klipleri **kendiliğinden bulan**, **uygulama kapalıyken de çalmaya devam eden**,
Spotify tarzı arayüzlü müzik çalar.

## Özellikler
- İlk açılışta izin ister, ardından telefondaki tüm müzikleri ve klipleri otomatik tarar. Yeni dosya eklenince liste kendiliğinden güncellenir.
- Arka planda çalma: bildirim ve kilit ekranı kontrolleriyle (Media3 MediaSession servisi). Uygulamayı kapatsan da çalar.
- Klipler tam ekran "Şimdi çalıyor" sayfasında görüntüyle oynar. Sadece ses olan parçalarda kapak görünür.
- Şarkı sözleri: İnternet varken lrclib.net'ten bulunur, telefona kaydedilir. Zamanlı sözler şarkıyla akar, bir satıra dokununca oraya atlar.
- En çok dinlediklerin: Bir parça yaklaşık 30 saniye dinlenince sayılır. Ana sayfada ve "En çok" sekmesinde görünür.
- Öneriler: İnternet varken en çok dinlediğin sanatçılara göre benzer sanatçılar (Deezer) "Tavsiye Edilen İstasyonlar" olarak çıkar. Dokununca 30 saniyelik önizlemeler çalar.

## APK nasıl alınır

### Yol A: GitHub ile (bilgisayara bir şey kurmadan)
1. github.com'da ücretsiz hesap aç, yeni bir **boş depo** oluştur.
2. Bu klasörün içindekileri (CepCalar klasörünün içi, `.github` klasörü dahil) depoya yükle.
3. Depoda **Actions** sekmesi > **APK derle** > **Run workflow**.
4. İş bitince (yaklaşık 5-8 dk) sayfanın altındaki **cep-calar-apk** dosyasını indir, zip'ten `app-debug.apk` çıkar.
5. APK'yı telefona gönder, aç. "Bilinmeyen kaynaklardan yükle" izni isterse ver.

### Yol B: Android Studio ile
1. Android Studio'yu kur, **Open** ile bu klasörü aç, Gradle eşitlemesini bekle.
2. **Build > Build APK(s)** ile APK'yı al ya da telefonu USB ile bağlayıp **Run**'a bas.

## Notlar
- Kod henüz gerçek bir derlemeyle denenmedi. Derleme hata verirse hata metnini gönder, düzeltirim.
- Telefon üreticilerinin pil tasarrufu ayarları arka plandaki çalmayı kesebilir. Kesilirse Ayarlar > Pil > Cep Çalar için "Kısıtlama yok" seç.
- Telefonda sanatçı bilgisi olmayan dosyalarda, ad "Sanatçı - Şarkı" biçimindeyse sanatçı dosya adından okunur.
- Klipler: Sanatçı bilgisi olan veya Müzik, Klip gibi klasörlerdeki videolar listelenir. Kamera, WhatsApp vb. videolar gizlidir. Kitaplık sekmesindeki anahtarla tüm videoları gösterebilirsin.
