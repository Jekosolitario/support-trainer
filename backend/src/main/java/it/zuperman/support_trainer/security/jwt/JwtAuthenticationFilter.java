package it.zuperman.support_trainer.security.jwt;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import it.zuperman.support_trainer.security.service.CustomUserDetailsService;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final ApplicationTimeProvider timeProvider;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService,
            ApplicationTimeProvider timeProvider) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.timeProvider = timeProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwtToken = authHeader.substring(7);
        final String userEmail;

        try {
            userEmail = jwtService.extractUsername(jwtToken);
        } catch (ExpiredJwtException ex) {
            writeUnauthorized(response, "TOKEN_EXPIRED", "Token scaduto");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "INVALID_TOKEN", "Token non valido");
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(userEmail);

                if (!jwtService.isTokenValid(jwtToken, userDetails)) {
                    writeUnauthorized(response, "INVALID_TOKEN", "Token non valido");
                    return;
                }

                UsernamePasswordAuthenticationToken authToken
                        = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (UsernameNotFoundException ex) {
                writeUnauthorized(response, "INVALID_TOKEN", "Token non valido");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletResponse response,
            String errorCode,
            String message
    ) throws IOException {
        SecurityContextHolder.clearContext();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String json = """
            {
              "error": "UNAUTHORIZED",
              "errorCode": "%s",
              "message": "%s",
              "status": 401,
              "timestamp": "%s",
              "validationErrors": null
            }
            """.formatted(
                escapeJson(errorCode),
                escapeJson(message),
                timeProvider.nowBusinessDateTime()
        );

        response.getWriter().write(json);
        response.getWriter().flush();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
