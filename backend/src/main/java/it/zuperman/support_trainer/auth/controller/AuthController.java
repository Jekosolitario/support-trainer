package it.zuperman.support_trainer.auth.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.auth.dto.request.ConfirmEmailVerificationRequest;
import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterClientRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.dto.request.ResendEmailVerificationRequest;
import it.zuperman.support_trainer.auth.dto.response.CsrfTokenResponse;
import it.zuperman.support_trainer.auth.dto.response.RegistrationAcceptedResponse;
import it.zuperman.support_trainer.auth.service.AuthService;
import it.zuperman.support_trainer.invite.dto.request.ValidateInviteCodeRequest;
import it.zuperman.support_trainer.invite.dto.response.ValidateInviteCodeResponse;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.security.session.SessionLoginOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final InviteCodeService inviteCodeService;
    private final SessionLoginOrchestrator sessionLoginOrchestrator;

    public AuthController(
            AuthService authService,
            InviteCodeService inviteCodeService,
            SessionLoginOrchestrator sessionLoginOrchestrator
    ) {
        this.authService = authService;
        this.inviteCodeService = inviteCodeService;
        this.sessionLoginOrchestrator = sessionLoginOrchestrator;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrfToken(CsrfToken csrfToken) {
        String token = csrfToken.getToken();
        CsrfTokenResponse body = new CsrfTokenResponse(token, csrfToken.getHeaderName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @PostMapping("/register/professional")
    public ResponseEntity<RegistrationAcceptedResponse> registerProfessional(
            @Valid @RequestBody RegisterProfessionalRequest request
    ) {
        RegistrationAcceptedResponse response = authService.registerProfessional(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/register/client")
    public ResponseEntity<RegistrationAcceptedResponse> registerClient(
            @Valid @RequestBody RegisterClientRequest request
    ) {
        RegistrationAcceptedResponse response = authService.registerClient(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Map<String, String>> confirmEmailVerification(
            @Valid @RequestBody ConfirmEmailVerificationRequest request
    ) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(Map.of("message", "Email verificata correttamente"));
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<Map<String, String>> resendEmailVerification(
            @Valid @RequestBody ResendEmailVerificationRequest request
    ) {
        authService.resendEmailVerification(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message",
                "Se l'indirizzo è associato a un account da verificare, riceverai le istruzioni necessarie"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        sessionLoginOrchestrator.login(request, httpServletRequest, httpServletResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register/client/validate-invite")
    public ResponseEntity<ValidateInviteCodeResponse> validateInviteCode(
            @Valid @RequestBody ValidateInviteCodeRequest request
    ) {
        InviteCode inviteCode = inviteCodeService.validateInviteCode(request.getCode());
        ValidateInviteCodeResponse response = ValidateInviteCodeResponse.fromEntity(inviteCode);

        return ResponseEntity.ok(response);
    }
}
