package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSummaryDto {
    private Long emailId;
    private String subject;
    private String summary;
    private List<String> keyTakeaways;
    private int originalWordCount;
    private int summaryWordCount;
    private double compressionPercentage;
}
