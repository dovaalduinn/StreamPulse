# 📱 StreamPulse — Gelişmiş Medya Yakalama, Oynatma ve İndirme Merkezi

StreamPulse; web sayfalarındaki gizli veya parçalı video akışlarını otomatik olarak yakalayan (Stream Sniffing), IPTV oynatma listelerini yöneten, yüksek performanslı yerel/çevrimdışı oynatma sunan ve kesintisiz arka plan indirme desteği sağlayan **kapsamlı bir Android medya ve yayın merkezidir**.

---

## 1. 🎯 Uygulama Amacı
Modern web sitelerinde parçalanmış (Blob, MSE, HLS, DASH) olarak sunulan medya içeriklerini tek dokunuşla oynatılabilir, indirilebilir ve organize edilebilir hale getirmektir.

---

## 2. ✨ Temel Özellikler

### 🌐 Akıllı Web Tarayıcısı ve Yayın Yakalayıcı (Stream Sniffer v2)
* **Otomatik Medya Yakalama:** Web sitelerindeki HLS (`.m3u8`), MP4, WebM, DASH (`.mpd`), MKV ve TS segmentlerini ağ seviyesinde ve DOM katmanında dinamik olarak yakalar.
* **Blob ve MSE Çözümleme:** JavaScript enjeksiyonu (`SNIFFER_JS`) ile `HTMLMediaElement`, `MediaSource` ve `fetch/XHR` çağrılarını dinleyerek korumalı akışların asıl kaynak adreslerini çıkarır.
* **Akıllı Master & Varyant Gruplama:** Aynı videonun farklı çözünürlüklerini (1080p, 720p, 480p vb.) otomatik gruplar; parça segmentleri yerine ana oynatma listesini (Master Playlist) önceliklendirir.
* **Reklam ve İzleyici Engelleme:** Reklam ve izleme betiklerini engelleyerek hızlı ve temiz bir gezinme sağlar.

### 🎬 Gelişmiş Medya Oynatıcı (Media3 / ExoPlayer)
* **Kapsamlı Format Desteği:** HLS, DASH, MP4, MKV, WebM, Canlı Yayın (Live) ve VOD akışları.
* **Kullanıcı Dostu Kontroller:** Çift dokunarak ileri/geri sarma, ekranın sağında/solunda dikey kaydırma ile parlaklık ve ses kontrolü.
* **Çoklu Kalite ve Altyazı:** WebVTT, SRT altyazı entegrasyonu ve manuel/otomatik çözünürlük seçimi.
* **Picture-in-Picture (PiP):** Arka planda veya diğer uygulamaları kullanırken kayan pencerede video oynatma.

### 📺 IPTV ve Çalma Listesi Yönetimi
* M3U / M3U8 IPTV listelerini içe aktarma, kategorilere ayırma ve kanal arama.
* Canlı TV yayınları için düşük gecikmeli oynatma optimizasyonu.

### 📥 Çok İş Parçacıklı İndirme Merkezi (Download Manager)
* **HLS Segment İndirme & Birleştirme:** Parçalı HLS `.ts` segmentlerini sıra tabanlı indirip yerel `.mp4` dosyasına kayıpsız dönüştürme.
* **Arka Plan Servisi (Foreground Service):** Uygulama kapalıyken veya bildirim çubuğundan duraklatma, devam ettirme ve iptal etme.
* **Çevrimdışı Oynatma:** İndirilen videoları internet bağlantısı olmadan doğrudan uygulama içinden izleme.

### 🛡️ Özel DNS (DoH) & Ağ Yönetimi
* Cloudflare, Google DNS veya özel DoH (DNS-over-HTTPS) adresleri tanımlayabilme.
* Ağ engellemelerini ve ISP kısıtlamalarını aşmak için uygulama düzeyinde DNS çözümleme.

### 🎮 TV & D-Pad Uyumluluğu (Virtual Mouse)
* Android TV ve kumanda cihazları için ekranda D-Pad ile kontrol edilebilen sanal fare imleci.

---

## 3. 🚀 Uygulamanın Hedefleri
1. **Tek Merkezden Medya Tüketimi:** Tarayıcı, IPTV oynatıcı, medya oynatıcı ve indirme yöneticisini tek ve hafif bir arayüzde birleştirmek.
2. **Korumalı/Parçalı Medyayı Erişilebilir Kılmak:** Yalnızca web oynatıcılarında çalışan karmaşık video akışlarını native oynatıcıya ve indirme kuyruğuna taşımak.
3. **Maksimum Güvenlik ve Gizlilik:** Kullanıcı verilerini sızdırmayan, SSRF/DNS Rebinding korumalı, oturum anahtarlı güvenli JS köprüsü ve yerel veri saklama mimarisi sunmak.

---

## 4. 🏗️ Mimari ve Teknik Altyapı

```
┌─────────────────────────────────────────────────────────────┐
│                   UI Katmanı (Jetpack Compose)              │
│  MainScreen │ Browser │ Player │ IPTV │ Downloads │ History │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Actions
┌──────────────────────────────▼──────────────────────────────┐
│            ViewModel Katmanı (MediaPlayerViewModel)          │
│       İş Mantığı, Oynatma Durumu, Sniffer Olay Yönetimi     │
└──────┬───────────────────────┬───────────────────────┬──────┘
       │                       │                       │
┌──────▼──────┐         ┌──────▼──────┐         ┌──────▼──────┐
│ Stream      │         │ Media3 /    │         │ Download    │
│ Sniffer v2  │         │ ExoPlayer   │         │ Service     │
│ (JS Engine, │         │ (Session &  │         │ (HLS Parser │
│  Correlator,│         │  OkHttp     │         │  Segment    │
│  Resolver)  │         │  DataSource)│         │  Merger)    │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
┌──────▼───────────────────────▼───────────────────────▼──────┐
│                Veri ve Depolama Katmanı (Room)              │
│  HistoryDao │ BookmarkDao │ IptvDao │ DownloadDao │ AppDb   │
└─────────────────────────────────────────────────────────────┘
```

* **Programlama Dili:** Kotlin (%100)
* **Arayüz (UI Framework):** Jetpack Compose, Material Design 3 (M3)
* **Mimari Model:** MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF)
* **Asenkron Yapı:** Kotlin Coroutines & StateFlow / SharedFlow
* **Veritabanı & Kalıcılık:** Android Room Database (SQLite)
* **Medya Oynatma:** Jetpack Media3 (ExoPlayer 1.x)
* **Ağ Katmanı:** OkHttp3 & Özel DNS Resolver

---

## 5. 🔒 Güvenlik ve Performans Tasarımı
* **SSRF & DNS Rebinding Koruması:** `AsyncMetadataResolver` içinde resmi `InetAddress` metodlarıyla yerel ağ, loopback ve IPv6 ULA (`fc00::/7`) adresleri engellenir; OkHttp özel DNS pini ile DNS rebinding saldırıları önlenir.
* **Güvenli JS Köprüsü:** Web sayfasına enjekte edilen `WebAppInterface`, yalnızca uygulamanın bildiği oturum belirteci (`SNIFFER_SECRET_TOKEN`) ile yetkilendirilir.
* **WebView İzolasyonu:** `allowFileAccess` ve `allowContentAccess` kapatılarak yerel dosya sistemi saldırıları engellenmiştir.
* **ProGuard / R8 & Minifikasyon:** Kod optimizasyonu ve küçültme aktif edilerek güvenlik ve performans artırılmıştır.

* İLETİŞİM: dovaalduinn@gmail.com
