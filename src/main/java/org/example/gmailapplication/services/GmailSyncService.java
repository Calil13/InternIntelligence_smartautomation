package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.config.CacheConfig;
import org.example.gmailapplication.dto.EmailSummaryDto;
import org.example.gmailapplication.dto.GmailStatusDto;
import org.example.gmailapplication.dto.GmailSyncResultDto;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.exceptions.BadRequestException;
import org.example.gmailapplication.exceptions.GmailApiException;
import org.example.gmailapplication.exceptions.NotFoundException;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.example.gmailapplication.services.GmailApiClient.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSyncService {

    private final GmailApiClient gmailApiClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final CurrentUserResolver currentUserResolver;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final EmailCategorizationService categorizationService;
    private final EmailLabelingService labelingService;
    private final EmailSummarizationService summarizationService;
    private final PerformanceMetricsService metricsService;

    @Value("${app.gmail.sync-max-results:25}")
    private int syncMaxResults;

    @Value("${app.gmail.fetch-concurrency:4}")
    private int fetchConcurrency;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_SIDEBAR_SUMMARY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_CATEGORIZED_EMAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_USER_LABELS, allEntries = true)
    })
    public GmailSyncResultDto syncForUser(Authentication authentication) {
        long startNs = System.nanoTime();
        User user = currentUserResolver.resolveRequired(authentication);
        String accessToken = resolveAccessToken(authentication, user);

        int apiCallsBefore = (int) metricsService.getSnapshot().getGmailApiCallsMade();
        boolean incrementalUsed = false;
        String latestHistoryId = user.getGmailHistoryId();
        List<String> candidateIds = new ArrayList<>();

        if (user.getGmailHistoryId() != null && !user.getGmailHistoryId().isBlank()) {
            try {
                GmailHistoryResponse historyResponse = gmailApiClient.listHistory(accessToken, user.getGmailHistoryId());
                incrementalUsed = true;
                if (historyResponse != null) {
                    if (historyResponse.getHistoryId() != null) {
                        latestHistoryId = historyResponse.getHistoryId();
                    }
                    if (historyResponse.getHistory() != null) {
                        for (GmailHistoryRecord record : historyResponse.getHistory()) {
                            if (record.getMessagesAdded() != null) {
                                for (GmailMessageAdded added : record.getMessagesAdded()) {
                                    if (added.getMessage() != null && added.getMessage().getId() != null) {
                                        candidateIds.add(added.getMessage().getId());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (GmailApiException ex) {
                if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                    log.info("Stored historyId {} expired for user {}; falling back to bounded list sync",
                            user.getGmailHistoryId(), user.getEmail());
                    incrementalUsed = false;
                } else {
                    throw ex;
                }
            }
        }

        if (!incrementalUsed) {
            GmailProfileResponse profile = gmailApiClient.getProfile(accessToken);
            if (profile != null && profile.getHistoryId() != null) {
                latestHistoryId = profile.getHistoryId();
            }
            GmailListResponse listResponse = gmailApiClient.listMessages(accessToken, syncMaxResults, null);
            if (listResponse != null && listResponse.getMessages() != null) {
                for (GmailMessageRef ref : listResponse.getMessages()) {
                    if (ref.getId() != null) {
                        candidateIds.add(ref.getId());
                    }
                }
            }
        }

        // Deduplicate message IDs against existing DB records in a single SQL query
        List<String> distinctIds = candidateIds.stream().distinct().toList();
        Set<String> existingIds = distinctIds.isEmpty()
                ? Set.of()
                : new HashSet<>(emailRepository.findExistingGmailMessageIds(user.getId(), distinctIds));

        List<String> idsToFetch = distinctIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();

        int skippedCount = distinctIds.size() - idsToFetch.size();
        if (skippedCount > 0) {
            metricsService.recordGmailApiCallsSaved(skippedCount);
        }

        List<Email> syncedEmails = fetchAndProcessInParallel(user, accessToken, idsToFetch);
        if (!syncedEmails.isEmpty()) {
            emailRepository.saveAll(syncedEmails);
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        user.setGmailHistoryId(latestHistoryId);
        user.setLastSyncedAt(syncedAt);
        userRepository.save(user);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        int apiCallsMade = (int) metricsService.getSnapshot().getGmailApiCallsMade() - apiCallsBefore;

        return GmailSyncResultDto.builder()
                .fetchedCount(syncedEmails.size())
                .skippedDuplicateCount(skippedCount)
                .incrementalUsed(incrementalUsed)
                .apiCallsMade(apiCallsMade)
                .apiCallsSaved(skippedCount)
                .durationMs(durationMs)
                .historyId(latestHistoryId)
                .syncedAt(syncedAt)
                .build();
    }

    public GmailStatusDto getStatus(Authentication authentication) {
        User user = currentUserResolver.resolveRequired(authentication);
        long total = emailRepository.findByUserId(user.getId()).stream()
                .filter(e -> e.getGmailMessageId() != null)
                .count();
        return GmailStatusDto.builder()
                .oauthConnected(Boolean.TRUE.equals(user.getOauthConnected()))
                .email(user.getEmail())
                .historyId(user.getGmailHistoryId())
                .lastSyncedAt(user.getLastSyncedAt())
                .totalSyncedEmails(total)
                .build();
    }

    public String pushLabelsToGmail(Authentication authentication, Long emailId) {
        User user = currentUserResolver.resolveRequired(authentication);
        String accessToken = resolveAccessToken(authentication, user);

        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        if (!email.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Email does not belong to the authenticated user.");
        }
        if (email.getGmailMessageId() == null || email.getGmailMessageId().isBlank()) {
            throw new BadRequestException("This email was created locally and does not have a Gmail message ID.");
        }

        Set<String> targetLabels = new LinkedHashSet<>(email.getLabels() != null ? email.getLabels() : Set.of());
        if (email.getCategory() != null) {
            targetLabels.add("SMART_" + email.getCategory().name());
        }
        if (targetLabels.isEmpty()) {
            return "No labels to push to Gmail.";
        }

        GmailLabelsListResponse existingLabelsResp = gmailApiClient.listLabels(accessToken);
        Map<String, String> nameToId = new HashMap<>();
        if (existingLabelsResp != null && existingLabelsResp.getLabels() != null) {
            for (GmailLabelDto dto : existingLabelsResp.getLabels()) {
                if (dto.getName() != null && dto.getId() != null) {
                    nameToId.put(dto.getName().toUpperCase(), dto.getId());
                }
            }
        }

        List<String> labelIdsToApply = new ArrayList<>();
        for (String labelName : targetLabels) {
            String key = labelName.toUpperCase();
            String labelId = nameToId.get(key);
            if (labelId == null) {
                GmailLabelDto created = gmailApiClient.createLabel(accessToken, key);
                if (created != null && created.getId() != null) {
                    labelId = created.getId();
                    nameToId.put(key, labelId);
                }
            }
            if (labelId != null) {
                labelIdsToApply.add(labelId);
            }
        }

        if (!labelIdsToApply.isEmpty()) {
            gmailApiClient.modifyMessageLabels(accessToken, email.getGmailMessageId(), labelIdsToApply, List.of());
        }
        return "Pushed " + labelIdsToApply.size() + " smart labels to Gmail message " + email.getGmailMessageId();
    }

    private List<Email> fetchAndProcessInParallel(User user, String accessToken, List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        int threads = Math.max(1, Math.min(fetchConcurrency, messageIds.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<GmailMessageResponse>> futures = messageIds.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> gmailApiClient.getMessage(accessToken, id), pool))
                    .toList();

            List<Email> result = new ArrayList<>(messageIds.size());
            long smartStartNs = System.nanoTime();
            for (CompletableFuture<GmailMessageResponse> future : futures) {
                GmailMessageResponse msg = future.join();
                if (msg != null) {
                    result.add(convertToSmartEmail(user, msg));
                }
            }
            long smartMs = (System.nanoTime() - smartStartNs) / 1_000_000;
            metricsService.recordSmartProcessing(result.size(), smartMs);
            return result;
        } finally {
            pool.shutdown();
        }
    }

    private Email convertToSmartEmail(User user, GmailMessageResponse msg) {
        String fromHeader = extractHeader(msg, "From");
        String toHeader = extractHeader(msg, "To");
        String subject = Optional.ofNullable(extractHeader(msg, "Subject"))
                .filter(s -> !s.isBlank())
                .orElse("(No Subject)");

        ParsedAddress from = parseAddress(fromHeader, "unknown@gmail.com");
        ParsedAddress to = parseAddress(toHeader, user.getEmail());

        String body = extractBodyText(msg.getPayload());
        if (body.isBlank() && msg.getSnippet() != null) {
            body = msg.getSnippet();
        }

        List<String> gmailLabels = msg.getLabelIds() != null ? msg.getLabelIds() : List.of();
        EmailFolder folder = mapFolder(gmailLabels);
        boolean isRead = !gmailLabels.contains("UNREAD");
        boolean isStarred = gmailLabels.contains("STARRED");

        LocalDateTime createdAt = msg.getInternalDate() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.getInternalDate()), ZoneId.systemDefault())
                : LocalDateTime.now();

        EmailCategory category = categorizationService.determineCategory(subject, body, from.email());
        Set<String> smartLabels = labelingService.extractLabels(subject, body);
        EmailSummaryDto summaryDto = summarizationService.generateSummary(null, subject, body);
        String takeaways = summaryDto.getKeyTakeaways() != null
                ? String.join("\n", summaryDto.getKeyTakeaways())
                : "";

        return Email.builder()
                .user(user)
                .gmailMessageId(msg.getId())
                .gmailThreadId(msg.getThreadId())
                .senderEmail(from.email())
                .senderName(from.name())
                .recipientEmail(to.email())
                .recipientName(to.name())
                .subject(subject)
                .body(body)
                .folder(folder)
                .category(category)
                .labels(new HashSet<>(smartLabels))
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

    private String resolveAccessToken(Authentication authentication, User user) {
        if (!Boolean.TRUE.equals(user.getOauthConnected())
                || !(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new BadRequestException("Google OAuth 2.0 login is required to access the live Gmail API.");
        }

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(oauthToken.getAuthorizedClientRegistrationId())
                .principal(oauthToken)
                .build();

        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new GmailApiException("OAuth 2.0 access token is missing or expired. Please sign in with Google again.", HttpStatus.UNAUTHORIZED);
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private String extractHeader(GmailMessageResponse msg, String targetName) {
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) {
            return null;
        }
        return msg.getPayload().getHeaders().stream()
                .filter(h -> targetName.equalsIgnoreCase(h.getName()))
                .map(GmailHeader::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractBodyText(GmailPayload payload) {
        if (payload == null) {
            return "";
        }
        if ("text/plain".equalsIgnoreCase(payload.getMimeType())
                && payload.getBody() != null && payload.getBody().getData() != null) {
            return decodeBase64Url(payload.getBody().getData());
        }
        if (payload.getParts() != null) {
            for (GmailPayload part : payload.getParts()) {
                String plain = extractBodyText(part);
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        }
        if ("text/html".equalsIgnoreCase(payload.getMimeType())
                && payload.getBody() != null && payload.getBody().getData() != null) {
            String html = decodeBase64Url(payload.getBody().getData());
            return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        }
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            return decodeBase64Url(payload.getBody().getData());
        }
        return "";
    }

    private String decodeBase64Url(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private EmailFolder mapFolder(List<String> labelIds) {
        if (labelIds.contains("TRASH")) return EmailFolder.TRASH;
        if (labelIds.contains("DRAFT")) return EmailFolder.DRAFT;
        if (labelIds.contains("SENT")) return EmailFolder.SENT;
        return EmailFolder.INBOX;
    }

    private ParsedAddress parseAddress(String header, String fallbackEmail) {
        if (header == null || header.isBlank()) {
            return new ParsedAddress(null, fallbackEmail);
        }
        int lt = header.indexOf('<');
        int gt = header.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String name = header.substring(0, lt).replace("\"", "").trim();
            String email = header.substring(lt + 1, gt).trim();
            return new ParsedAddress(name.isEmpty() ? null : name, email);
        }
        return new ParsedAddress(null, header.trim());
    }

    private record ParsedAddress(String name, String email) {
    }
}
