package com.jobportal.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Cache configuration for the application
 * Uses in-memory caching with Spring's ConcurrentMapCache
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("jobs"),
            new ConcurrentMapCache("companies"),
            new ConcurrentMapCache("candidates"),
            new ConcurrentMapCache("users")
        ));
        return cacheManager;
    }
}
