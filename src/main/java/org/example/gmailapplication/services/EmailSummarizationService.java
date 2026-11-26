package org.example.gmailapplication.services;

import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.dto.EmailSummaryDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailSummarizationService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // English stop words
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "up", "about", "into", "over", "after", "this",
            "that", "it", "they", "we", "you", "i", "my", "your", "our", "their", "have",
            "has", "had", "do", "does", "did", "be", "been", "being", "will", "would",
            "shall", "should", "can", "could", "may", "might", "must", "and", "or", "but",
            "so", "as", "if", "there", "here", "just", "very", "all", "any", "both", "each",
            "few", "more", "most", "other", "some", "such", "no", "not", "only", "same", "than",

            // Azerbaijani stop words
            "və", "ilə", "bu", "bir", "üçün", "o", "ki", "da", "də", "çox", "həm", "amma",
            "ancaq", "lakin", "isə", "hər", "bütün", "bəzi", "heç", "öz", "onlar", "biz",
            "siz", "mən", "sən", "kimi", "görə", "sonra", "əvvəl", "artıq", "olar", "olur",
            "oldu", "olan", "etmək", "edir", "edə", "olanlar", "belə", "beləliklə", "yaxud",
            "yəni", "haqqında", "vasitəsilə", "tərəfindən", "buna", "ona", "bundan", "ondan",
            "nə", "necə", "harada", "hansı", "daha", "baxmayaraq", "lazımdır"
    ));

    private static final List<String> ACTION_KEYWORDS = Arrays.asList(
            "xahiş", "zəhmət olmasa", "tələb", "vacib", "zəruri", "deadline", "son tarix",
            "tarixinədək", "please", "kindly", "need to", "action item", "urgently", "təsdiqləyin",
            "göndərin", "yoxlayın", "cavablandırın", "review", "confirm", "respond", "submit", "meeting"
    );

    /**
     * Generates an NLP-based extractive summary and key takeaways for an email.
     */
    public EmailSummaryDto generateSummary(Long emailId, String subject, String body) {
        String cleanSubject = (subject != null) ? subject.trim() : "";
        String cleanBody = (body != null) ? body.trim() : "";

        if (cleanBody.isEmpty() && cleanSubject.isEmpty()) {
            return EmailSummaryDto.builder()
                    .emailId(emailId)
                    .subject(cleanSubject)
                    .summary("No content available to summarize.")
                    .keyTakeaways(Collections.emptyList())
                    .originalWordCount(0)
                    .summaryWordCount(0)
                    .compressionPercentage(0.0)
                    .build();
        }

        // 1. Clean boilerplate (greetings, sign-offs)
        String sanitizedText = sanitizeEmailContent(cleanBody);
        List<String> sentences = extractSentences(sanitizedText);

        int originalWordCount = countWords(cleanBody);

        // If email is very short, use content directly
        if (sentences.size() <= 2) {
            String shortSummary = !sentences.isEmpty() ? String.join(" ", sentences) : cleanSubject;
            int summaryWordCount = countWords(shortSummary);
            return EmailSummaryDto.builder()
                    .emailId(emailId)
                    .subject(cleanSubject)
                    .summary(shortSummary)
                    .keyTakeaways(extractKeyTakeaways(sentences))
                    .originalWordCount(originalWordCount)
                    .summaryWordCount(summaryWordCount)
                    .compressionPercentage(calculateCompression(originalWordCount, summaryWordCount))
                    .build();
        }

        // 2. Tokenize and compute word frequencies (TF)
        Map<String, Integer> wordFrequencies = computeWordFrequencies(cleanSubject, sanitizedText);
        int maxFreq = wordFrequencies.values().stream().max(Integer::compareTo).orElse(1);

        // Subject keywords set for overlap calculation
        Set<String> subjectKeywords = tokenizeWords(cleanSubject).stream()
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());

        // 3. Score each sentence using NLP features
        List<ScoredSentence> scoredSentences = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            double score = scoreSentence(sentence, i, sentences.size(), wordFrequencies, maxFreq, subjectKeywords);
            scoredSentences.add(new ScoredSentence(i, sentence, score));
        }

        // 4. Select top K sentences (e.g. 2 for medium emails, 3 for long emails)
        int targetCount = sentences.size() <= 5 ? 2 : 3;
        List<ScoredSentence> topSentences = scoredSentences.stream()
                .sorted(Comparator.comparingDouble(ScoredSentence::getScore).reversed())
                .limit(targetCount)
                .sorted(Comparator.comparingInt(ScoredSentence::getIndex)) // preserve discourse order
                .collect(Collectors.toList());

        String summaryText = topSentences.stream()
                .map(ScoredSentence::getText)
                .collect(Collectors.joining(" "));

        // 5. Extract actionable key takeaways
        List<String> keyTakeaways = extractKeyTakeaways(sentences);

        int summaryWordCount = countWords(summaryText);
        double compression = calculateCompression(originalWordCount, summaryWordCount);

        log.debug("Generated NLP summary for email id {}: {} sentences -> {} sentences ({}% compression)",
                emailId, sentences.size(), topSentences.size(), compression);

        return EmailSummaryDto.builder()
                .emailId(emailId)
                .subject(cleanSubject)
                .summary(summaryText)
                .keyTakeaways(keyTakeaways)
                .originalWordCount(originalWordCount)
                .summaryWordCount(summaryWordCount)
                .compressionPercentage(compression)
                .build();
    }

    /**
     * Scores a single sentence using NLP heuristics:
     * - Term frequency salience
     * - Sentence position bonus (lead bias)
     * - Action intent bonus
     * - Subject keyword overlap
     */
    private double scoreSentence(String sentence, int index, int totalSentences,
                                  Map<String, Integer> wordFrequencies, int maxFreq,
                                  Set<String> subjectKeywords) {
        List<String> words = tokenizeWords(sentence);
        if (words.isEmpty()) {
            return 0.0;
        }

        // Content density score
        double tfScore = 0.0;
        int contentWordCount = 0;
        for (String word : words) {
            if (!STOP_WORDS.contains(word)) {
                contentWordCount++;
                int freq = wordFrequencies.getOrDefault(word, 0);
                tfScore += ((double) freq / maxFreq);
            }
        }

        double normalizedScore = contentWordCount > 0
                ? (tfScore / Math.sqrt(words.size()))
                : 0.0;

        // Position bonus: first sentence is often the thesis in emails
        if (index == 0) {
            normalizedScore += 1.5;
        } else if (index == 1) {
            normalizedScore += 0.8;
        } else if (index == totalSentences - 1) {
            normalizedScore += 0.3; // conclusion/call to action
        }

        // Action / Intent keyword boost
        String lowerSentence = sentence.toLowerCase();
        for (String actionKw : ACTION_KEYWORDS) {
            if (lowerSentence.contains(actionKw)) {
                normalizedScore += 1.2;
                break;
            }
        }

        // Subject word overlap boost
        for (String subjectKw : subjectKeywords) {
            if (words.contains(subjectKw)) {
                normalizedScore += 0.7;
            }
        }

        return normalizedScore;
    }

    /**
     * Extracts actionable key takeaways (bullet points) from sentences.
     */
    private List<String> extractKeyTakeaways(List<String> sentences) {
        List<String> takeaways = new ArrayList<>();
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            boolean isActionable = ACTION_KEYWORDS.stream().anyMatch(lower::contains)
                    || sentence.contains("?")
                    || lower.contains("deadline")
                    || lower.contains("tarix");

            if (isActionable && sentence.length() > 15) {
                takeaways.add(sentence.trim());
            }
            if (takeaways.size() >= 3) {
                break;
            }
        }
        return takeaways;
    }

    /**
     * Strips email boilerplate such as standard greetings and closing sign-offs.
     */
    private String sanitizeEmailContent(String body) {
        if (body == null) return "";

        // Remove standard salutations
        String text = body.replaceAll("(?i)^(Salam([\\s\\w,]+)?|Dear([\\s\\w,]+)?|Hörmətli([\\s\\w,]+)?|Hi([\\s\\w,]+)?|Hello([\\s\\w,]+)?)[\\n\\r]+", "");

        // Remove common email sign-offs at the end
        text = text.replaceAll("(?i)(Best regards|Kind regards|Hörmətlə|Təşəkkürlər|Thanks & regards|Sincerely|Uğurlar|Cheers)[\\s\\S]*$", "");

        return text.trim();
    }

    /**
     * Segment text into sentences using punctuation boundaries.
     */
    private List<String> extractSentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // Regex splits on sentence-ending punctuation followed by whitespace or newline
        Pattern pattern = Pattern.compile("([^.!?\\n]+[.!?]?)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            // Filter out trivial non-sentences
            if (raw.length() > 10 && countWords(raw) >= 3) {
                result.add(raw);
            }
        }

        if (result.isEmpty() && !text.trim().isEmpty()) {
            result.add(text.trim());
        }

        return result;
    }

    private Map<String, Integer> computeWordFrequencies(String subject, String text) {
        Map<String, Integer> freq = new HashMap<>();

        // Subject words get double frequency
        for (String word : tokenizeWords(subject)) {
            if (!STOP_WORDS.contains(word)) {
                freq.put(word, freq.getOrDefault(word, 0) + 2);
            }
        }

        for (String word : tokenizeWords(text)) {
            if (!STOP_WORDS.contains(word)) {
                freq.put(word, freq.getOrDefault(word, 0) + 1);
            }
        }

        return freq;
    }

    private List<String> tokenizeWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String clean = text.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ");
        String[] tokens = clean.split("\\s+");
        List<String> words = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                words.add(token);
            }
        }
        return words;
    }

    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    private double calculateCompression(int originalWords, int summaryWords) {
        if (originalWords <= 0 || summaryWords >= originalWords) return 0.0;
        double saved = ((double) (originalWords - summaryWords) / originalWords) * 100.0;
        return Math.round(saved * 10.0) / 10.0;
    }

    private static class ScoredSentence {
        private final int index;
        private final String text;
        private final double score;

        public ScoredSentence(int index, String text, double score) {
            this.index = index;
            this.text = text;
            this.score = score;
        }

        public int getIndex() {
            return index;
        }

        public String getText() {
            return text;
        }

        public double getScore() {
            return score;
        }
    }
}
