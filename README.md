# Smart Email Automation (Ağıllı E-poçt Avtomatlaşdırılması)

Bu layihə Spring Boot əsasında qurulmuş elektron poçt idarəetmə sisteminə ağıllı avtomatlaşdırma, məzmun analizi, açar sözlərə əsaslanan etiketləmə və NLP (Təbii Dil Emalı) əsaslı xülasə çıxarma imkanları əlavə edir.

---

## 🚀 Həyata Keçirilmiş Funksionallıqlar

### 1. E-maillərin Kateqoriyalara Ayrılması (Email Categorization)
E-maillər həm göndərən məlumatı (domain/headers), həm də mövzu və mətn daxilindəki məzmun analizi əsasında avtomatik olaraq aşağıdakı kateqoriyalara ayrılır:
- `PRIMARY` — Şəxsi və birbaşa ünsiyyət xarakterli e-maillər.
- `WORK` — İş, layihə, iclas, tapşırıq və hesabatlarla bağlı yazışmalar.
- `PROMOTIONS` — Endirim, kampaniya, xüsusi təkliflər və marketinq bülletenləri.
- `SOCIAL` — Sosial şəbəkə bildirişləri və dəvətlər (LinkedIn, Facebook, Twitter və s.).
- `UPDATES` — Təhlükəsizlik bildirişləri, təsdiq kodları (OTP), sistem yeniləmələri və hesab qəbzləri.
- `SPAM` — Şübhəli uduş, lotereya və ya spam məzmunlu e-maillər.

**Əlaqədar API Endpoint-ləri:**
- `GET /api/emails/category/{userId}?category=WORK` — İstifadəçinin seçilmiş kateqoriyadakı e-maillərini səhifələnmiş formada qaytarır.
- `GET /api/emails/categorized/{userId}` — Kateqoriyalar üzrə ümumi statistik baxış və e-maillərin qruplaşdırılmış siyahısı.
- `POST /api/emails/{emailId}/categorize` — Tək bir e-maili avtomatik analiz edib kateqoriyasını təyin edir.
- `POST /api/emails/categorize-all/{userId}` — İstifadəçinin bütün e-maillərini toplu şəkildə kateqoriyalaşdırır.

---

### 2. Açar Sözlərə Əsaslanan Etiketləmə (Keyword-based Labeling)
E-poçtun mövzu və mətnindəki xüsusi açar sözlər (Azərbaycan və İngilis dillərində) aşkarlanaraq e-mailə uyğun etiketlər (labels) təyin edilir:
- `URGENT` — "təcili", "urgent", "asap", "vacib", "immediate", "critical", "dərhal", "mühüm".
- `INVOICE` — "faktura", "invoice", "receipt", "ödəniş", "bill", "payment", "qaime", "hesab-faktura".
- `MEETING` — "görüş", "iclas", "meeting", "call", "zoom", "google meet", "teams", "təqvim".
- `PROJECT` — "layihə", "project", "task", "deadline", "tapşırıq", "sprint", "milestone", "jira", "github".
- `SECURITY` — "təhlükəsizlik", "security", "otp", "2fa", "verification", "parol", "password", "kod", "təsdiq".
- `SUPPORT` — "dəstək", "support", "help", "kömək", "issue", "ticket", "problem", "xəta", "bug".
- `HR` — "müsahibə", "interview", "vakansiya", "vacancy", "cv", "resume", "karyera", "iş təklifi".
- `FINANCE` — "maliyyə", "finance", "bank", "kredit", "credit", "transfer", "balans", "faiz", "vergi".
- **Dinamik Qaydalar**: `labelingService.addRule(label, keywords)` vasitəsilə yeni fərdi etiket qaydaları əlavə edilə bilər.

**Əlaqədar API Endpoint-ləri:**
- `GET /api/emails/label/{userId}?label=URGENT` — Seçilmiş etiketə malik e-maillərin siyahısı.
- `GET /api/emails/labels/{userId}` — İstifadəçinin istifadə olunan bütün etiketləri və onların say statistikası.
- `POST /api/emails/{emailId}/label` — E-mailin mətnini analiz edib açar sözlərə uyğun etiketləyir.
- `POST /api/emails/label-all/{userId}` — Bütün e-mailləri toplu şəkildə etiketləyir.
- `POST /api/emails/{emailId}/labels?label=CUSTOM_TAG` — E-mailə əl ilə xüsusi etiket əlavə edir.
- `DELETE /api/emails/{emailId}/labels?label=CUSTOM_TAG` — E-maildən etiketi silir.

---

### 3. NLP (Təbii Dil Emalı) Vasitəsilə Xülasə Yaradılması (NLP Email Summarization)
Uzun və mürəkkəb e-maillərdən vaxt itirmədən vacib informasiyanı əldə etmək üçün xüsusi NLP Extractive Summarization mühərriki hazırlanmışdır:
1. **Şablon təmizlənməsi (Boilerplate Cleaning)**: "Salam [ad]", "Dear [ad]", "Hörmətlə", "Best regards" və imza kimi şablon ifadələr mətndən təmizlənir.
2. **Cümlə və Söz Seqmentasiyası (Tokenization)**: Mətn cümlələrə və söz vahidlərinə bölünür.
3. **Stop-Words Filter**: İngilis və Azərbaycan dillərindəki bağlayıcılar və köməkçi sözlər ("və", "ilə", "bu", "the", "and", "is" və s.) çıxarılır.
4. **Term Frequency (TF) Hesablanması**: Məzmunda əsas məna daşıyan sözlərin tezlik və əhəmiyyət çəkisi hesablanır. Mövzu (Subject) daxilindəki sözlərə ikiqat çəki verilir.
5. **Cümlə Qiymətləndirmə Alqoritmi (Sentence Scoring)**:
   - *Content Density*: Cümlədəki vacib sözlərin sıxlığı.
   - *Position Bonus (Lead Bias)*: İlk cümlələr adətən e-poçtun əsas məqsədini ifadə etdiyi üçün əlavə xal alır.
   - *Action / Intent Boost*: Hərəkət və tələb bildirən sözlər ("xahiş edirik", "zəhmət olmasa", "deadline", "son tarix", "please", "confirm") olan cümlələr xüsusi vurğulanır.
6. **Xronoloji Sintez**: Ən yüksək xal qazanmış ən vacib cümlələr mətnin təbii ardıcıllığı qorunmaqla xülasələşdirilir.
7. **Fəaliyyət Maddələri (Key Action Items)**: E-maildəki konkret tapşırıqlar, tarixlər və suallar ayrıca maddələr şəklində çıxarılır.
8. **Statistika**: Orijinal söz sayı, xülasə söz sayı və sıxılma faizi (compression percentage) hesablanır.

**Əlaqədar API Endpoint-ləri:**
- `POST /api/emails/{emailId}/summarize` — E-mail üçün NLP xülasəsi yaradır və verilənlər bazasında saxlayır.
- `GET /api/emails/{emailId}/summary` — E-mailin strukturlaşdırılmış xülasəsini (`EmailSummaryDto`) qaytarır.
- `POST /api/emails/summarize-all/{userId}` — İstifadəçinin bütün e-maillərini toplu xülasələşdirir.

---

### 4. Smart Pipeline (Tam Avtomatlaşdırılmış Zəncir)
- `POST /api/emails/send` metodu ilə göndərilən hər bir e-mail avtomatik olaraq:
  1. Kateqoriyalaşdırılır (`EmailCategory`),
  2. Açar sözlər üzrə etiketlənir (`Set<String> labels`),
  3. NLP xülasəsi yaradılır (`summary`).
- `POST /api/emails/{emailId}/process-smart` — Mövcud e-mail üçün hər 3 əməliyyatı tək sorğu ilə icra edir.
- `POST /api/emails/process-smart-all/{userId}` — İstifadəçinin bütün e-mailləri üçün ağıllı emal prosesini icra edir.

---

## 🛠️ Layihə Arxitekturası

```
src/main/java/org/example/gmailapplication/
├── config/
│   └── SwaggerConfig.java
├── controller/
│   └── EmailController.java               <- REST API endpoint-ləri
├── dto/
│   ├── CategorizedEmailsResponseDto.java  <- Kateqoriyalar üzrə qruplaşdırılmış DTO
│   ├── EmailDraftRequest.java
│   ├── EmailResponseDto.java              <- Kateqoriya, etiketlər və xülasə daxil
│   ├── EmailSendRequest.java
│   ├── EmailsListResponseDto.java         <- Kateqoriya, etiketlər və xülasə daxil
│   ├── EmailSummaryDto.java               <- NLP xülasə, maddələr və sıxılma faizi
│   ├── LabelCountDto.java                 <- Etiket statistikası
│   └── SidebarSummaryDto.java
├── entity/
│   ├── Email.java                         <- Category, Labels və Summary sahələri
│   └── User.java
├── enums/
│   ├── EmailCategory.java                 <- PRIMARY, WORK, PROMOTIONS, SOCIAL, UPDATES, SPAM
│   └── EmailFolder.java                   <- INBOX, SENT, DRAFT, TRASH
├── exceptions/
│   ├── GlobalExceptionHandler.java
│   └── NotFoundException.java
├── mapper/
│   └── EmailMapper.java                   <- MapStruct + DTO çeviriciləri
├── repository/
│   ├── EmailRepository.java               <- Kateqoriya və etiket axtarış sorğuları
│   └── UserRepository.java
└── services/
    ├── EmailCategorizationService.java    <- Ağıllı kateqoriyalaşdırma mühərriki
    ├── EmailLabelingService.java          <- Açar sözlərə əsaslanan etiketləmə
    ├── EmailSummarizationService.java     <- NLP əsaslı xülasə çıxarma xidməti
    └── EmailService.java                  <- Əsas biznes məntiqi və inteqrasiya
```

---

## 📑 Swagger / OpenAPI Sənədləşməsi
Layihə işə salındıqdan sonra bütün API endpoint-ləri və onların test interfeysi brauzerdə aşağıdakı ünvanda əlçatandır:
```
http://localhost:8080/swagger-ui.html
```
