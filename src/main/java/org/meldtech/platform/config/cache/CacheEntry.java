package org.meldtech.platform.config.cache;

import java.time.Duration;

record CacheEntry<V>(V value, long expiresAtEpochMillis) {

    boolean isExpired(long now) {
        return now >= expiresAtEpochMillis;
    }
}

final class CacheMetrics {
    volatile long hits;
    volatile long misses;
    volatile long evictions;
}


