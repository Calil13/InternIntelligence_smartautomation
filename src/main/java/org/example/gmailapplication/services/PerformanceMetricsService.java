package org.example.gmailapplication.services;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.PerformanceMetricsDto;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class PerformanceMetricsService {

    private final CacheManager cacheManager;

    private final AtomicLong gmailApiCallsMade = new AtomicLong(0);
    private final AtomicLong gmailApiCallsSaved = new AtomicLong(0);
    private final AtomicLong emailsSmartProcessed = new AtomicLong(0);
    private final AtomicLong totalSmartProcessingMs = new AtomicLong(0);

    public void recordGmailApiCall(int count) {
        gmailApiCallsMade.addAndGet(count);
    }

    public void recordGmailApiCallsSaved(int count) {
        gmailApiCallsSaved.addAndGet(count);
    }

    public void recordSmartProcessing(int emailsCount, long durationMs) {
        emailsSmartProcessed.addAndGet(emailsCount);
        totalSmartProcessingMs.addAndGet(durationMs);
    }

    public PerformanceMetricsDto getSnapshot() {
        long hits = 0;
        long misses = 0;

        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                CacheStats stats = caffeineCache.getNativeCache().stats();
                hits += stats.hitCount();
                misses += stats.missCount();
            }
        }

        long totalLookups = hits + misses;
        double hitRate = totalLookups > 0
                ? Math.round(((double) hits / totalLookups) * 1000.0) / 10.0
                : 0.0;

        long processed = emailsSmartProcessed.get();
        double avgMs = processed > 0
                ? Math.round(((double) totalSmartProcessingMs.get() / processed) * 100.0) / 100.0
                : 0.0;

        return PerformanceMetricsDto.builder()
                .cacheHits(hits)
                .cacheMisses(misses)
                .cacheHitRatePercent(hitRate)
                .gmailApiCallsMade(gmailApiCallsMade.get())
                .gmailApiCallsSaved(gmailApiCallsSaved.get())
                .emailsSmartProcessed(processed)
                .avgProcessingMs(avgMs)
                .build();
    }
}
