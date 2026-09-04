# Geemu

Controller bağlandığında oyun moduna geçen Android emülasyon frontend'i.

## İlk cihaz testi

1. `app/build/outputs/apk/debug/app-debug.apk` dosyasını telefona kur.
2. Geemu'yu açıp **Ayarlar > Ekran yönünü değiştir** iznini ver.
3. **Ayarlar > Controller oyun modu** üzerinden `Geemu Oyun Modu` hizmetini etkinleştir.
4. Razer Kishi V2 Pro'yu bağla. Android USB uygulaması sorarsa Geemu'yu seçip varsayılan yap.
5. Başka bir controller için kol bağlıyken Geemu ayarlarında cihazın anahtarını aç.
6. Android oyunları ekranında telefondaki başlatılabilir uygulamalardan istediklerini oyun klasörüne ekle.
7. Vita3K dışındaki platformlarda ROM klasörünü platform ekranından seç.

## Şu anki davranış

- Razer üretici kimliğine sahip controller'lar otomatik oyun modu cihazıdır.
- Diğer USB veya Bluetooth gamepad'ler bağlı cihaz listesinden kaydedilebilir.
- Kayıtlı controller bağlanınca Geemu açılır ve ekran yatay kilitlenir.
- Controller çıkınca Ana Ekran komutu gönderilir ve ekran dikey kilitlenir.
- Vita3K kendi `Android/data/org.vita3k.emulator/files` kütüphanesini kullandığı için ilk sürümde uygulama doğrudan açılır.
- Eden, Citron, Cemu ve AetherSX2 ROM'ları seçilen klasörden taranır. Emülatör seçilen dosya için `ACTION_VIEW` kabul etmiyorsa uygulamanın ana ekranı açılır.

## Controller tuşları

- Ana ekran: `Sol/Sağ` veya `L1/R1` sistem değiştirir, `A` açar, `Start` ayarlara gider.
- Oyun ekranı: yön tuşları kapaklar arasında gezer, `A` başlatır, `B` geri döner.
- Emülatör ekranı: `X` ROM klasörünü, `Y` emülatörün kendisini açar.
- Android oyunları: `X` tüm uygulamalardan oyun seçme ekranını açar.
- Ayarlar ve uygulama seçici: yön tuşları seçim yapar, `A` uygular, `B` geri döner.

## Donanım testinde kaydedilecek bilgiler

- Controller adı, VID ve PID
- Emulator uygulamalarının gerçek paket adları
- Her emulatorun dosya intent'ini kabul edip etmediği
- S24 FE üzerinde yatay/dikey kilit davranışı
