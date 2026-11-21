package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.gmailapplication.enums.EmailCategory;

import java.time.LocalDateTime;
import java.util.Set;

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
    private EmailCategory category;
    private Set<String> labels;
    private String summary;
    private Boolean isStarred;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
