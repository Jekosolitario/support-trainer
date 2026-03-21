package it.zuperman.support_trainer.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.dto.response.AuthResponse;
import it.zuperman.support_trainer.auth.service.AuthService;
import it.zuperman.support_trainer.invite.dto.request.ValidateInviteCodeRequest;
import it.zuperman.support_trainer.invite.dto.response.ValidateInviteCodeResponse;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final InviteCodeService inviteCodeService;

    public AuthController(AuthService authService, InviteCodeService inviteCodeService) {
        this.authService = authService;
        this.inviteCodeService = inviteCodeService;
    }

    @PostMapping("/register/professional")
    public ResponseEntity<AuthResponse> registerProfessional(
            @Valid @RequestBody RegisterProfessionalRequest request
    ) {
        AuthResponse response = authService.registerProfessional(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok("Email verificata correttamente");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
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
