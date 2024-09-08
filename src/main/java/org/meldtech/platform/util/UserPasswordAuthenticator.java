package org.meldtech.platform.util;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.meldtech.platform.exception.ApiErrorAdvice;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

;



@Slf4j
public class UserPasswordAuthenticator {
    private UserPasswordAuthenticator() {}
    private static final String SELECT_USER_NAME = "select u.username, u.password, u.public_id, r.name from public.user u, " +
            "public.user_role ur, public.role r where u.id = ur.user_id " +
            "and r.id = ur.role_id" +
            " and u.username = %s";
    public static Map<String, Object> authenticate(String credentials,
                                                   PasswordEncoder passwordEncoder,
                                                   HikariDataSource dataSource)  {
        String[] usernamePassword = breakUpParts(credentials);
        if(usernamePassword.length != 2) ApiErrorAdvice.handleErrorResponse("No Credentials supplied");
        JdbcUserDetailsManager userDetailsManager = new JdbcUserDetailsManager(dataSource);
        Map<String, Object> resultSet = new ConcurrentHashMap<>();

        try{
            resultSet = Objects.requireNonNull(userDetailsManager.getJdbcTemplate())
                    .queryForMap(String.format(SELECT_USER_NAME, "'"+usernamePassword[0]+"'"));
            String foundPassword = ""+resultSet.get("password");
            // Validate password
            if(passwordEncoder.matches(usernamePassword[1], foundPassword) ) return resultSet;
            ApiErrorAdvice.handleErrorResponse("Invalid Username/Password");
            return resultSet;
        }catch (Exception ex){ ApiErrorAdvice.handleErrorResponse("Wrong User Credentials"); }
        return resultSet;
    }

    private static String[] breakUpParts(String credentials) {
        try{ return MessageEncoding.base64Decoding(credentials).split(":"); }
        catch (Exception e){ return new String[]{"sample", "sample"}; }
    }

}
