package org.meldtech.platform.config.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class InMemoryCache<K, V> implements AutoCloseable {
    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    // In-flight loads to prevent stampede
    private final ConcurrentHashMap<K, CompletableFuture<CacheEntry<V>>> loads = new ConcurrentHashMap<>();

    private final CacheConfig config;
    private final CacheMetrics metrics = new CacheMetrics();
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-reaper");
        t.setDaemon(true);
        return t;
    });

    // A lightweight access-order deque for approximate LRU
    private final ConcurrentLinkedDeque<K> accessOrder = new ConcurrentLinkedDeque<>();

    public InMemoryCache(CacheConfig config) {
        this.config = Objects.requireNonNull(config);
        reaper.scheduleAtFixedRate(this::cleanup, config.cleanupInterval().toMillis(),
                config.cleanupInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    public CacheMetrics metrics() { return metrics; }

    public Optional<V> getIfPresent(K key) {
        long now = System.currentTimeMillis();
        CacheEntry<V> entry = store.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired(now)) {
            store.remove(key, entry);
            metrics.evictions++;
            return Optional.empty();
        }
        metrics.hits++;
        recordAccess(key);
        return Optional.ofNullable(entry.value());
    }

    public V get(K key, Supplier<V> loader) {
        return get(key, k -> loader.get(), config.defaultTtl());
    }

    public V get(K key, Function<K, V> loader, Duration ttl) {
        long now = System.currentTimeMillis();
        CacheEntry<V> cached = store.get(key);
        if (cached != null && !cached.isExpired(now)) {
            metrics.hits++;
            recordAccess(key);
            return cached.value();
        }
        metrics.misses++;
        CompletableFuture<CacheEntry<V>> future = loads.computeIfAbsent(key, k ->
                CompletableFuture.supplyAsync(() -> {
                    V value = loader.apply(k);
                    CacheEntry<V> entry = new CacheEntry<>(value, now + ttl.toMillis());
                    store.put(k, entry);
                    recordAccess(k);
                    enforceMaxSize();
                    return entry;
                })
        );
        try {
            CacheEntry<V> entry = future.get();
            return entry.value();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            loads.remove(key, future);
        }
    }

    public void put(K key, V value) { put(key, value, config.defaultTtl()); }

    public void put(K key, V value, Duration ttl) {
        long now = System.currentTimeMillis();
        store.put(key, new CacheEntry<>(value, now + ttl.toMillis()));
        recordAccess(key);
        enforceMaxSize();
    }

    public void invalidate(K key) { store.remove(key); }
    public void clear() { store.clear(); accessOrder.clear(); }
    public int size() { return store.size(); }

    private void recordAccess(K key) {
        accessOrder.offerLast(key);
        // To prevent unbounded growth, drop old duplicates sometimes
        if (accessOrder.size() > config.maxSize() * 4L) {
            trimDeque();
        }
    }

    private void enforceMaxSize() {
        while (store.size() > config.maxSize()) {
            K eldest = accessOrder.pollFirst();
            if (eldest == null) break;
            store.remove(eldest);
            metrics.evictions++;
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        // Remove expired entries lazily; avoid scanning entire map if large.
        int budget = Math.max(128, config.maxSize() / 10); // bounded work per sweep
        int checked = 0;
        for (Map.Entry<K, CacheEntry<V>> e : store.entrySet()) {
            if (checked++ > budget) break;
            CacheEntry<V> entry = e.getValue();
            if (entry.isExpired(now)) {
                if (store.remove(e.getKey(), entry)) {
                    metrics.evictions++;
                }
            }
        }
        trimDeque();
    }

    private void trimDeque() {
        // Remove keys not present anymore to keep deque reasonably small
        for (int i = 0; i < 1024; i++) { // bounded work
            K k = accessOrder.peekFirst();
            if (k == null) break;
            if (!store.containsKey(k)) {
                accessOrder.pollFirst();
            } else {
                break; // stop at the first live key
            }
        }
    }

    @Override
    public void close() {
        reaper.shutdownNow();
    }
}
