package org.example.gmailapplication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.PerformanceMetricsDto;
import org.example.gmailapplication.services.PerformanceMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Performance & Optimization", description = "Cache hit rate, API call savings, and NLP processing metrics")
public class MetricsController {

    private final PerformanceMetricsService performanceMetricsService;

    @Operation(summary = "Get live Caffeine cache and Gmail API optimization metrics")
    @GetMapping("/performance")
    public PerformanceMetricsDto getPerformanceMetrics() {
        return performanceMetricsService.getSnapshot();
    }
}
