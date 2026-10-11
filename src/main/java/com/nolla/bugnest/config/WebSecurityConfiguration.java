package com.nolla.bugnest.config;

import com.nolla.bugnest.dto.AuthenticationErrorResponse;
import com.nolla.bugnest.security.AccountAuthenticationProvider;
import com.nolla.bugnest.security.ClientIpResolver;
import com.nolla.bugnest.security.JsonLoginAuthenticationFilter;
import com.nolla.bugnest.security.JsonLoginFailureHandler;
import com.nolla.bugnest.security.JsonLoginSuccessHandler;
import com.nolla.bugnest.security.LoginRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {
    @Bean
    AuthenticationManager authenticationManager(AccountAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    String dummyPasswordHash(PasswordEncoder passwordEncoder) {
        return passwordEncoder.encode("bugnest dummy authentication password");
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-bugnest_session");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            JsonMapper objectMapper,
            JsonLoginSuccessHandler successHandler,
            JsonLoginFailureHandler failureHandler
    ) throws Exception {
        JsonLoginAuthenticationFilter loginFilter = new JsonLoginAuthenticationFilter(
                objectMapper, rateLimiter, clientIpResolver
        );
        loginFilter.setAuthenticationManager(authenticationManager);
        loginFilter.setSecurityContextRepository(contextRepository);
        loginFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        loginFilter.setAuthenticationSuccessHandler(successHandler);
        loginFilter.setAuthenticationFailureHandler(failureHandler);

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/issues", "/settings", "/login", "/register",
                                "/favicon.svg", "/assets/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/auth/login",
                                "/auth/sign-up",
                                "/auth/sign-up/username-preview"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/csrf", "/api/ping", "/api/add").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/accounts/*/nickname").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                objectMapper, response, 401, "UNAUTHENTICATED", "需要登录"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                objectMapper, response, 403, "FORBIDDEN", "无权执行此操作"
                        )))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(
            JsonMapper objectMapper,
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new AuthenticationErrorResponse(code, message));
    }
}
