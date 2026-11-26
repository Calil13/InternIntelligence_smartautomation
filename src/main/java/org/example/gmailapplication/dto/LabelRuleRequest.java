package org.example.gmailapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelRuleRequest {
    @NotBlank(message = "Label name is required")
    private String label;

    @NotEmpty(message = "At least one keyword is required")
    private List<String> keywords;
}
