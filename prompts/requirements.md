### Overview
You asked for a production‑grade, efficient “stack cache” that:
- Pushes an item when a login URL is hit
- Pops after 1 second (TTL), or
- Pops/clears when `getResolvedUrl` is called with a null `appId`, or on a 404
- Works for several (many) app clients

Below are two robust designs:
- Option A: Local, in‑process, very fast (Caffeine)
- Option B: Distributed, multi‑instance safe (Redis)

Pick A if you run a single Authorizer instance (or don’t need cross‑node coordination). Pick B if you run multiple instances or need cross‑service visibility.

---

### Option A: Local stack cache with Caffeine (production‑grade in‑JVM)
Caffeine is the de‑facto local cache library for the JVM (high performance, great concurrency, robust eviction/TTL policies).

#### Design
- One stack per `appId`
- Each stack stores entries `{url, expiresAtMillis}`
- “Push on login URL hit” → push onto the top
- “Pop after 1 second” → expire entries lazily on access, and also via a periodic cleanup task
- “Pop on `getResolvedUrl(null)` or 404” → clear that appId stack (or all stacks for `null` if that’s your intended semantics)

Caffeine can expire whole cache entries; here, we need per‑element TTL inside the value (the stack). We therefore:
- Use Caffeine for the per‑appId stack container
- Maintain per‑element TTL within the stack
- Prune expired entries on every access and via scheduled maintenance

#### Maven/Gradle dependency
```
// Gradle
implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
```

#### Stack value type
```
public final class LoginUrlEntry {
    public final String url;
    public final long expiresAtMillis;
    public LoginUrlEntry(String url, long expiresAtMillis) {
        this.url = url;
        this.expiresAtMillis = expiresAtMillis;
    }
}
```

#### The stack cache service (local)
```
import com.github.benmanes.caffeine.cache.*;
import com.google.common.util.concurrent.Striped;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class LoginUrlStackCache implements AutoCloseable {
    private final Cache<String, Deque<LoginUrlEntry>> stacks;
    private final long ttlMillis;
    private final Striped<Lock> locks = Striped.lock(64);
    private final ScheduledExecutorService janitor;

    public LoginUrlStackCache(int maxAppIds, Duration ttl) {
        this.ttlMillis = ttl.toMillis();
        this.stacks = Caffeine.newBuilder()
                .maximumSize(maxAppIds)
                .expireAfterAccess(Duration.ofMinutes(5)) // container lifetime; elements have own TTL
                .executor(Runnable::run) // use caller’s thread; or a dedicated pool
                .build();
        this.janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "login-url-stack-janitor");
            t.setDaemon(true);
            return t;
        });
        // Periodic pruning of expired elements (bounded, fast)
        janitor.scheduleAtFixedRate(this::cleanup, 200, 200, TimeUnit.MILLISECONDS);
    }

    // Push on login URL hit
    public void push(String appId, String loginUrl) {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(loginUrl, "loginUrl");
        long now = System.currentTimeMillis();
        long exp = now + ttlMillis;
        Lock lock = locks.get(appId);
        lock.lock();
        try {
            Deque<LoginUrlEntry> dq = stacks.get(appId, k -> new ArrayDeque<>());
            prune(dq, now);
            dq.push(new LoginUrlEntry(loginUrl, exp));
        } finally {
            lock.unlock();
        }
    }

    // Pop top if present and not expired; also prune expired top(s)
    public String pop(String appId) {
        if (appId == null) return null;
        long now = System.currentTimeMillis();
        Lock lock = locks.get(appId);
        lock.lock();
        try {
            Deque<LoginUrlEntry> dq = stacks.getIfPresent(appId);
            if (dq == null) return null;
            prune(dq, now);
            LoginUrlEntry e = dq.pollFirst();
            return e == null ? null : e.url;
        } finally {
            lock.unlock();
        }
    }

    // Clear on 404 or null appId condition
    public void clear(String appId) {
        if (appId == null) return;
        stacks.invalidate(appId);
    }

    private void prune(Deque<LoginUrlEntry> dq, long now) {
        while (true) {
            LoginUrlEntry top = dq.peekFirst();
            if (top == null) break;
            if (top.expiresAtMillis <= now) {
                dq.pollFirst(); // drop expired
            } else {
                break;
            }
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        stacks.asMap().forEach((appId, dq) -> {
            Lock lock = locks.get(appId);
            if (lock.tryLock()) {
                try { prune(dq, now); } finally { lock.unlock(); }
            }
        });
        stacks.cleanUp();
    }

    @Override public void close() { janitor.shutdownNow(); }
}
```

#### Integrate with your `AppClientConfigService`
You can create a small companion service for login URL stack operations:
```
@Service
public class LoginUrlStackService {
    private final LoginUrlStackCache cache = new LoginUrlStackCache(10_000, Duration.ofSeconds(1));

    public void onLoginUrlHit(String appId, String url) { cache.push(appId, url); }
    public String popForApp(String appId) { return cache.pop(appId); }
    public void clearForApp(String appId) { cache.clear(appId); }
}
```

Then in your flow:
- When you detect a “login URL hit”, call `onLoginUrlHit(appId, loginUrl)`
- When `getResolvedUrl(appId)` is called:
    - If `appId == null`: clear relevant stacks (if it means “global”, you could iterate `asMap().keySet()` but typically you’ll just no‑op or handle separately)
    - If 404: call `clearForApp(appId)`
    - Otherwise: call `popForApp(appId)` to get most recent non‑expired login URL

Example usage in your existing service:
```
@Service
public class AppClientConfigService {
    private final AppClientConfigRepository appRepository;
    private final LoginUrlStackService loginUrlStacks;

    public AppClientConfigService(AppClientConfigRepository appRepository, LoginUrlStackService loginUrlStacks) {
        this.appRepository = appRepository;
        this.loginUrlStacks = loginUrlStacks;
    }

    public String getResolvedUrl(String appId) {
        if (appId == null) {
            // Clear global or ignore; requirement says pop when null appId is passed
            return "/404"; // or your notFoundPath
        }
        // Try stack pop first
        String url = loginUrlStacks.popForApp(appId);
        if (url != null) return url;
        // Fallback to your configured logout/login URLs or notFound
        return appRepository.findByApplicationId(appId)
                .map(AppClientConfig::appLogoutUrl)
                .orElse("/404");
    }

    public void onLoginHit(String appId) {
        String loginUrl = appRepository.findByApplicationId(appId)
                .map(AppClientConfig::appLoginUrl)
                .orElse("/login");
        loginUrlStacks.onLoginUrlHit(appId, loginUrl);
    }

    public void on404(String appId) { if (appId != null) loginUrlStacks.clearForApp(appId); }
}
```

Performance notes:
- All operations are O(1) amortized
- Concurrency is managed via striped locks to avoid global contention
- Memory bounded by `maximumSize` of appId containers and the 1‑second TTL naturally limits stack depth

---

### Option B: Distributed stack with Redis (production‑grade across nodes)
If you need this to work across clustered instances, use Redis. Two pragmatic designs:

#### B1. Per‑entry TTL via sorted sets + Lua (exact per‑item TTL)
- Key: `login:stack:{appId}` as a sorted set (ZSET)
- Score: expiration epoch millis
- Member: a unique id (UUID) with the URL as a separate hash or as part of the member payload
- Push: `ZADD key expMillis member`
- Pop: Remove all expired (`ZREMRANGEBYSCORE key -inf now`), then get the most recent non‑expired (highest score) with `ZREVRANGE key 0 0 WITHSCORES`, and `ZREM` it. Do this atomically in Lua.

Lua script for atomic pop with expiry cleanup:
```
-- KEYS[1] = zset key
-- now = ARGV[1]
local key = KEYS[1]
local now = tonumber(ARGV[1])
-- prune expired
redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
-- fetch newest
local items = redis.call('ZREVRANGE', key, 0, 0)
if #items == 0 then return nil end
local member = items[1]
redis.call('ZREM', key, member)
return member
```
You can encode the URL into the member (e.g., `uuid|<url>`) or store `uuid -> url` in a Redis HASH for large URLs.

- Clear on 404 or null `appId`: `DEL login:stack:{appId}`
- TTL per entry is precise (1 second)

#### B2. Simpler: LIST per appId + EXPIRE key (coarse; resets TTL on push)
- `LPUSH login:stack:{appId} url`, then `EXPIRE ... 1`
- `LPOP` for pop
- Caveat: pushing extends the key TTL to 1s from last push (not per item). If that’s acceptable, it’s the simplest.

#### Java client
Use `Lettuce` or `Redisson` with Spring. Redisson offers a ready `RDeque` and expirable entries, but per‑item TTL still needs a pattern like ZSET.

---

### Which should you pick?
- Single JVM or locality OK: use Option A (Caffeine). Minimal dependencies, blazing fast.
- Multiple instances, must share state: Option B1 (Redis ZSET + Lua) gives true per‑item TTL. If per‑stack TTL is acceptable, B2 is simplest.

---

### How to map your exact conditions
- Push on login URL hit: call `push(appId, loginUrl)`
- Pop after 1 second: either lazily prune expired items (Caffeine approach) or use Redis score expiry
- Pop when `getResolvedUrl(null)`: either treat as “no app context” and do nothing, or if requirement is to flush state, clear the relevant stack (if you can identify it), otherwise no‑op
- Pop/clear on 404: call `clear(appId)`

If you want `getResolvedUrl(null)` to actively pop one item globally, you can keep a special global stack key (e.g., `login:stack:__global__`) and push/pop there in flows that don’t have an `appId`.

---

### Production hardening checklist
- Cap per‑appId stack depth (e.g., 64) to avoid pathological growth (reject push when full)
- Metrics: expose hits/pushes/pops/evictions via Micrometer
- Backpressure: when Redis is unavailable, degrade gracefully (return fallback URL)
- Security: do not store sensitive parameters in URL in plain text, consider hashing
- Tests: add race tests for push‑pop under high concurrency and time‑based expiry fuzzing

---

### TL;DR recommendation
- Use Caffeine in‑JVM if you don’t need cross‑node visibility. Implement a per‑app stack with per‑element TTL and lazy/scheduled pruning (code above).
- If you are clustered, use Redis with a ZSET and a small Lua script to achieve true per‑entry 1s TTL and atomic pop/cleanup. Attach clear operations to 404 and null‑`appId` cases as required.

If you share your exact deployment topology (single instance vs cluster) I can tailor a concise drop‑in class for your project and wire it into your `AppClientConfigService`. 