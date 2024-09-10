package org.meldtech.platform.util;

import com.zaxxer.hikari.HikariDataSource;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class CustomTokenAttribute {
    public static Map<String, Object> getPublicId(String credentials, HikariDataSource dataSource, PasswordEncoder passwordEncoder)  {
        return loadUserByUsername(credentials, dataSource, passwordEncoder);
    }

    public static Map<String, Object> getPublicId(String credentials, HikariDataSource dataSource)  {
        return loadUserByUsername(credentials, dataSource);
    }

    private static Map<String, Object> loadUserByUsername(String credentials,
                                                          HikariDataSource dataSource,
                                                          PasswordEncoder passwordEncoder)  {
        return UserPasswordAuthenticator.authenticate(credentials, passwordEncoder, dataSource);

    }

    private static Map<String, Object> loadUserByUsername(String credentials,
                                                          HikariDataSource dataSource)  {
        return UserPasswordAuthenticator.authenticate(credentials, dataSource);

    }
}
