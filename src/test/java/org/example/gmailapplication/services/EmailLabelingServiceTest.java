package org.example.gmailapplication.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailLabelingServiceTest {

    private EmailLabelingService labelingService;

    @BeforeEach
    void setUp() {
        labelingService = new EmailLabelingService();
    }

    @Test
    @DisplayName("Should extract URGENT and INVOICE labels from keywords")
    void testExtractUrgentAndInvoiceLabels() {
        String subject = "Təcili: Aylıq faktura və ödəniş hesabatı";
        String body = "Xahiş edirik dərhal ödəniş qaiməsini yoxlayasınız. Gecikmə olarsa cərimə tətbiq ediləcəkdir. Urgent invoice payment.";

        Set<String> labels = labelingService.extractLabels(subject, body);

        assertTrue(labels.contains("URGENT"), "Should contain URGENT label");
        assertTrue(labels.contains("INVOICE"), "Should contain INVOICE label");
    }

    @Test
    @DisplayName("Should extract MEETING and PROJECT labels")
    void testExtractMeetingAndProjectLabels() {
        String subject = "Həftəlik layihə müzakirəsi və iclas";
        String body = "Salam komanda, sprint tapşırıqları və layihənin son vəziyyəti ilə bağlı Zoom üzərindən görüş keçiriləcək.";

        Set<String> labels = labelingService.extractLabels(subject, body);

        assertTrue(labels.contains("MEETING"), "Should contain MEETING label");
        assertTrue(labels.contains("PROJECT"), "Should contain PROJECT label");
    }

    @Test
    @DisplayName("Should extract SECURITY label from 2FA / OTP keywords")
    void testExtractSecurityLabel() {
        String subject = "Təhlükəsizlik bildirişi: Şifrə dəyişdirilməsi";
        String body = "Yeni giriş cəhdi aşkarlandı. Birdəfəlik kod (OTP) vasitəsilə təsdiq edin.";

        Set<String> labels = labelingService.extractLabels(subject, body);

        assertTrue(labels.contains("SECURITY"), "Should contain SECURITY label");
    }

    @Test
    @DisplayName("Should support adding custom dynamic keyword rules")
    void testCustomKeywordRule() {
        labelingService.addRule("VIP_CLIENT", Arrays.asList("vip", "xüsusi müştəri", "platinum"));

        Set<String> labels = labelingService.extractLabels("Xoş gəlmisiniz", "Hörmətli platinum müştərimiz, sizin üçün xüsusi xidmət hazırdır.");

        assertTrue(labels.contains("VIP_CLIENT"), "Should detect custom rule VIP_CLIENT");
    }
}
