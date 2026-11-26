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
public class AuthUserDto {
    private boolean authenticated;
    private boolean demoModeEnabled;
    private Long userId;
    private String email;
    private String fullName;
    private String avatarUrl;
    private boolean oauthConnected;
    private String gmailHistoryId;
    private LocalDateTime lastSyncedAt;
}
