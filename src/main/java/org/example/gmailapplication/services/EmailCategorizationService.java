package org.example.gmailapplication.services;

import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.enums.EmailCategory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailCategorizationService {

    private static final Map<EmailCategory, List<String>> CATEGORY_KEYWORDS = new EnumMap<>(EmailCategory.class);

    static {
        // SPAM keywords
        CATEGORY_KEYWORDS.put(EmailCategory.SPAM, Arrays.asList(
                "lottery", "you won", "qazandınız", "million dollars", "claim your prize",
                "wire transfer", "transfer urgently", "crypto giveaway", "selected winner",
                "casino", "viagra", "100% free money", "congratulations! you won", "fast cash"
        ));

        // PROMOTIONS keywords
        CATEGORY_KEYWORDS.put(EmailCategory.PROMOTIONS, Arrays.asList(
                "discount", "sale", "endirim", "kampaniya", "promo", "voucher", "deal",
                "kupon", "special offer", "təklif", "newsletter", "unsubscribe", "abunə",
                "shop now", "black friday", "save up to", "limited time offer", "hədiyyə",
                "pulsuz çatdırılma", "free shipping", "clearance", "exclusive deal", "endirimli",
                "best price", "promo code", "xüsusi təklif", "qiymət"
        ));

        // SOCIAL keywords
        CATEGORY_KEYWORDS.put(EmailCategory.SOCIAL, Arrays.asList(
                "linkedin", "facebook", "twitter", "instagram", "tiktok", "youtube",
                "friend request", "new connection", "tagged you", "commented on",
                "invitation to connect", "şəbəkə", "dostluq", "sosial", "forum",
                "community", "follower", "invited you to", "shared a post", "qrup", "şəbəkəniz"
        ));

        // UPDATES keywords
        CATEGORY_KEYWORDS.put(EmailCategory.UPDATES, Arrays.asList(
                "security alert", "verification code", "otp", "statement", "confirm your",
                "təsdiq edin", "şifrə", "kod", "yeniləmə", "hesabat", "bildiriş", "receipt",
                "order confirmation", "sifarişiniz təsdiqləndi", "status update", "password reset",
                "account alert", "login attempt", "subscription renewed", "avtomatik bildiriş",
                "monthly statement", "ödənişiniz qəbul olundu", "hesabınız aktivləşdirildi"
        ));

        // WORK keywords
        CATEGORY_KEYWORDS.put(EmailCategory.WORK, Arrays.asList(
                "project", "task", "deadline", "meeting", "görüş", "iclas", "tapşırıq",
                "layihə", "contract", "müqavilə", "interview", "müsahibə", "hr", "sprint",
                "standup", "deliverable", "presentation", "təqdimat", "quarterly review",
                "agenda", "action items", "jira", "pull request", "business proposal",
                "iş planı", "həftəlik iclas", "müştəri", "hesabat", "işçi"
        ));
    }

    /**
     * Determines the most appropriate category for an email based on its sender, subject, and content.
     */
    public EmailCategory determineCategory(String subject, String body, String senderEmail) {
        String cleanSubject = (subject != null) ? subject.toLowerCase() : "";
        String cleanBody = (body != null) ? body.toLowerCase() : "";
        String cleanSender = (senderEmail != null) ? senderEmail.toLowerCase() : "";

        // 1. Sender-specific heuristics
        if (cleanSender.contains("linkedin.com") || cleanSender.contains("facebookmail.com")
                || cleanSender.contains("twitter.com") || cleanSender.contains("x.com")
                || cleanSender.contains("instagram.com")) {
            return EmailCategory.SOCIAL;
        }

        if (cleanSender.contains("marketing@") || cleanSender.contains("promo@")
                || cleanSender.contains("newsletter@") || cleanSender.contains("deals@")) {
            return EmailCategory.PROMOTIONS;
        }

        if (cleanSender.contains("security@") || cleanSender.contains("billing@")
                || cleanSender.contains("no-reply@") || cleanSender.contains("noreply@")
                || cleanSender.contains("alerts@")) {
            // Check if it's promotional first
            if (hasPromotionalSignals(cleanSubject, cleanBody)) {
                return EmailCategory.PROMOTIONS;
            }
            return EmailCategory.UPDATES;
        }

        // 2. Score each category
        Map<EmailCategory, Integer> scores = new EnumMap<>(EmailCategory.class);
        for (EmailCategory category : EmailCategory.values()) {
            scores.put(category, 0);
        }

        for (Map.Entry<EmailCategory, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            EmailCategory category = entry.getKey();
            List<String> keywords = entry.getValue();

            int score = 0;
            for (String kw : keywords) {
                // Subject matches carry triple weight
                if (containsWordOrPhrase(cleanSubject, kw)) {
                    score += 3;
                }
                // Body matches carry single weight
                if (containsWordOrPhrase(cleanBody, kw)) {
                    score += 1;
                }
            }
            scores.put(category, score);
        }

        // 3. Spam threshold check
        if (scores.get(EmailCategory.SPAM) >= 2) {
            return EmailCategory.SPAM;
        }

        // 4. Find category with maximum score
        EmailCategory bestCategory = EmailCategory.PRIMARY;
        int maxScore = 0;

        for (EmailCategory category : Arrays.asList(
                EmailCategory.WORK,
                EmailCategory.PROMOTIONS,
                EmailCategory.SOCIAL,
                EmailCategory.UPDATES
        )) {
            int score = scores.get(category);
            if (score > maxScore) {
                maxScore = score;
                bestCategory = category;
            }
        }

        // If score is too low, treat as general/personal PRIMARY email
        if (maxScore < 1) {
            return EmailCategory.PRIMARY;
        }

        log.debug("Categorized email (Subject: '{}') as {} with score {}", subject, bestCategory, maxScore);
        return bestCategory;
    }

    private boolean hasPromotionalSignals(String subject, String body) {
        return subject.contains("discount") || subject.contains("endirim") || subject.contains("sale")
                || subject.contains("offer") || body.contains("unsubscribe") || body.contains("abunəni ləğv et");
    }

    private boolean containsWordOrPhrase(String text, String keyword) {
        if (text.isEmpty() || keyword.isEmpty()) {
            return false;
        }
        // Match phrase or whole word
        String regex = "(?i)\\b" + Pattern.quote(keyword) + "\\b";
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS).matcher(text).find()
                || text.contains(keyword);
    }
}
