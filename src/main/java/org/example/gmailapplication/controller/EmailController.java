package org.example.gmailapplication.controller;

import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.services.EmailService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public String sendEmail(@RequestBody EmailSendRequest request) {
        return emailService.sendEmail(request);
    }

    @PostMapping("/draft")
    public String saveDraft(@RequestBody EmailDraftRequest request) {
        return emailService.saveDraft(request);
    }

    @GetMapping("/message/{emailId}")
    public EmailResponseDto getEmailMessage(@PathVariable Long emailId) {
        return emailService.getEmailMessage(emailId);
    }

    @GetMapping("/starred/{userId}")
    public Page<EmailsListResponseDto> getStarredEmails(@PathVariable Long userId, @ParameterObject Pageable pageable) {
        return emailService.getStarredEmails(userId, pageable);
    }

    @GetMapping("/folder/{userId}")
    public Page<EmailsListResponseDto> getEmailsByFolder(@PathVariable Long userId, @RequestParam EmailFolder folder, @ParameterObject Pageable pageable) {
        return emailService.getEmailsByFolder(userId, folder, pageable);
    }

    @PatchMapping("/{emailId}/toggle-star")
    public boolean toggleStar(@PathVariable Long emailId) {
        return emailService.toggleStar(emailId);
    }

    @GetMapping("/sidebar-summary/{userId}")
    public SidebarSummaryDto getSidebarSummary(@PathVariable Long userId) {
        return emailService.getSidebarSummary(userId);
    }
}
