package org.meldtech.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.meldtech.platform.endpoint.helper.CustomLogoutHandler;
import org.meldtech.platform.model.service.AppClientConfigService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.sql.DataSource;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AppClientConfigService appConfigService) throws Exception {
        http
                .authorizeHttpRequests(authorize ->
                        authorize.requestMatchers(HttpMethod.OPTIONS).permitAll()
                                .requestMatchers("/.well-known/**").permitAll()
                                .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
//                        .logoutSuccessUrl(appLogOutUrl+"?logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String appId = request.getParameter("appId"); // or from session/header
                            String logoutUrl = appConfigService.getLogoutUrl(appId);
                            response.sendRedirect(logoutUrl + "?logout");
                        })
                        .addLogoutHandler(new CustomLogoutHandler("JSESSIONID", "USER"))
                        .permitAll());
        http.httpBasic(withDefaults());
        http
                // Form login handles the redirect to the login page from the
                // authorization server filter chain
                .formLogin(Customizer.withDefaults())
//                .formLogin(form -> form.failureUrl(appLogInUrl+"?error"));
                .formLogin(form -> form
                        .failureHandler((request, response, exception) -> {
                            String appId = request.getParameter("appId"); // or from session/header
                            String loginUrl = appConfigService.getLoginUrl(appId);
                            response.sendRedirect(loginUrl + "?error&appId=" + appId);
                        })
                        .successHandler((request, response, authentication) -> {
                            String appId = request.getParameter("appId");
                            if (appId != null && !appId.isEmpty()) {
                                // Get the success URL for the application
                                String successUrl = appConfigService.getResolvedUrl(appId);
                                if (successUrl != null && !successUrl.isEmpty()) {
                                    response.sendRedirect(successUrl);
                                    return;
                                }
                            }
                            // Default redirect if no appId or successUrl is found
                            response.sendRedirect("/");
                        })
                );
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {

        String findByUsername = "select u.username, u.password, enabled from public.user u where u.username = ?";
        String findUserRolesByUsername = "select u.username, r.name from public.role r, public.user_role ur, " +
                "public.user u where ur.role_id = r.id and u.id = ur.user_id and u.username = ?";

        JdbcUserDetailsManager userDetailsManager = new JdbcUserDetailsManager(dataSource);
        userDetailsManager.setUsersByUsernameQuery(findByUsername);
        userDetailsManager.setAuthoritiesByUsernameQuery(findUserRolesByUsername);

        return userDetailsManager;
    }

    // https://stackoverflow.com/questions/30632200/standalone-spring-oauth2-jwt-authorization-server-cors/30638914#30638914

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.applyPermitDefaultValues();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setExposedHeaders(List.of("content-length"));
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(0);
        return bean;
    }

}
