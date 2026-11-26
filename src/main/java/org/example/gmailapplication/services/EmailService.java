package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.exceptions.NotFoundException;
import org.example.gmailapplication.mapper.EmailMapper;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final EmailMapper emailMapper;
    private final EmailCategorizationService categorizationService;
    private final EmailLabelingService labelingService;
    private final EmailSummarizationService summarizationService;

    @Transactional
    public String sendEmail(EmailSendRequest request) {
        User sender = (request.getSenderId() != null)
                ? userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new RuntimeException("The sending user was not found: ID=" + request.getSenderId()))
                : userRepository.findByEmail(request.getSenderEmail())
                .orElseThrow(() -> new RuntimeException("The sending user was not found: " + request.getSenderEmail()));

        Optional<User> recipientOpt = userRepository.findByEmail(request.getRecipientEmail());

        String subject = (request.getSubject() != null && !request.getSubject().trim().isEmpty())
                ? request.getSubject()
                : "(No Subject)";

        String recipientName = recipientOpt.map(User::getFullName).orElse(null);

        // Smart automation: Auto-categorize, Auto-label with keywords, and NLP-summarize
        EmailCategory category = categorizationService.determineCategory(subject, request.getBody(), sender.getEmail());
        Set<String> labels = labelingService.extractLabels(subject, request.getBody());
        EmailSummaryDto summaryDto = summarizationService.generateSummary(null, subject, request.getBody());
        String summary = summaryDto.getSummary();

        Email sentEmail = Email.builder()
                .user(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getFullName())
                .recipientEmail(request.getRecipientEmail())
                .recipientName(recipientName)
                .subject(subject)
                .body(request.getBody())
                .folder(EmailFolder.SENT)
                .category(category)
                .labels(new HashSet<>(labels))
                .summary(summary)
                .isRead(true)
                .isStarred(false)
                .createdAt(LocalDateTime.now())
                .build();

        emailRepository.save(sentEmail);

        if (recipientOpt.isPresent()) {
            User recipient = recipientOpt.get();
            Email inboxEmail = Email.builder()
                    .user(recipient)
                    .senderEmail(sender.getEmail())
                    .senderName(sender.getFullName())
                    .recipientEmail(recipient.getEmail())
                    .recipientName(recipient.getFullName())
                    .subject(subject)
                    .body(request.getBody())
                    .folder(EmailFolder.INBOX)
                    .category(category)
                    .labels(new HashSet<>(labels))
                    .summary(summary)
                    .isRead(false)
                    .isStarred(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            emailRepository.save(inboxEmail);
        }

        return "Email sent. Sender Email: " + sender.getEmail();
    }

    @Transactional
    public String saveDraft(EmailDraftRequest request) {
        User user = (request.getUserId() != null)
                ? userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: ID=" + request.getUserId()))
                : userRepository.findByEmail(request.getUserEmail())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserEmail()));

        String subject = request.getSubject() != null ? request.getSubject() : "(No Subject)";
        EmailCategory category = categorizationService.determineCategory(subject, request.getBody(), user.getEmail());
        Set<String> labels = labelingService.extractLabels(subject, request.getBody());

        Email draft = Email.builder()
                .user(user)
                .senderEmail(user.getEmail())
                .senderName(user.getFullName())
                .recipientEmail(request.getRecipientEmail())
                .recipientName(null)
                .subject(subject)
                .body(request.getBody())
                .folder(EmailFolder.DRAFT)
                .category(category)
                .labels(new HashSet<>(labels))
                .isRead(false)
                .isStarred(false)
                .createdAt(LocalDateTime.now())
                .build();

        emailRepository.save(draft);
        return "Email of the person making the draft: " + request.getUserEmail();
    }

    public EmailResponseDto getEmailMessage(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        return emailMapper.mapToDto(email);
    }

    public Page<EmailsListResponseDto> getStarredEmails(Long userId, Pageable pageable) {
        validateUserExists(userId);
        Page<Email> starredEmails = emailRepository.findByUserIdAndIsStarredTrue(userId, pageable);
        return starredEmails.map(emailMapper::mapToEmailsListDto);
    }

    public Page<EmailsListResponseDto> getEmailsByFolder(Long userId, EmailFolder folder, Pageable pageable) {
        validateUserExists(userId);
        Page<Email> folderEmails = emailRepository.findByUserIdAndFolder(userId, folder, pageable);
        return folderEmails.map(emailMapper::mapToEmailsListDto);
    }

    // ==========================================
    // 1. CATEGORIZATION METHODS
    // ==========================================

    public Page<EmailsListResponseDto> getEmailsByCategory(Long userId, EmailCategory category, Pageable pageable) {
        validateUserExists(userId);
        Page<Email> emails = emailRepository.findByUserIdAndCategory(userId, category, pageable);
        return emails.map(emailMapper::mapToEmailsListDto);
    }

    public CategorizedEmailsResponseDto getCategorizedEmails(Long userId) {
        validateUserExists(userId);

        Map<EmailCategory, Long> categoryCounts = new EnumMap<>(EmailCategory.class);
        Map<EmailCategory, List<EmailsListResponseDto>> categorizedMap = new EnumMap<>(EmailCategory.class);

        for (EmailCategory category : EmailCategory.values()) {
            long count = emailRepository.countByUserIdAndCategory(userId, category);
            categoryCounts.put(category, count);

            List<Email> sampleEmails = emailRepository.findByUserIdAndCategory(userId, category);
            List<EmailsListResponseDto> dtos = sampleEmails.stream()
                    .limit(10)
                    .map(emailMapper::mapToEmailsListDto)
                    .collect(Collectors.toList());
            categorizedMap.put(category, dtos);
        }

        return CategorizedEmailsResponseDto.builder()
                .userId(userId)
                .categoryCounts(categoryCounts)
                .categorizedEmails(categorizedMap)
                .build();
    }

    @Transactional
    public EmailResponseDto categorizeEmail(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        EmailCategory detected = categorizationService.determineCategory(
                email.getSubject(),
                email.getBody(),
                email.getSenderEmail()
        );
        email.setCategory(detected);
        emailRepository.save(email);

        return emailMapper.mapToDto(email);
    }

    @Transactional
    public int categorizeAllEmails(Long userId) {
        validateUserExists(userId);
        List<Email> userEmails = emailRepository.findByUserId(userId);
        int updated = 0;

        for (Email email : userEmails) {
            EmailCategory detected = categorizationService.determineCategory(
                    email.getSubject(),
                    email.getBody(),
                    email.getSenderEmail()
            );
            email.setCategory(detected);
            updated++;
        }
        emailRepository.saveAll(userEmails);
        return updated;
    }

    // ==========================================
    // 2. KEYWORD-BASED LABELING METHODS
    // ==========================================

    public Page<EmailsListResponseDto> getEmailsByLabel(Long userId, String label, Pageable pageable) {
        validateUserExists(userId);
        Page<Email> emails = emailRepository.findByUserIdAndLabel(userId, label, pageable);
        return emails.map(emailMapper::mapToEmailsListDto);
    }

    public List<LabelCountDto> getUserLabels(Long userId) {
        validateUserExists(userId);
        List<Object[]> rawList = emailRepository.countLabelsByUserId(userId);
        List<LabelCountDto> result = new ArrayList<>();
        for (Object[] row : rawList) {
            String label = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            result.add(new LabelCountDto(label, count));
        }
        return result;
    }

    @Transactional
    public EmailResponseDto labelEmail(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        Set<String> detected = labelingService.extractLabels(email.getSubject(), email.getBody());
        if (email.getLabels() == null) {
            email.setLabels(new HashSet<>());
        }
        email.getLabels().addAll(detected);
        emailRepository.save(email);

        return emailMapper.mapToDto(email);
    }

    @Transactional
    public int labelAllEmails(Long userId) {
        validateUserExists(userId);
        List<Email> userEmails = emailRepository.findByUserId(userId);
        int updated = 0;

        for (Email email : userEmails) {
            Set<String> detected = labelingService.extractLabels(email.getSubject(), email.getBody());
            if (email.getLabels() == null) {
                email.setLabels(new HashSet<>());
            }
            email.getLabels().addAll(detected);
            updated++;
        }
        emailRepository.saveAll(userEmails);
        return updated;
    }

    @Transactional
    public EmailResponseDto addLabel(Long emailId, String label) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        if (label != null && !label.trim().isEmpty()) {
            if (email.getLabels() == null) {
                email.setLabels(new HashSet<>());
            }
            email.getLabels().add(label.trim().toUpperCase());
            emailRepository.save(email);
        }
        return emailMapper.mapToDto(email);
    }

    @Transactional
    public EmailResponseDto removeLabel(Long emailId, String label) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        if (email.getLabels() != null && label != null) {
            email.getLabels().removeIf(l -> l.equalsIgnoreCase(label.trim()));
            emailRepository.save(email);
        }
        return emailMapper.mapToDto(email);
    }

    // ==========================================
    // 3. NLP SUMMARIZATION METHODS
    // ==========================================

    @Transactional
    public EmailSummaryDto summarizeEmail(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        EmailSummaryDto summaryDto = summarizationService.generateSummary(
                email.getId(),
                email.getSubject(),
                email.getBody()
        );

        email.setSummary(summaryDto.getSummary());
        emailRepository.save(email);

        return summaryDto;
    }

    public EmailSummaryDto getEmailSummary(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        return summarizationService.generateSummary(
                email.getId(),
                email.getSubject(),
                email.getBody()
        );
    }

    @Transactional
    public int summarizeAllEmails(Long userId) {
        validateUserExists(userId);
        List<Email> userEmails = emailRepository.findByUserId(userId);
        int updated = 0;

        for (Email email : userEmails) {
            EmailSummaryDto summaryDto = summarizationService.generateSummary(
                    email.getId(),
                    email.getSubject(),
                    email.getBody()
            );
            email.setSummary(summaryDto.getSummary());
            updated++;
        }
        emailRepository.saveAll(userEmails);
        return updated;
    }

    // ==========================================
    // 4. SMART AUTOMATION PIPELINE (ALL-IN-ONE)
    // ==========================================

    @Transactional
    public EmailResponseDto processSmart(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        // 1. Categorize
        EmailCategory category = categorizationService.determineCategory(
                email.getSubject(),
                email.getBody(),
                email.getSenderEmail()
        );
        email.setCategory(category);

        // 2. Keyword-based Label
        Set<String> labels = labelingService.extractLabels(email.getSubject(), email.getBody());
        if (email.getLabels() == null) {
            email.setLabels(new HashSet<>());
        }
        email.getLabels().addAll(labels);

        // 3. NLP Summarize
        EmailSummaryDto summaryDto = summarizationService.generateSummary(
                email.getId(),
                email.getSubject(),
                email.getBody()
        );
        email.setSummary(summaryDto.getSummary());

        emailRepository.save(email);
        return emailMapper.mapToDto(email);
    }

    @Transactional
    public int processSmartAll(Long userId) {
        validateUserExists(userId);
        List<Email> userEmails = emailRepository.findByUserId(userId);
        int processedCount = 0;

        for (Email email : userEmails) {
            EmailCategory category = categorizationService.determineCategory(
                    email.getSubject(),
                    email.getBody(),
                    email.getSenderEmail()
            );
            email.setCategory(category);

            Set<String> labels = labelingService.extractLabels(email.getSubject(), email.getBody());
            if (email.getLabels() == null) {
                email.setLabels(new HashSet<>());
            }
            email.getLabels().addAll(labels);

            EmailSummaryDto summaryDto = summarizationService.generateSummary(
                    email.getId(),
                    email.getSubject(),
                    email.getBody()
            );
            email.setSummary(summaryDto.getSummary());
            processedCount++;
        }

        emailRepository.saveAll(userEmails);
        return processedCount;
    }

    // ==========================================
    // OTHER UTILITY METHODS
    // ==========================================

    @Transactional
    public boolean toggleStar(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        boolean newStarred = email.getIsStarred() == null || !email.getIsStarred();
        email.setIsStarred(newStarred);
        emailRepository.save(email);
        return newStarred;
    }

    public SidebarSummaryDto getSidebarSummary(Long userId) {
        validateUserExists(userId);

        return SidebarSummaryDto.builder()
                .unreadInboxCount(emailRepository.countByUserIdAndFolderAndIsReadFalse(userId, EmailFolder.INBOX))
                .starredCount(emailRepository.countByUserIdAndIsStarredTrue(userId))
                .sentCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.SENT))
                .draftCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.DRAFT))
                .trashCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.TRASH))
                .build();
    }

    private void validateUserExists(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + userId));
    }
}
