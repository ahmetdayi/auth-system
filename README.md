# JWT Auth — Hexagonal / DDD örnek projesi

Düzeltilmiş "Spring Boot JWT Authentication" rehberinin, iki kural dokümanına uyan çalışan hâli:

- `DDD_HEXAGONAL_ARCHITECTURE_RULES.md` — paket yapısı, port/adaptör, bounded context, ArchUnit kuralları
- `Yapay_Zeka_Proje_Kodlama_Kuralları.pdf` — doğrulama, hata yönetimi, JPA, zaman, güvenlik, test kuralları

Yanında her şeyi tarayıcıdan deneyebileceğin bir **Thymeleaf test konsolu** var.

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| JWT | jjwt 0.13.0 (HS256) |
| Veritabanı | PostgreSQL 16, Flyway |
| Önbellek / sayaç | Redis 7 |
| Test | JUnit 5, ArchUnit 1.5.1, Testcontainers 2.0 |

---

## 1. Çalıştırma

**Gerekenler:** JDK 21, Maven 3.9+, Docker (Docker Desktop ya da Docker Engine).

```bash
mvn spring-boot:run
```

Maven eklentisi `local` profilini açar. Spring Boot `compose.yaml`'daki PostgreSQL ve Redis'i Docker'da başlatır,
rastgele portlarını kendisi bağlar; Flyway şemayı ve demo verisini kurar. Uygulama durunca konteynerler de durur.

Ardından: **http://localhost:8080/console**

Demo hesapların hepsinin şifresi `DemoSifre123!`:

| E-posta | Tip | Ne için |
|---|---|---|
| `ayse@example.com` | CUSTOMER | GOLD üye, `PREMIUM_READER` rolü, 2 adres |
| `mehmet@example.com` | CUSTOMER | SILVER üye |
| `can@example.com` | CUSTOMER | Üyeliği yok |
| `zeynep@example.com` | CUSTOMER | GOLD üyeliği 31.01.2026'da bitmiş |
| `pasif@example.com` | CUSTOMER | Devre dışı: doğru şifreyle de giremez |
| `admin@example.com` | ADMIN | Standart admin, `customer:read` + `customer:write` |
| `super@example.com` | ADMIN | SUPER_ADMIN, diğer adminlerin oturumlarını yönetir |
| `destek@example.com` | ADMIN | Yalnızca `customer:read` |

Demo veri (`src/main/resources/db/demo/R__demo_data.sql`) yalnızca `local` profilinde yüklenir. Boş bir
veritabanında id'ler 1–5 müşteriler, 6–8 adminlerdir; yükseltilmiş bir veritabanında adminler müşterilerden sonra
numaralanır (aşağıda). Hiçbir yer sabit id'ye güvenmez.

### IntelliJ'den çalıştırma

`main` metodunu doğrudan çalıştırınca Maven eklentisi devrede olmadığından **hiçbir profil açılmaz** ve uygulama
DataSource hatasıyla durur (fail-closed, kasıtlı). Profili `application.yml`'a yazmak yerine hazır çalıştırma
ayarını kullan: `.run/AuthDemoApplication (local).run.xml` — IntelliJ bunu çalıştırma listesinde
**AuthDemoApplication (local)** olarak gösterir; `SPRING_PROFILES_ACTIVE=local` ortam değişkenini yalnızca senin
makinendeki çalıştırmaya verir.

> **Neden `application.yml`'a `spring.profiles.active: local` yazılmamalı:** bu dosya üretim varsayılanıdır. Satır
> orada durursa, ortamda profil verilmeden başlatılan her kurulum (ör. `java -jar`) sessizce `local` olur:
> `JWT_SECRET` yoksa durmak yerine **geçici anahtar üretir**, şifresi bilinen **demo hesapları yükler**
> (`admin@example.com` / `DemoSifre123!`), cookie'leri **`Secure` olmadan** yazar ve DEBUG log açar. Üretimde
> `SPRING_PROFILES_ACTIVE` verilirse dosyadaki değeri ezer; risk, bunun unutulduğu kurulumdur. Testleri
> etkilemez (`@ActiveProfiles("test")` önceliklidir; doğrulandı).

### Mevcut veritabanını yükseltme (V4, V5)

Modüllere bölünme şemayı da böldü. `V4__split_bounded_contexts.sql` eski tabloları yeni sahiplerine taşır, veri
kaybı olmadan: müşteriler **kendi id'lerini korur**, adminler müşterilerden sonra **yeni id** alır (tüm hesaplar
tek id alanında). Oturumlar, roller, izinler, üyelikler, adresler ve admin bölgeleri taşınır. `V5__outbox.sql`
outbox tablosunu ekler. V1–V3'e dokunulmadı (uygulanmış migration geçmiştir).

Yükseltmeden önce verilmiş access token'lar reddedilir (`TOKEN_INVALID`, en fazla 15 dakikalık token'lar):
token'daki hesap tipi alanı değişti ve bu kasıtlı. Aksi hâlde eski "admin #1" token'ı artık müşteri #1'i
(ayse) gösterirdi. Oturumlar taşındığı için yenileme ya da yeniden giriş yeterli. Redis'teki eski biçimli iptal
anahtarları kendiliğinden süresi dolarak silinir; yalnızca zaten reddedilen eski token'ları ilgilendirir.

Yerelde temiz başlamak istersen: `docker compose down -v` (veritabanı silinir, demo veriyle yeniden kurulur).

### Üretim gibi çalıştırma

```bash
mvn package
export SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/auth
export SPRING_DATASOURCE_USERNAME=auth
export SPRING_DATASOURCE_PASSWORD=...
export SPRING_DATA_REDIS_HOST=redis
export JWT_SECRET=$(openssl rand -base64 32)     # en az 256 bit
java -jar target/jwt-auth-hexagonal-1.0.0-SNAPSHOT.jar
```

Eksik ya da zayıf ayar uygulamayı **başlatmaz** (fail-closed): `JWT_SECRET` yoksa
`app.token.jwt-secret (env JWT_SECRET) must be set`, 256 bitten kısaysa `WeakKeyException`, veritabanı adresi
yoksa DataSource hatası. Docker Compose desteği üretim jar'ına girmez.

Bir ters proxy arkasında `server.forward-headers-strategy` ayarlanmalı; aksi hâlde IP bazlı giriş sınırı proxy'nin
IP'sini sayar. `X-Forwarded-For` doğrudan okunmaz, çünkü istemci onu istediği gibi yazabilir.

---

## 2. Test konsolu

Sayfalar birer kabuktur: her buton REST API'yi tarayıcıdan çağırır, sonuç ve sayfanın altındaki **istek günlüğü**
(metot, adres, durum kodu, hata kodu, istek/yanıt gövdesi) görünür. Konsol için hiçbir güvenlik kuralı gevşetilmedi.

| Sayfa | Ne denenir |
|---|---|
| Giriş | Tarayıcı girişi; `document.cookie` ile token'ların JavaScript'ten okunamadığını görme; kaba kuvvet sınırı |
| Kayıt | Doğrulama hataları, şifrenin bayt sınırı (Türkçe harf = 2 bayt), CSRF'siz deneme |
| Profil | Üç modülün verisi yan yana: profil ve adresler (customer), üyelik (membership), şifre değiştirme (identity); adres ekle/sil (en fazla 5), başkasının adını değiştirmeye çalışma (403) |
| Oturumlar | Cihaz listesi, tek oturumu kapatma, yenileme, **iki paralel yenileme** (biri 200, diğeri 409), çıkış, tüm cihazlardan çıkış |
| İçerik | Aynı kısıtın üç uygulaması: domain kuralı, `@PreAuthorize` + rol hiyerarşisi, rolden gelen izin |
| Admin | Dashboard (staff + identity), müşteri listesi (customer listesi + identity'den durum + membership'ten üyelik, sayfa başına iki toplu sorgu), ban/ban kaldırma, üyelik verme, oturum sonlandırma, SUPER_ADMIN kontrolü |
| Mobil (Bearer) | Token'lar gövdede; JWT içeriği; rotation; eski refresh token ile yeniden kullanım tespiti; değiştirilmiş token |
| Senaryolar | **40 kontrolü tek tıkla** çalıştırır (güvenlik + outbox ile profil oluşumu + ortak id alanında tip kontrolü), beklenen/gerçekleşen sonucu yan yana gösterir |

Senaryolar yeni açılan test hesaplarında çalışır, tarayıcının oturumuna dokunmaz. Her çalıştırma yaklaşık 9 hatalı
giriş üretir; yerel profilde IP başına sınır 100'dür (15 dakikada ~11 çalıştırma).

---

## 3. Testler

```bash
mvn test
```

| Test | Ne doğrular |
|---|---|
| `ArchitectureTest` (31 kural) | Doküman §9'daki 22 kural (uyarlananlar aşağıda) + 9 ek kural (modüller arası izinli yollar, `contracts` kuralları dahil) |
| `UserAccountTest`, `UserSessionTest`, `ValueObjectTest` (identity), `CustomerProfileTest`, `MembershipTest`, `ArticleTest` | Saf domain kuralları, Spring'siz |
| `ErrorMessagesTest` | Her hata kodunun Türkçe mesajı var, mesajsız kod yok, kodlar modüller arasında tekil |
| `AuthenticationFlowIntegrationTest` | Cookie bayrakları, aynı hata cevabı, kaba kuvvet, süresi dolan token, rotation + reuse, CSRF, çıkışlar |
| `AuthorizationIntegrationTest` | Tip ayrımı, kaynak bazlı yetki, izinler, rol ve üyelik hiyerarşisi, sıralama beyaz listesi, LIKE kaçışı |
| `AccountLifecycleIntegrationTest` | Kayıt, ban, şifre değişikliği, üyelik, adres limiti, oturum yönetimi |
| `SessionPersistenceIntegrationTest` | Optimistic lock, yalnızca hash saklama, toplu silme işi, veritabanı kısıtları |
| `CustomerOnboardingIntegrationTest` | Kayıt → outbox → profil; tekrar teslimde ikinci profil yok; bozuk olay geri çekilmeyle yeniden denenir ve diğerlerini durdurmaz; başarısız kayıt olay bırakmaz |
| `SharedIdSpaceIntegrationTest` | Müşteri URL'leri admin hesabına, admin URL'leri müşteriye ulaşmaz; id ile toplu sorgular sınırlı ve yetkili |

Entegrasyon testleri **gerçek PostgreSQL ve Redis** kullanır (H2 yok). Varsayılan olarak Testcontainers ile
Docker'da açılırlar. Docker olmayan bir ortamda çalışan sunuculara bağlanmak için:

```bash
IT_DB_URL=jdbc:postgresql://localhost:5432/auth_it IT_DB_USERNAME=... IT_DB_PASSWORD=... \
IT_REDIS_HOST=localhost IT_REDIS_PORT=6379 IT_REDIS_DATABASE=1 mvn test
```

Testlerde `Thread.sleep` yok: saat bir `MutableClock` bean'idir, token süresi dolması ya da 14 günlük oturum
ömrü saati ileri alarak test edilir. Her test kendi hesaplarını ve kendi istemci IP'sini kullanır.

---

## 4. Mimari

```
tr.gov.kurum
├── common/       Shared kernel: AccountId, AccountType, EmailAddress, hata tipleri, AggregateRoot, sayfalama,
│                 BaseJpaEntity, Problem Details, çağıranın kimliği (AuthenticatedAccount), outbox
├── contracts/    Yayınlanmış dil: modüller arası entegrasyon olayları (yalnızca record, JDK + common.domain)
├── identity/     Kimlik: hesap (giriş bilgisi, durum, roller, izinler, admin seviyesi), oturumlar, token'lar
├── customer/     Müşteri: ad, iletişim e-postası, adresler, puan
├── membership/   Üyelik: seviye (BRONZE/SILVER/GOLD) ve geçerlilik süresi
├── staff/        Personel: adminin adı, birimi, sorumlu olduğu bölgeler
├── content/      İçerik: makaleler, okuyucu seviyesi, indirim
└── console/      Thymeleaf konsolunun sayfa controller'ı
```

Her modül aynı iç düzende: `domain/model`, `domain/event`, `application/port/in`, `application/port/out`,
`application/service`, `infrastructure/adapter/in/{web,messaging,scheduling}`, `infrastructure/adapter/out/...`.

**Neden bu sınırlar.** Önceki hâlde `Customer` aggregate'i giriş bilgisini, adı, adresleri ve üyeliği birlikte
taşıyordu: ad değiştirmek ile şifre değiştirmek aynı satırın versiyonunu yarıştırıyordu; üyelik kuralı kimlik
modelinin içindeydi. Şimdi her kavram tek sahibinde. Hepsini bağlayan tek şey hesabın kimliği, `AccountId`
(shared kernel): her modül kendi tablosunu bu id ile anahtarlar, başka modülün tablosuna JPA ilişkisi kurmaz.

**Modüller birbirine nasıl ulaşır** (ArchUnit yalnızca bu yollara izin verir):

| Kim → kime | Nasıl | Neden |
|---|---|---|
| identity → membership | `MembershipLevelLookup` (identity'nin outbound port'u) → adaptör → `MembershipQueryUseCase` | Token'a geçerli üyelik seviyesini koymak; yalnızca seviye adı sınırı geçer |
| membership → customer | `CustomerRegistry` → adaptör → `CustomerDirectoryUseCase` | Üyelik yalnızca var olan müşteriye verilir |
| identity → customer | `CustomerAccountOpened` (identity domain olayı) → çevirici adaptör → `contracts.identity.CustomerRegistered` → outbox → customer'ın dinleyicisi | Kayıt hesabı açar; profil ayrı transaction'da (bir transaction'da bir aggregate, kural 3.3) |

Senkron çağrılar her zaman çağıranın kendi outbound port'u üzerinden (kural 5.1); servisler başka modülün
koduna hiç dokunmaz. Olaylar `contracts` paketinden geçer, çünkü customer'ın identity'nin koduna bağlanması
identity → membership → customer → identity döngüsünü kurardı (kural 10).

**Outbox (kural 6.2).** `CustomerRegistered` hesapla **aynı transaction'da** `outbox_events` tablosuna yazılır.
`OutboxRelay` olayları tek tek, her biri kendi transaction'ında teslim eder: tüketicinin değişikliği ile "teslim
edildi" işareti birlikte commit olur. Tüketici hata verirse ikisi de geri alınır ve olay artan aralıklarla yeniden
denenir (en fazla 10 kez, sonra `last_error` ile incelemeye kalır); bozuk bir olay diğerlerini durdurmaz.
`FOR UPDATE SKIP LOCKED` ile birden çok instance aynı olayı aynı anda teslim etmez. Tüketiciler yine de
idempotent: profil zaten varsa olay yok sayılır. Sonuç: kayıttan sonra profil birkaç yüz milisaniye içinde oluşur
(nihai tutarlılık); o arada `/api/customer/profile` `404 CUSTOMER_NOT_FOUND` döner.

**UI composition.** Hiçbir controller birden fazla modülü birleştirmez (kural 7.3). Ekran birleştirir: admin
müşteri listesi önce customer'dan bir sayfa ister, sonra o sayfadaki id'ler için identity'den durumu
(`/api/admin/accounts?ids=`) ve membership'ten üyeliği (`/api/admin/memberships?ids=`) **tek seferde** alır
(en fazla 100 id). Bir sorgu düşerse yalnızca o sütun boş kalır.

**Ortak id alanı ve tip kontrolü.** Müşteri ve admin aynı id dizisinden numara alır; `/api/admin/customers/7/...`
ile `/api/admin/admins/7/...` aynı hesaba işaret edebilir. Bu yüzden her admin işlemi beklenen hesap tipini
servis katmanında kontrol eder; tutmazsa `404 ACCOUNT_NOT_FOUND` (müşteri URL'si admin hesabına ulaşamaz).

Kurallar ArchUnit ile her derlemede denetlenir: domain saf (Spring/JPA/Jackson/Lombok yok), modüller yalnızca
kendilerini, `common`'ı ve yukarıdaki izinli yolları görür, `contracts`'ı yalnızca adaptörler kullanır, döngü yok,
JPA entity'leri ve Spring Data repository'leri paket-özel, yalnızca aggregate root'ların repository'si var,
ilişkiler LAZY, `CascadeType.ALL` ve `EnumType.ORDINAL` yok.

### Önemli tasarım kararları

**Token taşıma.** Tarayıcı: access ve refresh token yalnızca `HttpOnly; SameSite=Strict` cookie'de, refresh
cookie'si `Path=/api/auth`; yanıt gövdesinde token yok. Mobil: `/api/auth/mobile/**` token'ları gövdede verir,
istemci `Authorization: Bearer` gönderir; bu uç noktalar hiç cookie okumaz. `Authorization` başlığı varsa yalnızca
o kullanılır, cookie'ye düşülmez; CSRF muafiyeti buna dayanır.

**Refresh token.** Opak (JWT değil, 256 bit rastgele), veritabanında yalnızca SHA-256 özeti. Her yenilemede değişir.
Eski token 5 saniye içinde gelirse paralel sekme sayılır (`409 REFRESH_IN_PROGRESS`, oturum yaşar); daha sonra
gelirse zincir çalınmış sayılır: oturum kapanır ve hesabın o ana kadar verilmiş bütün access token'ları iptal edilir.

**Toplu iptal, tek aggregate.** Şifre değişikliği, ban ve "tüm cihazlardan çık" yüzlerce `UserSession` kaydını
tek tek güncellemez (bir transaction'da bir aggregate kuralı). Hesaba bir kesim zamanı (`sessionsValidAfter`)
yazılır: bu zamandan önce başlamış oturumlar yenilenemez. Aynı olay, Redis'e hesap bazlı bir access-token kesim
zamanı yazar; filtre her istekte bakar.

**Fail-closed.** Redis'e ulaşılamazsa token'lı istekler `503` alır (iptal edilmiş mi bilinmeden içeri alınmaz).
İptal dinleyicisi `BEFORE_COMMIT` fazında çalışır: Redis'e yazamazsa şifre değişikliği / ban geri alınır ve istemci
503 görür; "yapıldı" denip token'lar açık kalmaz. Ters yön (Redis yazıldı, commit düştü) yalnızca fazladan bir
sessiz yenileme demektir. Aynı milisaniyedeki token ve oturumlar da kesimin içinde sayılır.

**Giriş.** Hatalı şifre, olmayan e-posta, devre dışı hesap, yanlış hesap tipi: hepsi aynı `401
INVALID_CREDENTIALS`; olmayan e-postada da BCrypt çalışır (zamanlama farkı yok). 5 hatalı denemeden sonra o e-posta +
IP 15 dakika kilitlenir; IP başına ayrıca genel bir sınır var. Sayaçlar Redis'te, anahtarlarda e-postanın özeti var.

**Yetki katmanları.** `TYPE_*` yetkisi kodda hesabın tipinden, `ROLE_ADMIN`/`ROLE_SUPER_ADMIN` admin
seviyesinden, `MEMBERSHIP_*` membership modülünden gelen seviyeden üretilir; veritabanındaki rol adları `ROLE_`,
`TYPE_`, `MEMBERSHIP_` ile başlayamaz (hem kodda hem `CHECK` kısıtında). `/api/admin/**` URL kuralı `TYPE_ADMIN`
ister; metotlarda `@PreAuthorize` izin (`customer:write`) ya da rol arar. Rol hiyerarşisi: `SUPER_ADMIN > ADMIN`
(identity), `GOLD > SILVER > BRONZE` (membership modülü kendi satırlarını `AuthorityHierarchyContribution` ile
verir; güvenlik ayarı seviyeleri tanımaz).

**Hatalar.** Tüm hatalar RFC 9457 (eski adıyla 7807) Problem Details: `{"status":403,"title":..,"detail":"Türkçe mesaj","code":"ACCESS_DENIED"}`.
İstemci `code` alanına göre davranır (ör. `TOKEN_EXPIRED` → sessiz yenileme). İç ayrıntı (stack trace, SQL, sınıf
adı) istemciye gitmez.

### API

| Metot | Yol | Modül | Yetki |
|---|---|---|---|
| POST | `/api/auth/register` | identity | herkes (CSRF) |
| POST | `/api/auth/login`, `/refresh`, `/logout` | identity | herkes (tarayıcı, cookie) |
| POST | `/api/auth/mobile/login`, `/refresh`, `/logout` | identity | herkes (mobil, gövde) |
| POST | `/api/auth/logout-all` | identity | giriş yapmış |
| GET | `/api/me` | identity | giriş yapmış |
| POST | `/api/account/password` | identity | giriş yapmış (müşteri ve admin) |
| GET, DELETE | `/api/sessions`, `/api/sessions/{id}` | identity | giriş yapmış (kendi oturumları) |
| GET | `/api/customer/profile` | customer | `TYPE_CUSTOMER` |
| POST, DELETE | `/api/customer/addresses`, `/{id}` | customer | `TYPE_CUSTOMER` |
| PUT | `/api/customers/{id}/name` | customer | kendisi ya da `customer:write` |
| GET | `/api/membership` | membership | `TYPE_CUSTOMER` (üyelik yoksa 204) |
| GET | `/api/content/articles`, `/{id}`, `/discount` | content | `TYPE_CUSTOMER` |
| GET | `/api/content/tiers/{tier}/articles` | content | `MEMBERSHIP_{tier}` (hiyerarşiyle) |
| GET | `/api/content/premium/articles` | content | `content:premium:read` |
| GET | `/api/admin/dashboard` | staff | `ROLE_ADMIN` |
| GET | `/api/admin/customers?page&size&sort&direction&email` | customer | `customer:read` |
| GET | `/api/admin/accounts?ids=` | identity | `customer:read` (en fazla 100 id) |
| GET | `/api/admin/memberships?ids=` | membership | `customer:read` (en fazla 100 id) |
| POST | `/api/admin/customers/{id}/disable`, `/enable` | identity | `customer:write` |
| PUT, DELETE | `/api/admin/customers/{id}/membership` | membership | `customer:write` |
| GET, DELETE | `/api/admin/customers/{id}/sessions[/{sessionId}]` | identity | `customer:read` / `customer:write` |
| GET, DELETE | `/api/admin/admins/{id}/sessions[/{sessionId}]` | identity | `ROLE_SUPER_ADMIN` |
| GET | `/actuator/health` | — | herkes |

Bölünmeyle değişenler: şifre değiştirme `/api/customer/password` → `/api/account/password` (şifre hesabındır,
profilin değil; artık adminler de kullanabilir). Profil yanıtında `email` → `contactEmail`, `membership` alanı
kalktı (`/api/membership`). Admin listesi yanıtında durum ve üyelik yok (yukarıdaki iki toplu sorgu). Dashboard
yanıtında e-posta, seviye ve izinler yok (`/api/me`'de var).

---

## 5. Kural dokümanlarına uyum

### Dokümandaki ArchUnit kurallarından yazıldığı gibi çalışmayanlar

Bu kurallar niyetleri korunarak uyarlandı; her biri `ArchitectureTest`'te "ADAPTED" notuyla işaretli.

| Kural | Sorun | Uyarlama |
|---|---|---|
| 4 — portlar interface olmalı | Aynı doküman Command/Query DTO'larını `port/in` içine koyuyor; kural bunlarla çelişiyor | Record ve enum'lar serbest, geri kalan her şey interface |
| 7 — domain alanları private | Enum sabitleri ve sabitler `public static final` | Statik alanlar hariç |
| 8, 9, 11 — `tr.gov.kurum.(#)` | `(#)` ArchUnit sözdizimi değil; "başka modül" yer tutucusu yok | Özel koşul: hedefin modülü kaynağınkinden farklıysa (ve `common` değilse) ihlal. Ek kural, kural 5.1'in "doğru" dediği yolu açıkça serbest bırakır: yalnızca bir **outbound adaptör** başka modülün `port/in`'ini çağırabilir |
| 11–18 — `..infrastructure.web..`, `..infrastructure.persistence..` | Dokümanın kendi paket ağacında (`infrastructure/adapter/in/web`) hiçbir şeyle eşleşmiyor; kurallar boşta geçer | `..infrastructure.adapter.in.web..`, `..infrastructure.adapter.out.persistence..` |
| 19 — `@Data` yasağı | `lombok.Data` SOURCE retention; bytecode'da görünmez, kural hiç düşemez | Kural duruyor; asıl korumayı LAZY / cascade / paket-özel kuralları yapıyor |
| 21 — `resideInAnyPackage("java.util.Date")` | Sınıf adını paket adı gibi arıyor; hiçbir zaman eşleşmez | Tam sınıf adı karşılaştırması; tek istisna jjwt adaptörü (jjwt API'si `Date` ister, sınırda `Instant`'a çevrilir) |

Uyarlanan kuralların gerçekten ihlal yakaladığı, bilerek kural dışı yazılmış geçici bir sınıfla denendi
(6 ihlalin 6'sı yakalandı), sonra sınıf silindi.

### Bilinçli kararlar

- **Görünüm DTO'ları.** Controller'lar `port/in` içindeki görünüm kayıtlarını (`CustomerProfileView` vb.) doğrudan
  döner. Bunlar domain nesnesi değil, zaten DTO; 1:1 kopyası bir koruma eklemezdi. Token/cookie taşıyan yanıtlar
  (`BrowserSessionResponse`, `MobileTokenResponse`, `MeResponse`) web katmanına özgü DTO'lardır.
- **Outbox yalnızca modüller arası olaylar için.** Kayıt → profil olayı kaybolursa müşteri profilsiz kalırdı;
  bu yüzden outbox'tan geçer (kural 6.2). Redis'e iptal yazmak ise outbox'a konmadı: `BEFORE_COMMIT` ile
  transaction'a bağlı; Redis yazılamazsa şifre değişikliği / ban geri alınır. Outbox'tan geçseydi iptal gecikirdi;
  güvenlik yan etkisinde gecikme yerine "ya ikisi ya hiçbiri" doğru olan.
- **`contracts` paketi.** Dokümanda yok. Tüketicinin üreticinin `domain/event`'ini import etmesi (dokümanın 6.1
  örneğindeki gibi) burada döngü kuruyordu. Olay sözleşmeleri bu yüzden ayrı, hiçbir modüle bağımlı olmayan bir
  pakette; üretici kendi domain olayını adaptörde çevirir. Shared kernel'a konmadı: doküman §8 bağlama özgü
  kavramları oradan uzak tutuyor.
- **Admin seviyesi identity'de.** `STANDARD`/`SUPER` yetkiyi belirler, yani giriş bilgisidir; staff modülü
  yalnızca kurumsal bilgiyi (ad, birim, bölgeler) tutar.
- **Dağıtık kilit yok.** Temizleme işi idempotent, küçük parçalarla ve `FOR UPDATE SKIP LOCKED` ile siliyor;
  birden fazla instance aynı anda çalışsa da birbirini beklemez, aynı satırı iki kez silmez. ShedLock gerekmiyor.

### Rehberden farklar

Önceki düzeltilmiş rehbere göre projede şunlar farklı (hepsi kurallardan ya da çalıştırırken çıkan sorunlardan):

- Access token'a `sid` (oturum id) ve `iat_ms` (milisaniye) eklendi. Standart `iat` saniye hassasiyetinde; kesim
  zamanıyla karşılaştırınca aynı saniyedeki token yanlış tarafa düşebiliyordu.
- Toplu iptal hesap bazlı kesim zamanıyla yapılıyor (bir transaction'da bir aggregate kuralı yüzünden).
- İptal dinleyicisi `AFTER_COMMIT` değil `BEFORE_COMMIT` (fail-closed, yukarıda).
- Tarayıcıda access cookie'sinin ömrü token'la aynı olduğundan, süre dolunca tarayıcı cookie'yi siler ve istek
  `AUTHENTICATION_REQUIRED` alır (`TOKEN_EXPIRED` değil). Konsolun istemcisi bu durumda da bir kez sessiz yenileme
  dener.
- Spring Security 7'de `DefaultMethodSecurityExpressionHandler.setRoleHierarchy` kullanımdan kalktı; hiyerarşi
  `DefaultAuthorizationManagerFactory` üzerinden veriliyor.
- Premium izni üyelikten değil `PREMIUM_READER` rolünden geliyor (izin ile üyeliği ayrı göstermek için).

---

## 6. Neyin doğrulandığı, neyin doğrulanmadığı

**Doğrulandı** (bu ortamda, PostgreSQL 16.15 ve Redis 7.0.15 ile):

- Proje uyarısız derleniyor (`-Xlint:all`).
- `mvn test`: **128 test, 0 hata** (31 mimari, 57 domain/mesaj, 40 entegrasyon). Entegrasyon testleri
  `IT_*` değişkenleriyle yerel PostgreSQL ve Redis'e karşı koştu.
- **Yükseltme:** eski şemadaki (V3) gerçek veritabanı V4 + V5 ile yükseltildi: 13 müşteri, 3 admin (yeni id'leri
  18–20), 78 oturum, 3 üyelik, 2 adres, roller ve izinler eksiksiz taşındı; demo veri betiği tekrar koştu, kopya
  üretmedi.
- **Boş veritabanı:** V1–V5 + demo veri; id'ler 1–8.
- Her iki veritabanında uygulama çalıştırıldı ve konsolun bütün sayfaları headless Chrome (Playwright) ile
  gezildi: **40/40 senaryo** geçti (profil kayıttan ~20 ms sonra outbox ile oluştu), admin listesi üç modülün
  verisini doğru birleştirdi, JavaScript hatası ya da CSP ihlali yok, cookie bayrakları beklendiği gibi.
- `spring.profiles.active: local` satırı `application.yml`'da dururken testlerde yalnızca `test` profili açık.
- Önceki sürümde: üretim modunda sırsız, zayıf sırla ve veritabanı adresi olmadan başlamadığı.

**Bu ortamda denenemedi** (Docker yoktu):

- `mvn spring-boot:run` ile Docker Compose üzerinden başlatma. Aynı uygulama aynı profille, PostgreSQL ve Redis
  elle verilerek çalıştırıldı; fark yalnızca bağlantı bilgisinin Compose'dan gelmesi.
- Testcontainers yolu. Aynı testler aynı veritabanı sürümüne karşı `IT_*` yoluyla koştu.
- Konsol bir insan tarafından tıklanmadı; tarayıcı testleri otomatikti.

### Bilinen sınırlar

- Listeden **tek bir oturumu** kapatmak o cihazın refresh token'ını hemen durdurur; elindeki access token en fazla
  15 dakika daha çalışır. Anında kesmek için "tüm cihazlardan çık" ya da ban.
- Üyelik ve rol değişiklikleri token'a bir sonraki yenilemede yansır (en fazla 15 dakika).
- Kayıttan hemen sonra (outbox teslim edene kadar, normalde 1 saniyeden az) profil `404` döner.
- Admin profili (staff) şimdilik yalnızca migration ve demo veriyle oluşur; admin açma akışı yok.
- Kayıt uç noktası, e-posta zaten kayıtlıysa `409` döner; yani kayıt üzerinden e-postanın varlığı öğrenilebilir.
  Bunu kapatmak e-posta doğrulamalı bir kayıt akışı ister (kapsam dışı).
- Yerel profilde JWT anahtarı her başlatmada yeniden üretilir; uygulama yeniden başlayınca herkes yeniden giriş yapar.
