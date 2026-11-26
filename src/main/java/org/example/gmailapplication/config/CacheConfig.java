package org.example.gmailapplication.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_SIDEBAR_SUMMARY = "sidebarSummary";
    public static final String CACHE_CATEGORIZED_EMAILS = "categorizedEmails";
    public static final String CACHE_USER_LABELS = "userLabels";
    public static final String CACHE_EMAIL_SUMMARY = "emailSummary";
    public static final String CACHE_LABEL_RULES = "labelRules";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CACHE_SIDEBAR_SUMMARY,
                CACHE_CATEGORIZED_EMAILS,
                CACHE_USER_LABELS,
                CACHE_EMAIL_SUMMARY,
                CACHE_LABEL_RULES
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(2000)
                .recordStats());
        return cacheManager;
    }
}
