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
public class GmailSyncResultDto {
    private int fetchedCount;
    private int skippedDuplicateCount;
    private boolean incrementalUsed;
    private int apiCallsMade;
    private int apiCallsSaved;
    private long durationMs;
    private String historyId;
    private LocalDateTime syncedAt;
}
