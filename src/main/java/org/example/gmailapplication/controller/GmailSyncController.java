package org.example.gmailapplication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.GmailStatusDto;
import org.example.gmailapplication.dto.GmailSyncResultDto;
import org.example.gmailapplication.services.GmailSyncService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Tag(name = "Gmail API Integration", description = "OAuth 2.0 Gmail incremental sync and label push-back endpoints")
public class GmailSyncController {

    private final GmailSyncService gmailSyncService;

    @Operation(summary = "Sync emails from Gmail API using incremental historyId & batch deduplication")
    @PostMapping("/sync")
    public GmailSyncResultDto syncGmail(Authentication authentication) {
        return gmailSyncService.syncForUser(authentication);
    }

    @Operation(summary = "Get Gmail OAuth 2.0 connection and sync status")
    @GetMapping("/status")
    public GmailStatusDto getStatus(Authentication authentication) {
        return gmailSyncService.getStatus(authentication);
    }

    @Operation(summary = "Push smart labels of an email back to Gmail")
    @PostMapping("/{emailId}/push-labels")
    public String pushLabels(Authentication authentication, @PathVariable Long emailId) {
        return gmailSyncService.pushLabelsToGmail(authentication, emailId);
    }
}
