package org.meldtech.platform.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class ApiErrorAdvice {
    private static final String DEFAULT_URI = "/oauth2/token";
    private static final String DEFAULT_ERROR_CODE = "400";

    private ApiErrorAdvice() {}

    public static void handleErrorResponse(String message){
        throw new OAuth2AuthenticationException(new OAuth2Error(DEFAULT_ERROR_CODE, message, DEFAULT_URI));
    }

}
