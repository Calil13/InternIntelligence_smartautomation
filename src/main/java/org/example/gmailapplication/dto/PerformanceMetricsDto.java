package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetricsDto {
    private long cacheHits;
    private long cacheMisses;
    private double cacheHitRatePercent;
    private long gmailApiCallsMade;
    private long gmailApiCallsSaved;
    private long emailsSmartProcessed;
    private double avgProcessingMs;
}
