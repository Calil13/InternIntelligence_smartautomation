package org.example.gmailapplication.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.exceptions.GmailApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
public class GmailApiClient {

    private final RestClient restClient;
    private final PerformanceMetricsService metricsService;

    @Autowired
    public GmailApiClient(
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${app.gmail.base-url:https://gmail.googleapis.com/gmail/v1}") String baseUrl,
            @Autowired(required = false) PerformanceMetricsService metricsService) {
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder.baseUrl(baseUrl).build();
        this.metricsService = metricsService;
    }

    public GmailProfileResponse getProfile(String accessToken) {
        return executeWithRetry(() -> restClient.get()
                .uri("/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GmailProfileResponse.class));
    }

    public GmailListResponse listMessages(String accessToken, int maxResults, String query) {
        return executeWithRetry(() -> restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/users/me/messages")
                            .queryParam("maxResults", maxResults);
                    if (query != null && !query.isBlank()) {
                        builder.queryParam("q", query);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GmailListResponse.class));
    }

    public GmailHistoryResponse listHistory(String accessToken, String startHistoryId) {
        return executeWithRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users/me/history")
                        .queryParam("startHistoryId", startHistoryId)
                        .queryParam("historyTypes", "messageAdded")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GmailHistoryResponse.class));
    }

    public GmailMessageResponse getMessage(String accessToken, String messageId) {
        return executeWithRetry(() -> restClient.get()
                .uri("/users/me/messages/{id}?format=full", messageId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GmailMessageResponse.class));
    }

    public GmailLabelsListResponse listLabels(String accessToken) {
        return executeWithRetry(() -> restClient.get()
                .uri("/users/me/labels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GmailLabelsListResponse.class));
    }

    public GmailLabelDto createLabel(String accessToken, String labelName) {
        Map<String, Object> body = Map.of(
                "name", labelName,
                "labelListVisibility", "labelShow",
                "messageListVisibility", "show"
        );
        return executeWithRetry(() -> restClient.post()
                .uri("/users/me/labels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GmailLabelDto.class));
    }

    public void modifyMessageLabels(String accessToken, String messageId, List<String> addLabelIds, List<String> removeLabelIds) {
        Map<String, Object> body = Map.of(
                "addLabelIds", addLabelIds != null ? addLabelIds : List.of(),
                "removeLabelIds", removeLabelIds != null ? removeLabelIds : List.of()
        );
        executeWithRetry(() -> {
            restClient.post()
                    .uri("/users/me/messages/{id}/modify", messageId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T executeWithRetry(Supplier<T> call) {
        int maxAttempts = 3;
        long backoffMs = 200L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (metricsService != null) {
                    metricsService.recordGmailApiCall(1);
                }
                return call.get();
            } catch (HttpClientErrorException e) {
                HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
                if (status == HttpStatus.TOO_MANY_REQUESTS && attempt < maxAttempts) {
                    sleepQuietly(backoffMs * attempt);
                    continue;
                }
                throw new GmailApiException("Gmail API client error (" + status.value() + "): " + e.getStatusText(), status, e);
            } catch (HttpServerErrorException e) {
                HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
                if (attempt < maxAttempts) {
                    sleepQuietly(backoffMs * attempt);
                    continue;
                }
                throw new GmailApiException("Gmail API server error (" + status.value() + "): " + e.getStatusText(), status, e);
            }
        }
        throw new GmailApiException("Gmail API call failed after retries", HttpStatus.BAD_GATEWAY);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailProfileResponse {
        private String emailAddress;
        private Long messagesTotal;
        private String historyId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailListResponse {
        private List<GmailMessageRef> messages;
        private String nextPageToken;
        private Long resultSizeEstimate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailMessageRef {
        private String id;
        private String threadId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailHistoryResponse {
        private List<GmailHistoryRecord> history;
        private String historyId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailHistoryRecord {
        private String id;
        private List<GmailMessageAdded> messagesAdded;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailMessageAdded {
        private GmailMessageRef message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailMessageResponse {
        private String id;
        private String threadId;
        private List<String> labelIds;
        private String snippet;
        private String historyId;
        private Long internalDate;
        private GmailPayload payload;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailPayload {
        private String mimeType;
        private List<GmailHeader> headers;
        private GmailBody body;
        private List<GmailPayload> parts;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailHeader {
        private String name;
        private String value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailBody {
        private String data;
        private Integer size;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailLabelsListResponse {
        private List<GmailLabelDto> labels;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GmailLabelDto {
        private String id;
        private String name;
        private String type;
    }
}
