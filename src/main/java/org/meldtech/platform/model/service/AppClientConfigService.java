package org.meldtech.platform.model.service;

import org.meldtech.platform.model.AppClientConfig;
import org.meldtech.platform.model.repository.AppClientConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppClientConfigService {
    private final AppClientConfigRepository appRepository;

    @Value("${app.login.url:/login}")
    private String defaultLoginUrl;

    @Value("${app.logout.url:/logout}")
    private String defaultLogoutUrl;

    @Value("${authentication.not_found.path}")
    private String notFoundPath;

    public AppClientConfigService(AppClientConfigRepository appRepository) {
        this.appRepository = appRepository;
    }

    public String getLoginUrl(String appId) {
        return  appRepository.findByApplicationId(appId)
                .map(AppClientConfig::appLoginUrl)
                .orElse(defaultLoginUrl);
    }

    public String getLogoutUrl(String appId) {
        return appRepository.findByApplicationId(appId)
                .map(AppClientConfig::appLogoutUrl)
                .orElse(defaultLogoutUrl);
    }

    public String getResolvedUrl(String appId) {
        return appRepository.findByApplicationId(appId)
                .map(AppClientConfig::appLogoutUrl)
                .orElse(defaultLogoutUrl);
    }

}
