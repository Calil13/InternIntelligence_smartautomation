package org.example.gmailapplication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraftRequest {

    private Long userId;
    private String userEmail;
    private String recipientEmail;
    private String subject;
    private String body;
}
