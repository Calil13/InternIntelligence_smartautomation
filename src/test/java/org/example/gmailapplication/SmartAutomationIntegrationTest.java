package org.example.gmailapplication;

import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.repository.UserRepository;
import org.example.gmailapplication.services.CustomOAuth2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SmartAutomationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Web UI index page and auth status endpoint are publicly accessible")
    void testPublicEndpoints() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SmartMail Automation")));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.demoModeEnabled").value(true));
    }

    @Test
    @DisplayName("Protected API endpoints require OAuth 2.0 or demo session authentication")
    void testProtectedEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/metrics/performance"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/gmail/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Demo login, smart email search, caching, and persistent custom rules work end-to-end")
    void testEndToEndWorkspaceFlow() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.email").value("demo@smartmail.dev"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        User demoUser = userRepository.findByEmail("demo@smartmail.dev").orElseThrow();

        // Hit sidebar summary twice to verify Caffeine cache hit increment
        mockMvc.perform(get("/api/emails/sidebar-summary/" + demoUser.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inboxCount").doesNotExist())
                .andExpect(jsonPath("$.unreadInboxCount", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/emails/sidebar-summary/" + demoUser.getId()).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics/performance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHits", greaterThanOrEqualTo(1)));

        // Search endpoint
        mockMvc.perform(get("/api/emails/search/" + demoUser.getId())
                        .param("q", "sprint")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        // Custom persistent label rule lifecycle
        mockMvc.perform(post("/api/emails/rules")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"VIP_CLIENT\",\"keywords\":[\"platinum\",\"vip\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.VIP_CLIENT").isArray());

        mockMvc.perform(delete("/api/emails/rules/VIP_CLIENT").session(session))
                .andExpect(status().isOk());

        // Send email with smart automation
        String payload = String.format(
                "{\"senderId\":%d,\"recipientEmail\":\"partner@example.com\",\"subject\":\"Təcili müqavilə\",\"body\":\"Zəhmət olmasa sabah saat 11:00-dək müqaviləni təsdiqləyin.\"}",
                demoUser.getId()
        );
        mockMvc.perform(post("/api/emails/send")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("demo@smartmail.dev")));
    }

    @Test
    @DisplayName("OAuth2 user provisioning upserts Google user profile")
    void testOAuth2UserProvisioning() {
        User user = customOAuth2UserService.upsertGoogleUser(
                Map.of(
                        "sub", "google-uid-999",
                        "email", "oauth.tester@gmail.com",
                        "name", "OAuth Tester",
                        "picture", "https://example.com/avatar.png"
                ),
                "google"
        );

        assertNotNull(user.getId());
        assertEquals("oauth.tester@gmail.com", user.getEmail());
        assertEquals("google-uid-999", user.getGoogleSub());
        assertTrue(Boolean.TRUE.equals(user.getOauthConnected()));
        assertEquals("GOOGLE", user.getOauthProvider());
    }
}
