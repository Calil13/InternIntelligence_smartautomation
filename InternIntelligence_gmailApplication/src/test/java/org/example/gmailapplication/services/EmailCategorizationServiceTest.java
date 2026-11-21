package org.example.gmailapplication.services;

import org.example.gmailapplication.enums.EmailCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailCategorizationServiceTest {

    private EmailCategorizationService categorizationService;

    @BeforeEach
    void setUp() {
        categorizationService = new EmailCategorizationService();
    }

    @Test
    @DisplayName("Should categorize work-related email")
    void testCategorizeWorkEmail() {
        String subject = "Sprint planning and project deadline review";
        String body = "Salam komanda, sabah saat 10:00-da yeni sprint layihə tapşırıqları və deadline müzakirəsi üçün iclas təyin edilib.";
        String sender = "manager@company.com";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.WORK, category);
    }

    @Test
    @DisplayName("Should categorize promotional email")
    void testCategorizePromotionsEmail() {
        String subject = "Böyük Qış Endirimi - Bütün məhsullara 50% discount!";
        String body = "Xüsusi təklif! Kupon kodunu daxil edərək böyük endirim və pulsuz çatdırılma əldə edin. Abunəni ləğv etmək üçün bura klikləyin.";
        String sender = "marketing@shop.com";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.PROMOTIONS, category);
    }

    @Test
    @DisplayName("Should categorize social media email")
    void testCategorizeSocialEmail() {
        String subject = "You have a new connection request on LinkedIn";
        String body = "Elvin tagged you in a post. Connect with him and grow your professional network on LinkedIn!";
        String sender = "invitations@linkedin.com";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.SOCIAL, category);
    }

    @Test
    @DisplayName("Should categorize updates and verification email")
    void testCategorizeUpdatesEmail() {
        String subject = "Hesabınız üçün təhlükəsizlik bildirişi: Giriş kodu";
        String body = "Hörmətli istifadəçi, birdəfəlik təsdiq kodu (OTP): 482910. Bu kodu heç kimlə paylaşmayın. Security alert.";
        String sender = "no-reply@authservice.com";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.UPDATES, category);
    }

    @Test
    @DisplayName("Should categorize spam email")
    void testCategorizeSpamEmail() {
        String subject = "Congratulations! You won 1,000,000 dollars lottery prize!";
        String body = "Qazandınız! Wire transfer for your million dollars is waiting. Claim your prize immediately.";
        String sender = "lottery-winner@unknown-domain.xyz";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.SPAM, category);
    }

    @Test
    @DisplayName("Should default to PRIMARY for general conversational email")
    void testCategorizePrimaryEmail() {
        String subject = "Necəsən? Axşam çay içək?";
        String body = "Salam Rauf, necəsən? İşlər necə gedir? Vaxtın olanda xəbər elə, görüşək söhbət edək.";
        String sender = "dostum@gmail.com";

        EmailCategory category = categorizationService.determineCategory(subject, body, sender);
        assertEquals(EmailCategory.PRIMARY, category);
    }
}
