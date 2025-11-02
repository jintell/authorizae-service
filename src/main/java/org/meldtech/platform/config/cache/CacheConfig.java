package org.meldtech.platform.config.cache;

import java.time.Duration;

public record CacheConfig(int maxSize, Duration defaultTtl, Duration cleanupInterval) {

    static CacheConfig defaults() {
        return new CacheConfig(10_000, Duration.ofMinutes(10), Duration.ofSeconds(30));
    }
}