package it.zuperman.support_trainer.security.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import it.zuperman.support_trainer.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/error",
        "/api/v1/auth/**"
    };

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            CorsProperties corsProperties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/api/v1/clients/**").hasAuthority("PROFESSIONAL")
                .requestMatchers("/api/v1/invites/**").hasAuthority("PROFESSIONAL")
                .requestMatchers("/api/v1/availability/**").hasAuthority("PROFESSIONAL")
                .requestMatchers("/api/v1/professionals/**").hasAuthority("CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasAuthority("CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/client").hasAuthority("CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/professional").hasAuthority("PROFESSIONAL")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/*/confirm").hasAuthority("PROFESSIONAL")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/*/reject").hasAuthority("PROFESSIONAL")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/*/cancel").authenticated()
                .requestMatchers("/api/v1/me/**").authenticated()
                .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
