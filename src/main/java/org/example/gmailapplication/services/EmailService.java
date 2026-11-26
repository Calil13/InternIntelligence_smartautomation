package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.config.CacheConfig;
import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.exceptions.NotFoundException;
import org.example.gmailapplication.mapper.EmailMapper;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Autowired(required = false)
    private PerformanceMetricsService metricsService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
    })
    public String sendEmail(EmailSendRequest request) {
        long startNs = System.nanoTime();
        User sender = (request.getSenderId() != null)
                ? userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new NotFoundException("The sending user was not found: ID=" + request.getSenderId()))
                : userRepository.findByEmail(request.getSenderEmail())
                .orElseThrow(() -> new NotFoundException("The sending user was not found: " + request.getSenderEmail()));

        Optional<User> recipientOpt = userRepository.findByEmail(request.getRecipientEmail());

        String subject = (request.getSubject() != null && !request.getSubject().trim().isEmpty())
                ? request.getSubject()
                : "(No Subject)";

        String recipientName = recipientOpt.map(User::getFullName).orElse(null);

        EmailCategory category = categorizationService.determineCategory(subject, request.getBody(), sender.getEmail());
        Set<String> labels = labelingService.extractLabels(subject, request.getBody());
        EmailSummaryDto summaryDto = summarizationService.generateSummary(null, subject, request.getBody());

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
                .summary(summaryDto.getSummary())
                .keyTakeaways(joinTakeaways(summaryDto.getKeyTakeaways()))
                .originalWordCount(summaryDto.getOriginalWordCount())
                .summaryWordCount(summaryDto.getSummaryWordCount())
                .compressionPercentage(summaryDto.getCompressionPercentage())
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
                    .summary(summaryDto.getSummary())
                    .keyTakeaways(joinTakeaways(summaryDto.getKeyTakeaways()))
                    .originalWordCount(summaryDto.getOriginalWordCount())
                    .summaryWordCount(summaryDto.getSummaryWordCount())
                    .compressionPercentage(summaryDto.getCompressionPercentage())
                    .isRead(false)
                    .isStarred(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            emailRepository.save(inboxEmail);
        }

        recordSmartMetric(1, startNs);
        return "Email sent. Sender Email: " + sender.getEmail();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
    })
    public String saveDraft(EmailDraftRequest request) {
        User user = (request.getUserId() != null)
                ? userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + request.getUserId()))
                : userRepository.findByEmail(request.getUserEmail())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getUserEmail()));

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

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true)
    public EmailResponseDto getEmailMessage(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        if (!Boolean.TRUE.equals(email.getIsRead())) {
            email.setIsRead(true);
            emailRepository.save(email);
        }

        return emailMapper.mapToDto(email);
    }

    public Page<EmailsListResponseDto> getStarredEmails(Long userId, Pageable pageable) {
        validateUserExists(userId);
        return emailRepository.findByUserIdAndIsStarredTrue(userId, pageable)
                .map(emailMapper::mapToEmailsListDto);
    }

    public Page<EmailsListResponseDto> getEmailsByFolder(Long userId, EmailFolder folder, Pageable pageable) {
        validateUserExists(userId);
        return emailRepository.findByUserIdAndFolder(userId, folder, pageable)
                .map(emailMapper::mapToEmailsListDto);
    }

    public Page<EmailsListResponseDto> searchEmails(Long userId, String query, Pageable pageable) {
        validateUserExists(userId);
        String cleanQuery = query != null ? query.trim() : "";
        return emailRepository.searchByUserId(userId, cleanQuery, pageable)
                .map(emailMapper::mapToEmailsListDto);
    }

    // ==========================================
    // 1. CATEGORIZATION METHODS
    // ==========================================

    public Page<EmailsListResponseDto> getEmailsByCategory(Long userId, EmailCategory category, Pageable pageable) {
        validateUserExists(userId);
        return emailRepository.findByUserIdAndCategory(userId, category, pageable)
                .map(emailMapper::mapToEmailsListDto);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, key = "#userId")
    public CategorizedEmailsResponseDto getCategorizedEmails(Long userId) {
        validateUserExists(userId);

        Map<EmailCategory, Long> categoryCounts = new EnumMap<>(EmailCategory.class);
        Map<EmailCategory, List<EmailsListResponseDto>> categorizedMap = new EnumMap<>(EmailCategory.class);
        Pageable topTen = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        for (EmailCategory category : EmailCategory.values()) {
            long count = emailRepository.countByUserIdAndCategory(userId, category);
            categoryCounts.put(category, count);

            List<EmailsListResponseDto> dtos = emailRepository.findByUserIdAndCategory(userId, category, topTen)
                    .getContent().stream()
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, key = "#userId")
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
        return emailRepository.findByUserIdAndLabel(userId, label, pageable)
                .map(emailMapper::mapToEmailsListDto);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_USER_LABELS, key = "#userId")
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, key = "#userId")
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_EMAIL_SUMMARY, key = "#emailId")
    public EmailSummaryDto summarizeEmail(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        EmailSummaryDto summaryDto = summarizationService.generateSummary(
                email.getId(),
                email.getSubject(),
                email.getBody()
        );

        applySummaryMetadata(email, summaryDto);
        emailRepository.save(email);

        return summaryDto;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_EMAIL_SUMMARY, key = "#emailId")
    public EmailSummaryDto getEmailSummary(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        if (email.getSummary() != null && email.getOriginalWordCount() != null) {
            return EmailSummaryDto.builder()
                    .emailId(email.getId())
                    .subject(email.getSubject())
                    .summary(email.getSummary())
                    .keyTakeaways(splitTakeaways(email.getKeyTakeaways()))
                    .originalWordCount(email.getOriginalWordCount())
                    .summaryWordCount(email.getSummaryWordCount() != null ? email.getSummaryWordCount() : 0)
                    .compressionPercentage(email.getCompressionPercentage() != null ? email.getCompressionPercentage() : 0.0)
                    .build();
        }

        return summarizationService.generateSummary(
                email.getId(),
                email.getSubject(),
                email.getBody()
        );
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_EMAIL_SUMMARY, allEntries = true)
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
            applySummaryMetadata(email, summaryDto);
            updated++;
        }
        emailRepository.saveAll(userEmails);
        return updated;
    }

    // ==========================================
    // 4. SMART AUTOMATION PIPELINE (ALL-IN-ONE)
    // ==========================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_EMAIL_SUMMARY, key = "#emailId")
    })
    public EmailResponseDto processSmart(Long emailId) {
        long startNs = System.nanoTime();
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        runSmartPipelineOnEntity(email);
        emailRepository.save(email);
        recordSmartMetric(1, startNs);
        return emailMapper.mapToDto(email);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, key = "#userId"),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, key = "#userId"),
            @CacheEvict(cacheNames = CacheConfig.CACHE_EMAIL_SUMMARY, allEntries = true)
    })
    public int processSmartAll(Long userId) {
        long startNs = System.nanoTime();
        validateUserExists(userId);
        List<Email> userEmails = emailRepository.findByUserId(userId);
        int processedCount = 0;

        for (Email email : userEmails) {
            runSmartPipelineOnEntity(email);
            processedCount++;
        }

        emailRepository.saveAll(userEmails);
        recordSmartMetric(processedCount, startNs);
        return processedCount;
    }

    // ==========================================
    // OTHER UTILITY METHODS & LABEL RULES
    // ==========================================

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true)
    public boolean toggleStar(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        boolean newStarred = email.getIsStarred() == null || !email.getIsStarred();
        email.setIsStarred(newStarred);
        emailRepository.save(email);
        return newStarred;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true)
    public boolean toggleRead(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        boolean newRead = !Boolean.TRUE.equals(email.getIsRead());
        email.setIsRead(newRead);
        emailRepository.save(email);
        return newRead;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
    })
    public EmailResponseDto moveToTrash(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        email.setFolder(EmailFolder.TRASH);
        emailRepository.save(email);
        return emailMapper.mapToDto(email);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, key = "#userId")
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

    public Map<String, List<String>> getLabelRules() {
        return labelingService.getRules();
    }

    public Map<String, List<String>> addLabelRule(LabelRuleRequest request) {
        labelingService.addRule(request.getLabel(), request.getKeywords());
        return labelingService.getRules();
    }

    public void deleteLabelRule(String label) {
        labelingService.deleteRule(label);
    }

    private void runSmartPipelineOnEntity(Email email) {
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
        applySummaryMetadata(email, summaryDto);
    }

    private void applySummaryMetadata(Email email, EmailSummaryDto summaryDto) {
        email.setSummary(summaryDto.getSummary());
        email.setKeyTakeaways(joinTakeaways(summaryDto.getKeyTakeaways()));
        email.setOriginalWordCount(summaryDto.getOriginalWordCount());
        email.setSummaryWordCount(summaryDto.getSummaryWordCount());
        email.setCompressionPercentage(summaryDto.getCompressionPercentage());
    }

    private String joinTakeaways(List<String> takeaways) {
        if (takeaways == null || takeaways.isEmpty()) {
            return "";
        }
        return String.join("\n", takeaways);
    }

    private List<String> splitTakeaways(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void recordSmartMetric(int count, long startNs) {
        if (metricsService != null) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            metricsService.recordSmartProcessing(count, durationMs);
        }
    }

    private void validateUserExists(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + userId));
    }
}
