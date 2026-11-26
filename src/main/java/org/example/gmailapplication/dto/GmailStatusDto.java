package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GmailStatusDto {
    private boolean oauthConnected;
    private String email;
    private String historyId;
    private LocalDateTime lastSyncedAt;
    private long totalSyncedEmails;
}
