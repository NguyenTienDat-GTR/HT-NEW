package phucuong.ht.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.security.UnitOperationalAccessFilter;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.security.JwtAuthenticationFilter;
import phucuong.ht.modules.auth.service.AuthUserDetailsService;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({AuthProperties.class, RateLimitProperties.class})
public class securityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UnitOperationalAccessFilter unitOperationalAccessFilter;
    private final ApiRateLimitFilter apiRateLimitFilter;
    private final ApiAuditLoggingFilter apiAuditLoggingFilter;
    private final AuthUserDetailsService authUserDetailsService;
    private final ObjectMapper objectMapper;
    @Value("${app.security.swagger-public-enabled:false}")
    private boolean swaggerPublicEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicMatchers = swaggerPublicEnabled
                ? new String[]{
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/actuator/health",
                "/swagger-auth-token.js",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs",
                "/v3/api-docs/**"
        }
                : new String[]{
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/actuator/health"
        };

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicMatchers).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(
                                    response.getWriter(),
                                    apiResponse.error("Yeu cau dang nhap", errorCode.AUTHENTICATION_FAILED.name())
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(
                                    response.getWriter(),
                                    apiResponse.error("Ban khong co quyen truy cap tai nguyen nay", errorCode.ACCESS_DENIED.name())
                            );
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(unitOperationalAccessFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, UnitOperationalAccessFilter.class)
                .addFilterAfter(apiAuditLoggingFilter, ApiRateLimitFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(authUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
