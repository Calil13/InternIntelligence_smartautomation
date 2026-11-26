package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.EmailSummaryDto;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DemoDataService {

    public static final String DEMO_EMAIL = "demo@smartmail.dev";

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final EmailCategorizationService categorizationService;
    private final EmailLabelingService labelingService;
    private final EmailSummarizationService summarizationService;

    @Transactional
    public User getOrCreateDemoUser() {
        User demoUser = userRepository.findByEmail(DEMO_EMAIL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(DEMO_EMAIL)
                        .fullName("SmartMail Demo User")
                        .oauthProvider("DEMO")
                        .oauthConnected(false)
                        .build()));

        if (emailRepository.countByUserIdAndFolder(demoUser.getId(), EmailFolder.INBOX) == 0) {
            seedSampleEmails(demoUser);
        }
        return demoUser;
    }

    private void seedSampleEmails(User user) {
        List<Email> samples = new ArrayList<>();

        samples.add(buildSmartEmail(
                user,
                "manager@techcorp.az",
                "Aysel Əliyeva",
                "Təcili: Sprint layihə iclası və Q4 faktura təsdiqi",
                "Salam komanda,\n\nSabah saat 10:00-da Google Meet üzərindən yeni sprint layihə tapşırıqları və deadline müzakirəsi üçün iclas keçiriləcək. "
                        + "Həmçinin müştəri fakturası və ödəniş qaiməsi bu gün saat 18:00-dək təsdiqlənməlidir. "
                        + "Xahiş edirik bütün departament rəhbərləri təqdimat slaydlarını və büdcə hesabatını hazır saxlasınlar.\n\nHörmətlə,\nAysel",
                EmailFolder.INBOX,
                false,
                true,
                LocalDateTime.now().minusHours(2)
        ));

        samples.add(buildSmartEmail(
                user,
                "no-reply@auth-cloud.io",
                "Cloud Security",
                "Security alert: Your 2FA verification code (OTP)",
                "Hörmətli istifadəçi, hesabınıza yeni giriş cəhdi qeydə alındı. "
                        + "Birdəfəlik təhlükəsizlik təsdiq kodu (OTP): 849201. "
                        + "Əgər bu siz deyilsinizsə, dərhal parolunuzu yeniləyin və dəstək komandasına müraciət edin.",
                EmailFolder.INBOX,
                false,
                false,
                LocalDateTime.now().minusHours(5)
        ));

        samples.add(buildSmartEmail(
                user,
                "invitations@linkedin.com",
                "LinkedIn",
                "Kamran Həsənov sent you an invitation to connect",
                "Hi, Kamran Həsənov would like to connect with you on LinkedIn and tagged you in a post about Java & Spring Boot microservices. Grow your professional network today!",
                EmailFolder.INBOX,
                true,
                false,
                LocalDateTime.now().minusDays(1)
        ));

        samples.add(buildSmartEmail(
                user,
                "marketing@megashop.az",
                "MegaShop Deals",
                "Böyük Endirim Kampaniyası — 50% kupon fürsəti!",
                "Xüsusi təklif! Bu həftə sonu bütün elektronika məhsullarına 50% endirim və pulsuz çatdırılma mövcuddur. Promo kod: SMART50. Abunəni ləğv etmək üçün linkə keçin.",
                EmailFolder.INBOX,
                true,
                false,
                LocalDateTime.now().minusDays(2)
        ));

        samples.add(buildSmartEmail(
                user,
                "hr@fintech.com",
                "Talent Acquisition",
                "Interview invitation for Senior Backend Engineer",
                "Dear candidate, thank you for submitting your CV. We reviewed your resume and would like to schedule a technical interview via Zoom this Thursday at 14:00. Please confirm your availability.",
                EmailFolder.INBOX,
                false,
                true,
                LocalDateTime.now().minusDays(3)
        ));

        samples.add(buildSmartEmail(
                user,
                "murad@gmail.com",
                "Murad Quliyev",
                "Axşam görüşək?",
                "Salam, necəsən? Bu həftə işlər necə gedir? Vaxtın varsa axşam saat 19:00-da çay içməyə çıxaq, bir az söhbət edək.",
                EmailFolder.INBOX,
                true,
                false,
                LocalDateTime.now().minusDays(4)
        ));

        samples.add(buildSmartEmail(
                user,
                "lottery-prize@unknown-promo.xyz",
                "Global Lottery",
                "Congratulations! You won 1,000,000 dollars!",
                "Qazandınız! You are the selected winner of our crypto giveaway. Send wire transfer details to claim your prize immediately!",
                EmailFolder.INBOX,
                true,
                false,
                LocalDateTime.now().minusDays(5)
        ));

        samples.add(buildSmartEmail(
                user,
                user.getEmail(),
                user.getFullName(),
                "Q3 Project Milestone Report",
                "Please find attached our Q3 project milestone summary. All Jira tickets for the deployment release are verified and ready for production.",
                EmailFolder.SENT,
                true,
                false,
                LocalDateTime.now().minusDays(1)
        ));

        emailRepository.saveAll(samples);
    }

    private Email buildSmartEmail(
            User user,
            String senderEmail,
            String senderName,
            String subject,
            String body,
            EmailFolder folder,
            boolean isRead,
            boolean isStarred,
            LocalDateTime createdAt) {
        EmailCategory category = categorizationService.determineCategory(subject, body, senderEmail);
        Set<String> labels = labelingService.extractLabels(subject, body);
        EmailSummaryDto summaryDto = summarizationService.generateSummary(null, subject, body);
        String takeaways = summaryDto.getKeyTakeaways() != null
                ? String.join("\n", summaryDto.getKeyTakeaways())
                : "";

        return Email.builder()
                .user(user)
                .senderEmail(senderEmail)
                .senderName(senderName)
                .recipientEmail(folder == EmailFolder.SENT ? "team@techcorp.az" : user.getEmail())
                .recipientName(folder == EmailFolder.SENT ? "Engineering Team" : user.getFullName())
                .subject(subject)
                .body(body)
                .folder(folder)
                .category(category)
                .labels(new HashSet<>(labels))
                .summary(summaryDto.getSummary())
                .keyTakeaways(takeaways)
                .originalWordCount(summaryDto.getOriginalWordCount())
                .summaryWordCount(summaryDto.getSummaryWordCount())
                .compressionPercentage(summaryDto.getCompressionPercentage())
                .isRead(isRead)
                .isStarred(isStarred)
                .createdAt(createdAt)
                .build();
    }
}
