package org.meldtech.platform.model.service;

import org.meldtech.platform.config.cache.InMemoryCache;
import org.meldtech.platform.config.cache.CacheConfig;
import org.meldtech.platform.model.AppClientConfig;
import org.meldtech.platform.model.repository.AppClientConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AppClientConfigService {
    private final AppClientConfigRepository appRepository;
    private final InMemoryCache<String, AppClientConfig> cache =
            new InMemoryCache<>(new CacheConfig(10_000, Duration.ofMinutes(5), Duration.ofSeconds(15)));

    public AppClientConfigService(AppClientConfigRepository appRepository) {
        this.appRepository = appRepository;
    }

    public String getLoginUrl(String appId) {
        return getAppClient(appId).appLoginUrl();
    }

    public String getLogoutUrl(String appId) {
        return getAppClient(appId).appLogoutUrl();
    }

    public String getResolvedUrl(String appId) {
        return getAppClient(appId).appLogoutUrl();
    }

    public AppClientConfig getAppClient(String appId) {
        return cache.get(appId, () -> appRepository.findByApplicationId(appId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appId: " + appId)));
    }

    public void invalidate(String appId) { cache.invalidate(appId); }

}
