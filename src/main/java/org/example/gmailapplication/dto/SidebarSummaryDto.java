package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SidebarSummaryDto {
    private long unreadInboxCount;
    private long starredCount;
    private long sentCount;
    private long draftCount;
    private long trashCount;
}
