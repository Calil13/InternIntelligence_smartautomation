package org.example.gmailapplication.services;

import org.example.gmailapplication.dto.EmailSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailSummarizationServiceTest {

    private EmailSummarizationService summarizationService;

    @BeforeEach
    void setUp() {
        summarizationService = new EmailSummarizationService();
    }

    @Test
    @DisplayName("Should summarize a multi-sentence email into key informative sentences")
    void testSummarizeEmail() {
        String subject = "Rüblük Maliyyə Hesabatı və Yeni Strategiya";
        String body = "Salam komanda,\n\n" +
                "Ümid edirəm hər kəs yaxşıdır. " +
                "Bu gün keçirilən iclasda şirkətin üçüncü rüb maliyyə nəticələri təqdim olundu və gəlirlərin 25% artdığı qeydə alındı. " +
                "Yeni məhsul xəttinin istifadəyə verilməsi bazar payımızın genişlənməsinə böyük təkan verdi. " +
                "Lakin bəzi texniki infrastruktur gecikmələri hələ də aradan qaldırılmalıdır. " +
                "Xahiş edirik bütün departament rəhbərləri cümə gününədək son büdcə təkliflərini göndərsinlər. " +
                "\n\nHörmətlə,\nElvin Məmmədov";

        EmailSummaryDto summaryDto = summarizationService.generateSummary(1L, subject, body);

        assertNotNull(summaryDto);
        assertNotNull(summaryDto.getSummary());
        assertFalse(summaryDto.getSummary().isEmpty());
        assertTrue(summaryDto.getSummaryWordCount() < summaryDto.getOriginalWordCount(),
                "Summary word count should be less than original text");
        assertTrue(summaryDto.getCompressionPercentage() > 0,
                "Compression percentage should be positive");

        // Key takeaways check
        assertNotNull(summaryDto.getKeyTakeaways());
        assertFalse(summaryDto.getKeyTakeaways().isEmpty(), "Should extract key action item");

        // The summary should not retain email greetings/sign-offs
        assertFalse(summaryDto.getSummary().contains("Hörmətlə"));
    }

    @Test
    @DisplayName("Should handle very short emails gracefully")
    void testShortEmailSummary() {
        String subject = "Görüş saatı";
        String body = "Görüş sabah saat 15:00-da baş tutacaq.";

        EmailSummaryDto summaryDto = summarizationService.generateSummary(2L, subject, body);

        assertNotNull(summaryDto);
        assertEquals(body, summaryDto.getSummary());
    }

    @Test
    @DisplayName("Should handle empty content gracefully")
    void testEmptyEmailSummary() {
        EmailSummaryDto summaryDto = summarizationService.generateSummary(3L, "", "");

        assertNotNull(summaryDto);
        assertEquals("No content available to summarize.", summaryDto.getSummary());
    }
}
