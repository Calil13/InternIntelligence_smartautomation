package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.gmailapplication.enums.EmailCategory;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorizedEmailsResponseDto {
    private Long userId;
    private Map<EmailCategory, Long> categoryCounts;
    private Map<EmailCategory, List<EmailsListResponseDto>> categorizedEmails;
}
