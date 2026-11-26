package org.example.gmailapplication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.services.EmailService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Tag(name = "Email Automation API", description = "Endpoints for smart email handling, categorization, keyword labeling, and NLP summarization")
public class EmailController {

    private final EmailService emailService;

    @Operation(summary = "Send email (with automatic categorization, keyword labeling, and NLP summarization)")
    @PostMapping("/send")
    public String sendEmail(@RequestBody EmailSendRequest request) {
        return emailService.sendEmail(request);
    }

    @Operation(summary = "Save email draft")
    @PostMapping("/draft")
    public String saveDraft(@RequestBody EmailDraftRequest request) {
        return emailService.saveDraft(request);
    }

    @Operation(summary = "Get single email details by ID")
    @GetMapping("/message/{emailId}")
    public EmailResponseDto getEmailMessage(@PathVariable Long emailId) {
        return emailService.getEmailMessage(emailId);
    }

    @Operation(summary = "Get starred emails for user")
    @GetMapping("/starred/{userId}")
    public Page<EmailsListResponseDto> getStarredEmails(@PathVariable Long userId, @ParameterObject Pageable pageable) {
        return emailService.getStarredEmails(userId, pageable);
    }

    @Operation(summary = "Get emails by folder (INBOX, SENT, DRAFT, TRASH)")
    @GetMapping("/folder/{userId}")
    public Page<EmailsListResponseDto> getEmailsByFolder(@PathVariable Long userId, @RequestParam EmailFolder folder, @ParameterObject Pageable pageable) {
        return emailService.getEmailsByFolder(userId, folder, pageable);
    }

    @Operation(summary = "Search emails by subject, body, or sender")
    @GetMapping("/search/{userId}")
    public Page<EmailsListResponseDto> searchEmails(
            @PathVariable Long userId,
            @RequestParam String q,
            @ParameterObject Pageable pageable) {
        return emailService.searchEmails(userId, q, pageable);
    }

    @Operation(summary = "Toggle star status on an email")
    @PatchMapping("/{emailId}/toggle-star")
    public boolean toggleStar(@PathVariable Long emailId) {
        return emailService.toggleStar(emailId);
    }

    @Operation(summary = "Toggle read/unread status on an email")
    @PatchMapping("/{emailId}/toggle-read")
    public boolean toggleRead(@PathVariable Long emailId) {
        return emailService.toggleRead(emailId);
    }

    @Operation(summary = "Move an email to Trash")
    @PatchMapping("/{emailId}/trash")
    public EmailResponseDto moveToTrash(@PathVariable Long emailId) {
        return emailService.moveToTrash(emailId);
    }

    @Operation(summary = "Get sidebar folder counts for a user")
    @GetMapping("/sidebar-summary/{userId}")
    public SidebarSummaryDto getSidebarSummary(@PathVariable Long userId) {
        return emailService.getSidebarSummary(userId);
    }

    // ==========================================
    // 1. CATEGORIZATION ENDPOINTS
    // ==========================================

    @Operation(summary = "Get emails filtered by category (PRIMARY, WORK, PROMOTIONS, SOCIAL, UPDATES, SPAM)")
    @GetMapping("/category/{userId}")
    public Page<EmailsListResponseDto> getEmailsByCategory(
            @PathVariable Long userId,
            @RequestParam EmailCategory category,
            @ParameterObject Pageable pageable) {
        return emailService.getEmailsByCategory(userId, category, pageable);
    }

    @Operation(summary = "Get overview of emails grouped by categories with counts")
    @GetMapping("/categorized/{userId}")
    public CategorizedEmailsResponseDto getCategorizedEmails(@PathVariable Long userId) {
        return emailService.getCategorizedEmails(userId);
    }

    @Operation(summary = "Automatically categorize a specific email")
    @PostMapping("/{emailId}/categorize")
    public EmailResponseDto categorizeEmail(@PathVariable Long emailId) {
        return emailService.categorizeEmail(emailId);
    }

    @Operation(summary = "Batch categorize all emails for a user")
    @PostMapping("/categorize-all/{userId}")
    public String categorizeAllEmails(@PathVariable Long userId) {
        int count = emailService.categorizeAllEmails(userId);
        return count + " emails were successfully categorized.";
    }

    // ==========================================
    // 2. KEYWORD-BASED LABELING ENDPOINTS
    // ==========================================

    @Operation(summary = "Get emails by label (e.g., URGENT, INVOICE, MEETING, PROJECT, SECURITY, SUPPORT, HR, FINANCE)")
    @GetMapping("/label/{userId}")
    public Page<EmailsListResponseDto> getEmailsByLabel(
            @PathVariable Long userId,
            @RequestParam String label,
            @ParameterObject Pageable pageable) {
        return emailService.getEmailsByLabel(userId, label, pageable);
    }

    @Operation(summary = "Get list of all labels and their counts for a user")
    @GetMapping("/labels/{userId}")
    public List<LabelCountDto> getUserLabels(@PathVariable Long userId) {
        return emailService.getUserLabels(userId);
    }

    @Operation(summary = "Automatically extract keywords and apply labels to an email")
    @PostMapping("/{emailId}/label")
    public EmailResponseDto labelEmail(@PathVariable Long emailId) {
        return emailService.labelEmail(emailId);
    }

    @Operation(summary = "Batch extract keywords and label all emails for a user")
    @PostMapping("/label-all/{userId}")
    public String labelAllEmails(@PathVariable Long userId) {
        int count = emailService.labelAllEmails(userId);
        return count + " emails were scanned and labeled.";
    }

    @Operation(summary = "Manually add a label to an email")
    @PostMapping("/{emailId}/labels")
    public EmailResponseDto addLabel(
            @PathVariable Long emailId,
            @RequestParam String label) {
        return emailService.addLabel(emailId, label);
    }

    @Operation(summary = "Remove a label from an email")
    @DeleteMapping("/{emailId}/labels")
    public EmailResponseDto removeLabel(
            @PathVariable Long emailId,
            @RequestParam String label) {
        return emailService.removeLabel(emailId, label);
    }

    @Operation(summary = "List all active keyword-to-label rules")
    @GetMapping("/rules")
    public Map<String, List<String>> getLabelRules() {
        return emailService.getLabelRules();
    }

    @Operation(summary = "Create or update a persistent keyword labeling rule")
    @PostMapping("/rules")
    public Map<String, List<String>> addLabelRule(@Valid @RequestBody LabelRuleRequest request) {
        return emailService.addLabelRule(request);
    }

    @Operation(summary = "Delete a custom keyword labeling rule")
    @DeleteMapping("/rules/{label}")
    public void deleteLabelRule(@PathVariable String label) {
        emailService.deleteLabelRule(label);
    }

    // ==========================================
    // 3. NLP SUMMARIZATION ENDPOINTS
    // ==========================================

    @Operation(summary = "Generate and save NLP summary for an email")
    @PostMapping("/{emailId}/summarize")
    public EmailSummaryDto summarizeEmail(@PathVariable Long emailId) {
        return emailService.summarizeEmail(emailId);
    }

    @Operation(summary = "Get NLP summary & key takeaways for an email")
    @GetMapping("/{emailId}/summary")
    public EmailSummaryDto getEmailSummary(@PathVariable Long emailId) {
        return emailService.getEmailSummary(emailId);
    }

    @Operation(summary = "Batch generate NLP summaries for all emails of a user")
    @PostMapping("/summarize-all/{userId}")
    public String summarizeAllEmails(@PathVariable Long userId) {
        int count = emailService.summarizeAllEmails(userId);
        return count + " emails were summarized using NLP.";
    }

    // ==========================================
    // 4. SMART AUTOMATION (ALL-IN-ONE PIPELINE)
    // ==========================================

    @Operation(summary = "Run full smart pipeline: Categorize + Keyword Label + NLP Summarize for an email")
    @PostMapping("/{emailId}/process-smart")
    public EmailResponseDto processSmart(@PathVariable Long emailId) {
        return emailService.processSmart(emailId);
    }

    @Operation(summary = "Run full smart pipeline for all emails of a user")
    @PostMapping("/process-smart-all/{userId}")
    public String processSmartAll(@PathVariable Long userId) {
        int count = emailService.processSmartAll(userId);
        return count + " emails were completely processed by smart automation.";
    }
}
