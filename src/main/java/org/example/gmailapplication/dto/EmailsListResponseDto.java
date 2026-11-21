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
public class EmailsListResponseDto {
    private Long id;
    private String senderName;
    private String senderEmail;
    private String subject;
    private String body;
    private Boolean isStarred;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
