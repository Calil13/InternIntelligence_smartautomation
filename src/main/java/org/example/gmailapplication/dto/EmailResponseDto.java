package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponseDto {
    private Long id;
    private Long userId;
    private String gmailMessageId;
    private String gmailThreadId;
    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private String recipientName;
    private String subject;
    private String body;
    private EmailFolder folder;
    private EmailCategory category;
    private Set<String> labels;
    private String summary;
    private Boolean isRead;
    private Boolean isStarred;
    private LocalDateTime createdAt;
}
