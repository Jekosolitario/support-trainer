package it.zuperman.support_trainer.security.jwt;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
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
import it.zuperman.support_trainer.common.response.ErrorResponseWriter;
import it.zuperman.support_trainer.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final ErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService,
            ErrorResponseWriter errorResponseWriter
    ) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authHeader.substring(7);
        String userEmail;

        try {
            userEmail = jwtService.extractUsername(jwtToken);
        } catch (ExpiredJwtException ex) {
            writeUnauthorized(request, response, "TOKEN_EXPIRED", "Token scaduto");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(request, response, "INVALID_TOKEN", "Token non valido");
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(userEmail);

                if (!jwtService.isTokenValid(jwtToken, userDetails)) {
                    writeUnauthorized(request, response, "INVALID_TOKEN", "Token non valido");
                    return;
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (UsernameNotFoundException ex) {
                writeUnauthorized(request, response, "INVALID_TOKEN", "Token non valido");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String code,
            String message
    ) {
        SecurityContextHolder.clearContext();
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, code, message);
    }
}
