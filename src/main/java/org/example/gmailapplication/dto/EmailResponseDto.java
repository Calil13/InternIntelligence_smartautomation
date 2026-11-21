package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.gmailapplication.enums.EmailFolder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponseDto {
    private Long id;
    private Long userId;
    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private String recipientName;
    private String subject;
    private String body;
    private EmailFolder folder;
    private Boolean isRead;
    private Boolean isStarred;
    private LocalDateTime createdAt;
}