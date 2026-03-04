package com.palletpilot.wcs.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine (high-performance in-process cache).
 *
 * Per-cache TTL settings:
 * - equipmentList — 30s TTL, max 100 entries (equipment status changes
 * frequently)
 * - skuAnalytics — 5min TTL, max 500 entries (recalculated nightly)
 * - configuration — 60s TTL, max 200 entries (default)
 */
@Configuration
@EnableCaching
@SuppressWarnings("NullableProblems")
public class CacheConfig {

        @Bean
        public CacheManager cacheManager() {
                CaffeineCacheManager cacheManager = new CaffeineCacheManager();

                // Default spec for all unlisted caches
                cacheManager.setCaffeine(Caffeine.newBuilder()
                                .expireAfterWrite(60, TimeUnit.SECONDS)
                                .maximumSize(200)
                                .recordStats());

                // Register named caches with specific TTLs
                cacheManager.registerCustomCache("equipmentList",
                                Caffeine.newBuilder()
                                                .expireAfterWrite(30, TimeUnit.SECONDS)
                                                .maximumSize(100)
                                                .recordStats()
                                                .build());

                cacheManager.registerCustomCache("skuAnalytics",
                                Caffeine.newBuilder()
                                                .expireAfterWrite(5, TimeUnit.MINUTES)
                                                .maximumSize(500)
                                                .recordStats()
                                                .build());

                return cacheManager;
        }
}
