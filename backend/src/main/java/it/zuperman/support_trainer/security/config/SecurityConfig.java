package it.zuperman.support_trainer.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import it.zuperman.support_trainer.security.session.SessionAuthenticationStateFilter;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/error",
        "/api/v1/auth/**"
    };

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final SecurityContextRepository securityContextRepository;
    private final HttpSessionCsrfTokenRepository csrfTokenRepository;
    private final SessionAuthenticationStateFilter sessionAuthenticationStateFilter;

    public SecurityConfig(
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            SecurityContextRepository securityContextRepository,
            HttpSessionCsrfTokenRepository csrfTokenRepository,
            SessionAuthenticationStateFilter sessionAuthenticationStateFilter
    ) {
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessionAuthenticationStateFilter = sessionAuthenticationStateFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository))
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                )
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
                .addFilterBefore(sessionAuthenticationStateFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
