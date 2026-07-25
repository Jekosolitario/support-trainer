package it.zuperman.support_trainer.invite.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.invite.dto.response.InviteCodeResponse;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.security.session.AuthenticatedUserIdResolver;

@RestController
@RequestMapping("/api/v1/invites")
@Validated
public class InviteController {

    private final InviteCodeService inviteCodeService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public InviteController(
            InviteCodeService inviteCodeService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.inviteCodeService = inviteCodeService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @PostMapping
    public ResponseEntity<InviteCodeResponse> createInvite(Authentication authentication) {
        Long professionalUserId = authenticatedUserIdResolver.requireUserId(authentication);
        InviteCode inviteCode = inviteCodeService.createInviteCode(professionalUserId);
        InviteCodeResponse response = InviteCodeResponse.fromEntity(inviteCode);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<InviteCodeResponse>> getMyInviteCodes(Authentication authentication) {
        Long professionalUserId = authenticatedUserIdResolver.requireUserId(authentication);
        List<InviteCodeResponse> response = inviteCodeService.getInviteCodesByProfessional(professionalUserId)
                .stream()
                .map(InviteCodeResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }
}
