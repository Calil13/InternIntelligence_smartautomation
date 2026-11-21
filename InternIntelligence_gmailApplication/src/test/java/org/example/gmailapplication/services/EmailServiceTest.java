package org.example.gmailapplication.services;

import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.mapper.EmailMapper;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private EmailMapper emailMapper = new EmailMapper() {};

    @Spy
    private EmailCategorizationService categorizationService = new EmailCategorizationService();

    @Spy
    private EmailLabelingService labelingService = new EmailLabelingService();

    @Spy
    private EmailSummarizationService summarizationService = new EmailSummarizationService();

    @InjectMocks
    private EmailService emailService;

    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .id(1L)
                .email("sender@example.com")
                .fullName("Sender User")
                .password("pass123")
                .build();

        recipient = User.builder()
                .id(2L)
                .email("recipient@example.com")
                .fullName("Recipient User")
                .password("pass123")
                .build();
    }

    @Test
    @DisplayName("Send email should auto-categorize, label keywords, and summarize")
    void testSendEmailWithAutomation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));

        EmailSendRequest request = EmailSendRequest.builder()
                .senderId(1L)
                .recipientEmail("recipient@example.com")
                .subject("Təcili layihə müzakirəsi və faktura təsdiqi")
                .body("Salam komanda, xahiş edirik təcili olaraq layihə fakturasını və ödəniş cədvəlini yoxlayasınız. Deadline bu gün saat 18:00-dır.")
                .build();

        String result = emailService.sendEmail(request);

        assertNotNull(result);
        assertTrue(result.contains("sender@example.com"));

        // Verify that 2 emails were saved (SENT and INBOX)
        verify(emailRepository, times(2)).save(any(Email.class));
    }

    @Test
    @DisplayName("Should get emails by category")
    void testGetEmailsByCategory() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        Email email = Email.builder()
                .id(10L)
                .user(sender)
                .subject("Work report")
                .body("Report details")
                .category(EmailCategory.WORK)
                .folder(EmailFolder.INBOX)
                .createdAt(LocalDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(emailRepository.findByUserIdAndCategory(1L, EmailCategory.WORK, pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(email)));

        Page<EmailsListResponseDto> page = emailService.getEmailsByCategory(1L, EmailCategory.WORK, pageable);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals(EmailCategory.WORK, page.getContent().get(0).getCategory());
    }

    @Test
    @DisplayName("Should process smart pipeline for an email")
    void testProcessSmart() {
        Email email = Email.builder()
                .id(20L)
                .user(sender)
                .senderEmail("manager@company.com")
                .subject("Təcili görüş və layihə tapşırıqları")
                .body("Sabah saat 10:00-da layihə tapşırıqları və iclas təyin edilib. Xahiş edirik vaxtında qoşulun.")
                .category(EmailCategory.PRIMARY)
                .folder(EmailFolder.INBOX)
                .labels(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();

        when(emailRepository.findById(20L)).thenReturn(Optional.of(email));
        when(emailRepository.save(any(Email.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmailResponseDto response = emailService.processSmart(20L);

        assertNotNull(response);
        assertEquals(EmailCategory.WORK, response.getCategory());
        assertTrue(response.getLabels().contains("URGENT") || response.getLabels().contains("MEETING") || response.getLabels().contains("PROJECT"));
        assertNotNull(response.getSummary());
    }
}
