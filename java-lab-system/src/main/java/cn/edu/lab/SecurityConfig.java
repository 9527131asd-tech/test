package cn.edu.lab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(JdbcTemplate db) {
        return username -> db.query("SELECT user_id,password_hash,user_type,account_status FROM users WHERE user_id=?", rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("账号不存在");
            if (!"正常".equals(rs.getString("account_status"))) throw new UsernameNotFoundException("账号已冻结");
            String role = switch (rs.getString("user_type")) {
                case "ADMIN" -> "ADMIN"; case "LAB_MANAGER" -> "LAB_MANAGER"; case "MAINTAINER" -> "MAINTAINER";
                case "TEACHER" -> "TEACHER"; default -> "STUDENT";
            };
            return User.withUsername(rs.getString("user_id")).password(rs.getString("password_hash")).roles(role).build();
        }, username);
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/style.css", "/app.js", "/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/labs", "/api/equipment", "/api/consumables").permitAll()
                    .requestMatchers("/files/**").authenticated()
                    .anyRequest().authenticated())
                .httpBasic(b -> {}).build();
    }
}
