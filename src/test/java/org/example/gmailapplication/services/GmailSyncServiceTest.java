package org.example.gmailapplication.services;

import org.example.gmailapplication.dto.GmailSyncResultDto;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.exceptions.BadRequestException;
import org.example.gmailapplication.exceptions.GmailApiException;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.example.gmailapplication.services.GmailApiClient.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailSyncServiceTest {

    @Mock
    private GmailApiClient gmailApiClient;

    @Mock
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private UserRepository userRepository;

    private PerformanceMetricsService metricsService;
    private GmailSyncService gmailSyncService;
    private User oauthUser;
    private OAuth2AuthenticationToken oauthAuth;

    @BeforeEach
    void setUp() {
        metricsService = new PerformanceMetricsService(new ConcurrentMapCacheManager());
        CurrentUserResolver resolver = new CurrentUserResolver(userRepository);
        gmailSyncService = new GmailSyncService(
                gmailApiClient,
                authorizedClientManager,
                resolver,
                emailRepository,
                userRepository,
                new EmailCategorizationService(),
                new EmailLabelingService(),
                new EmailSummarizationService(),
                metricsService
        );
        ReflectionTestUtils.setField(gmailSyncService, "syncMaxResults", 25);
        ReflectionTestUtils.setField(gmailSyncService, "fetchConcurrency", 2);

        oauthUser = User.builder()
                .id(10L)
                .email("celil@gmail.com")
                .fullName("Celil")
                .googleSub("google-sub-123")
                .oauthConnected(true)
                .build();

        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(),
                Map.of("sub", "google-sub-123", "email", "celil@gmail.com"),
                "sub"
        );
        oauthAuth = new OAuth2AuthenticationToken(principal, List.of(), "google");
    }

    private void mockAuthorizedToken() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("cid")
                .clientSecret("csec")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "ya29.mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(registration, "google-sub-123", token);
        when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(client);
    }

    @Test
    @DisplayName("Initial sync fetches only non-duplicate Gmail messages and runs Smart Automation")
    void testInitialSyncWithDeduplication() {
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(oauthUser));
        mockAuthorizedToken();

        GmailProfileResponse profile = new GmailProfileResponse();
        profile.setEmailAddress("celil@gmail.com");
        profile.setHistoryId("5001");
        when(gmailApiClient.getProfile("ya29.mock-token")).thenReturn(profile);

        GmailMessageRef ref1 = new GmailMessageRef();
        ref1.setId("msg-existing");
        GmailMessageRef ref2 = new GmailMessageRef();
        ref2.setId("msg-new");
        GmailListResponse listResponse = new GmailListResponse();
        listResponse.setMessages(List.of(ref1, ref2));
        when(gmailApiClient.listMessages("ya29.mock-token", 25, null)).thenReturn(listResponse);

        // msg-existing is already in DB -> should NOT trigger getMessage
        when(emailRepository.findExistingGmailMessageIds(eq(10L), anyCollection()))
                .thenReturn(List.of("msg-existing"));

        GmailMessageResponse newMsg = buildGmailMessage(
                "msg-new",
                "Aysel <manager@company.com>",
                "celil@gmail.com",
                "Urgent sprint deadline and invoice",
                "Please review the project sprint task and confirm the invoice payment before 18:00."
        );
        when(gmailApiClient.getMessage("ya29.mock-token", "msg-new")).thenReturn(newMsg);

        GmailSyncResultDto result = gmailSyncService.syncForUser(oauthAuth);

        assertFalse(result.isIncrementalUsed());
        assertEquals(1, result.getFetchedCount());
        assertEquals(1, result.getSkippedDuplicateCount());
        assertEquals(1, result.getApiCallsSaved());
        assertEquals("5001", result.getHistoryId());

        verify(gmailApiClient, never()).getMessage("ya29.mock-token", "msg-existing");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Email>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailRepository).saveAll(savedCaptor.capture());
        Email saved = savedCaptor.getValue().get(0);
        assertEquals("msg-new", saved.getGmailMessageId());
        assertEquals(EmailCategory.WORK, saved.getCategory());
        assertTrue(saved.getLabels().contains("URGENT"));
        assertTrue(saved.getLabels().contains("INVOICE"));
        assertNotNull(saved.getSummary());
    }

    @Test
    @DisplayName("Incremental sync uses historyId and falls back gracefully when historyId expires (404)")
    void testIncrementalSyncAndExpiredFallback() {
        oauthUser.setGmailHistoryId("4900");
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(oauthUser));
        mockAuthorizedToken();

        when(gmailApiClient.listHistory("ya29.mock-token", "4900"))
                .thenThrow(new GmailApiException("History expired", HttpStatus.NOT_FOUND));

        GmailProfileResponse profile = new GmailProfileResponse();
        profile.setHistoryId("5200");
        when(gmailApiClient.getProfile("ya29.mock-token")).thenReturn(profile);

        GmailListResponse listResponse = new GmailListResponse();
        listResponse.setMessages(List.of());
        when(gmailApiClient.listMessages("ya29.mock-token", 25, null)).thenReturn(listResponse);

        GmailSyncResultDto result = gmailSyncService.syncForUser(oauthAuth);

        assertFalse(result.isIncrementalUsed());
        assertEquals("5200", result.getHistoryId());
        assertEquals(0, result.getFetchedCount());
    }

    @Test
    @DisplayName("Push labels creates missing Gmail labels and modifies Gmail message")
    void testPushLabelsToGmail() {
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(oauthUser));
        mockAuthorizedToken();

        Email email = Email.builder()
                .id(77L)
                .user(oauthUser)
                .gmailMessageId("gmail-77")
                .category(EmailCategory.WORK)
                .labels(new HashSet<>(Set.of("URGENT")))
                .build();
        when(emailRepository.findById(77L)).thenReturn(Optional.of(email));

        GmailLabelDto existing = new GmailLabelDto();
        existing.setId("LBL_1");
        existing.setName("URGENT");
        GmailLabelsListResponse existingResp = new GmailLabelsListResponse();
        existingResp.setLabels(List.of(existing));
        when(gmailApiClient.listLabels("ya29.mock-token")).thenReturn(existingResp);

        GmailLabelDto created = new GmailLabelDto();
        created.setId("LBL_2");
        created.setName("SMART_WORK");
        when(gmailApiClient.createLabel("ya29.mock-token", "SMART_WORK")).thenReturn(created);

        String response = gmailSyncService.pushLabelsToGmail(oauthAuth, 77L);

        assertTrue(response.contains("2"));
        verify(gmailApiClient).modifyMessageLabels(
                eq("ya29.mock-token"),
                eq("gmail-77"),
                argThat(ids -> ids.contains("LBL_1") && ids.contains("LBL_2")),
                eq(List.of())
        );
    }

    @Test
    @DisplayName("Sync rejects non-OAuth demo user cleanly")
    void testSyncRejectsDemoUser() {
        User demoUser = User.builder().id(1L).email("demo@smartmail.dev").oauthConnected(false).build();
        when(userRepository.findByEmail("demo@smartmail.dev")).thenReturn(Optional.of(demoUser));

        var demoAuth = new UsernamePasswordAuthenticationToken("demo@smartmail.dev", null, List.of());

        assertThrows(BadRequestException.class, () -> gmailSyncService.syncForUser(demoAuth));
    }

    private GmailMessageResponse buildGmailMessage(String id, String from, String to, String subject, String bodyText) {
        GmailHeader hFrom = new GmailHeader();
        hFrom.setName("From");
        hFrom.setValue(from);

        GmailHeader hTo = new GmailHeader();
        hTo.setName("To");
        hTo.setValue(to);

        GmailHeader hSub = new GmailHeader();
        hSub.setName("Subject");
        hSub.setValue(subject);

        GmailBody body = new GmailBody();
        body.setData(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyText.getBytes(StandardCharsets.UTF_8)));

        GmailPayload payload = new GmailPayload();
        payload.setMimeType("text/plain");
        payload.setHeaders(List.of(hFrom, hTo, hSub));
        payload.setBody(body);

        GmailMessageResponse msg = new GmailMessageResponse();
        msg.setId(id);
        msg.setThreadId("thread-" + id);
        msg.setLabelIds(List.of("INBOX", "UNREAD"));
        msg.setInternalDate(System.currentTimeMillis());
        msg.setPayload(payload);
        return msg;
    }
}
