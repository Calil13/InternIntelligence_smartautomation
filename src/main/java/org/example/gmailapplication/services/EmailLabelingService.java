package org.example.gmailapplication.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.entity.LabelRule;
import org.example.gmailapplication.repository.LabelRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailLabelingService {

    private final LabelRuleRepository labelRuleRepository;
    private final Map<String, List<String>> labelRules = new ConcurrentHashMap<>();
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    public EmailLabelingService() {
        this(null);
    }

    @Autowired
    public EmailLabelingService(@Autowired(required = false) LabelRuleRepository labelRuleRepository) {
        this.labelRuleRepository = labelRuleRepository;
        initDefaultRules();
    }

    @PostConstruct
    public void loadPersistedRules() {
        if (labelRuleRepository == null) {
            return;
        }
        for (LabelRule persisted : labelRuleRepository.findAll()) {
            mergeRuleInMemory(persisted.getLabelName(), persisted.getKeywords());
        }
    }

    private void initDefaultRules() {
        mergeRuleInMemory("URGENT", Arrays.asList(
                "təcili", "urgent", "asap", "vacib", "immediate", "critical",
                "priority", "mühüm", "diqqət", "emergency", "dərhal"
        ));
        mergeRuleInMemory("INVOICE", Arrays.asList(
                "faktura", "invoice", "receipt", "ödəniş", "bill", "payment",
                "qaime", "hesab-faktura", "çeki", "amount due", "mədaxil", "məxaric"
        ));
        mergeRuleInMemory("MEETING", Arrays.asList(
                "görüş", "iclas", "meeting", "call", "zoom", "google meet",
                "teams", "appointment", "təqvim", "webinar", "huddle", "standup"
        ));
        mergeRuleInMemory("PROJECT", Arrays.asList(
                "layihə", "project", "task", "deadline", "tapşırıq", "sprint",
                "milestone", "jira", "github", "pr", "commit", "deployment", "release"
        ));
        mergeRuleInMemory("SECURITY", Arrays.asList(
                "təhlükəsizlik", "security", "otp", "2fa", "verification", "parol",
                "password", "kod", "təsdiq", "login attempt", "unauthorized", "autentifikasiya"
        ));
        mergeRuleInMemory("SUPPORT", Arrays.asList(
                "dəstək", "support", "help", "kömək", "issue", "ticket",
                "problem", "xəta", "bug", "troubleshoot", "şikayət", "müraciət", "yardım"
        ));
        mergeRuleInMemory("HR", Arrays.asList(
                "müsahibə", "interview", "vakansiya", "vacancy", "cv", "resume",
                "karyera", "recruitment", "iş təklifi", "offer letter", "kadr"
        ));
        mergeRuleInMemory("FINANCE", Arrays.asList(
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
            for (String keyword : entry.getValue()) {
                if (matchesKeyword(combinedText, keyword)) {
                    matchedLabels.add(label.toUpperCase());
                    break;
                }
            }
        }

        log.debug("Extracted labels {} for email", matchedLabels);
        return matchedLabels;
    }

    private boolean matchesKeyword(String text, String keyword) {
        if (text == null || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        Pattern pattern = compiledPatterns.computeIfAbsent(keyword.toLowerCase(), EmailLabelingService::compileKeywordPattern);
        return pattern.matcher(text).find();
    }

    private static Pattern compileKeywordPattern(String keyword) {
        return Pattern.compile("(?i)(^|[^\\p{L}\\p{Nd}])" + Pattern.quote(keyword) + "([^\\p{L}\\p{Nd}]|$)",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    /**
     * Adds or updates a keyword rule for a specific label dynamically and persists it if DB is present.
     */
    @Transactional
    public void addRule(String label, List<String> keywords) {
        if (label == null || label.trim().isEmpty() || keywords == null) {
            return;
        }
        String upperLabel = label.trim().toUpperCase();
        mergeRuleInMemory(upperLabel, keywords);

        if (labelRuleRepository != null) {
            LabelRule entity = labelRuleRepository.findByLabelNameIgnoreCase(upperLabel)
                    .orElseGet(() -> LabelRule.builder().labelName(upperLabel).build());
            entity.setKeywords(new ArrayList<>(labelRules.get(upperLabel)));
            labelRuleRepository.save(entity);
        }

        log.info("Registered/updated label rule: {} with keywords {}", upperLabel, labelRules.get(upperLabel));
    }

    @Transactional
    public void deleteRule(String label) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        String upperLabel = label.trim().toUpperCase();
        labelRules.remove(upperLabel);
        if (labelRuleRepository != null) {
            labelRuleRepository.deleteByLabelNameIgnoreCase(upperLabel);
        }
    }

    private void mergeRuleInMemory(String label, List<String> keywords) {
        if (label == null || label.trim().isEmpty() || keywords == null) {
            return;
        }
        String upperLabel = label.trim().toUpperCase();
        List<String> existing = labelRules.getOrDefault(upperLabel, new ArrayList<>());
        Set<String> combined = new LinkedHashSet<>(existing);
        for (String kw : keywords) {
            if (kw != null && !kw.trim().isEmpty()) {
                String clean = kw.trim().toLowerCase();
                combined.add(clean);
                compiledPatterns.computeIfAbsent(clean, EmailLabelingService::compileKeywordPattern);
            }
        }
        labelRules.put(upperLabel, new ArrayList<>(combined));
    }

    public Map<String, List<String>> getRules() {
        return Collections.unmodifiableMap(new TreeMap<>(labelRules));
    }
}
