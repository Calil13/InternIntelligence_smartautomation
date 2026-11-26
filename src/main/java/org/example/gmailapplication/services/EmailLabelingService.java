package org.example.gmailapplication.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailLabelingService {

    // Thread-safe map of Label -> List of trigger keywords
    private final Map<String, List<String>> labelRules = new ConcurrentHashMap<>();

    public EmailLabelingService() {
        initDefaultRules();
    }

    private void initDefaultRules() {
        labelRules.put("URGENT", Arrays.asList(
                "təcili", "urgent", "asap", "vacib", "immediate", "critical",
                "priority", "mühüm", "diqqət", "emergency", "dərhal"
        ));

        labelRules.put("INVOICE", Arrays.asList(
                "faktura", "invoice", "receipt", "ödəniş", "bill", "payment",
                "qaime", "hesab-faktura", "çeki", "amount due", "mədaxil", "məxaric"
        ));

        labelRules.put("MEETING", Arrays.asList(
                "görüş", "iclas", "meeting", "call", "zoom", "google meet",
                "teams", "appointment", "təqvim", "webinar", "huddle", "standup"
        ));

        labelRules.put("PROJECT", Arrays.asList(
                "layihə", "project", "task", "deadline", "tapşırıq", "sprint",
                "milestone", "jira", "github", "pr", "commit", "deployment", "release"
        ));

        labelRules.put("SECURITY", Arrays.asList(
                "təhlükəsizlik", "security", "otp", "2fa", "verification", "parol",
                "password", "kod", "təsdiq", "login attempt", "unauthorized", "autentifikasiya"
        ));

        labelRules.put("SUPPORT", Arrays.asList(
                "dəstək", "support", "help", "kömək", "issue", "ticket",
                "problem", "xəta", "bug", "troubleshoot", "şikayət", "müraciət", "yardım"
        ));

        labelRules.put("HR", Arrays.asList(
                "müsahibə", "interview", "vakansiya", "vacancy", "cv", "resume",
                "karyera", "recruitment", "iş təklifi", "offer letter", "kadr"
        ));

        labelRules.put("FINANCE", Arrays.asList(
                "maliyyə", "finance", "bank", "kredit", "credit", "transfer",
                "balans", "balance", "faiz", "vergi", "tax", "köçürmə"
        ));
    }

    /**
     * Extracts all matching labels based on keywords present in the email subject and body.
     */
    public Set<String> extractLabels(String subject, String body) {
        String combinedText = ((subject != null ? subject : "") + " " + (body != null ? body : "")).toLowerCase();

        Set<String> matchedLabels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, List<String>> entry : labelRules.entrySet()) {
            String label = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                if (matchesKeyword(combinedText, keyword.toLowerCase())) {
                    matchedLabels.add(label.toUpperCase());
                    break;
                }
            }
        }

        log.debug("Extracted labels {} for email", matchedLabels);
        return matchedLabels;
    }

    /**
     * Checks if keyword appears in text with boundary recognition.
     */
    private boolean matchesKeyword(String text, String keyword) {
        if (text == null || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        String regex = "(?i)(^|[\\s.,!?;:\"'()[\\]{}<>/\\\\-])" + Pattern.quote(keyword) + "([\\s.,!?;:\"'()[\\]{}<>/\\\\-]|$)|" + Pattern.quote(keyword);
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS).matcher(text).find();
    }

    /**
     * Adds or updates a keyword rule for a specific label dynamically.
     */
    public void addRule(String label, List<String> keywords) {
        if (label == null || label.trim().isEmpty() || keywords == null) {
            return;
        }
        String upperLabel = label.trim().toUpperCase();
        List<String> existing = labelRules.getOrDefault(upperLabel, new ArrayList<>());
        Set<String> combined = new LinkedHashSet<>(existing);
        for (String kw : keywords) {
            if (kw != null && !kw.trim().isEmpty()) {
                combined.add(kw.trim().toLowerCase());
            }
        }
        labelRules.put(upperLabel, new ArrayList<>(combined));
        log.info("Registered/updated label rule: {} with keywords {}", upperLabel, labelRules.get(upperLabel));
    }

    /**
     * Returns all active label rules.
     */
    public Map<String, List<String>> getRules() {
        return Collections.unmodifiableMap(labelRules);
    }
}
