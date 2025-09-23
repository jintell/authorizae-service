package org.meldtech.platform.model.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomLoginUrlEntryPoint implements AuthenticationEntryPoint {
    AppClientConfigService appConfigService;

    public CustomLoginUrlEntryPoint(AppClientConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }


    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String appId = request.getParameter("appId"); // or get from header, session, etc.

        if (appId == null || appId.isBlank()) {
            appId = "default"; // Fallback
        }

        String loginUrl = appConfigService.getLoginUrl(appId);

        // Redirect to dynamically fetched login URL
        response.sendRedirect(loginUrl);
    }
}
