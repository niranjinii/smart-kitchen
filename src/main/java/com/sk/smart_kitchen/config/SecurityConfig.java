package com.sk.smart_kitchen.config;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) 
            
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/register", "/login").permitAll()
                .requestMatchers("/reviews/add", "/recipes/new", "/recipes/upload-image", "/pantry/**", "/profile").authenticated()
                .requestMatchers(HttpMethod.POST, "/recipes", "/recipes/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/recipes/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login") 
                .successHandler((request, response, authentication) -> {
    // 1. FIRST check the hidden input from the login form (Your Pinterest Flow)
    // This ensures if you are on the Pasta page, you STAY on the Pasta page.
    String customRedirect = request.getParameter("redirectUrl");
    if (customRedirect != null && !customRedirect.isEmpty()) {
        response.sendRedirect(customRedirect);
        return;
    }

    // 2. SECOND check if Spring Security intercepted a direct link (like /recipes/new)
    SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
    if (savedRequest != null) {
        response.sendRedirect(savedRequest.getRedirectUrl());
        return;
    }

    // 3. FALLBACK to home feed
    response.sendRedirect("/");
})
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/") 
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // This tells Spring Security to completely ignore these paths
        // They will never be blocked, regardless of login status
        return (web) -> web.ignoring().requestMatchers("/css/**", "/images/**", "/uploads/**");
    }
}

