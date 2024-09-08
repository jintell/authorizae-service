package org.meldtech.platform.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.meldtech.platform.util.CustomTokenAttribute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.meldtech.platform.jwk.Jwks.generateRsa;


@Slf4j
@Configuration
public class AuthorizationServerConfig {
    @Value("${issuer.uri}")
    private String issuerUri;

    @Value("${app.login.url}")
    private String appLoginUrl;

    @Value("${app.password.strength}")
    private int PASSWORD_STRENGTH;

    private RSAKey genRsaKey;

    // Configure the client
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    // Configure with Defaults the OAuth2 Authorization Server Configurer
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http
                // Redirect to the login page when not authenticated from the
                // authorization endpoint
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint(appLoginUrl))
                );

        return http.build();
    }

    // Configure the OAuth2 Authorization Service
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    // Configure the OAuth2 Authorization Consent Service
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(PASSWORD_STRENGTH);
    }


    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(HttpServletRequest request,
                                                                   PasswordEncoder passwordEncoder,
                                                                   HikariDataSource dataSource) {
        return context -> {
            JwtClaimsSet.Builder claims = context.getClaims();
            // Customize roles claims for access_token for authorization grant types
            if(context.getAuthorizationGrantType() == AuthorizationGrantType.AUTHORIZATION_CODE) {
                List<String> roles = context.getPrincipal().getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();
                claims.claim("roles", roles)
                        .build();
            }
            else if (context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) {
                // Customize headers/claims for access_token
                String encodedCredentials = request.getHeader("Auth-ID");
                if(encodedCredentials != null) {
                    List<String> roles = new ArrayList<>();
                    Map<String, Object>  result = CustomTokenAttribute.getPublicId(encodedCredentials, dataSource, passwordEncoder);
                    claims.claim("public_id", result.getOrDefault("public_id", "")).build();
                    roles.add((String) result.getOrDefault("name", ""));
                    claims.claim("roles", roles).build();
                }else log.error("No User Credentials supplied");
            } else if (context.getTokenType().getValue().equals(OidcParameterNames.ID_TOKEN)) {
                // Customize headers/claims for id_token
                log.error("No Implementation for OPEN ID Connect yet");
            }
        };
    }

    @Bean
    public JWKSet jwkSet() {
        return new JWKSet(generateRsaKey());
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        genRsaKey = rsaKey;
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    private RSAKey generateRsaKey() {
        if(genRsaKey == null) genRsaKey = generateRsa();
        return genRsaKey;
    }

    /**
     * Configure Spring Authorization Server issuer
     * @return AuthorizationServerSettings
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings
                .builder()
                .issuer(issuerUri)
                .build();
    }

}
