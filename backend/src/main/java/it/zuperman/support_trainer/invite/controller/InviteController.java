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

@RestController
@RequestMapping("/api/v1/invites")
@Validated
public class InviteController {

    private final InviteCodeService inviteCodeService;

    public InviteController(InviteCodeService inviteCodeService) {
        this.inviteCodeService = inviteCodeService;
    }

    @PostMapping
    public ResponseEntity<InviteCodeResponse> createInvite(Authentication authentication) {
        InviteCode inviteCode = inviteCodeService.createInviteCode(authentication.getName());
        InviteCodeResponse response = InviteCodeResponse.fromEntity(inviteCode);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<InviteCodeResponse>> getMyInviteCodes(Authentication authentication) {
        List<InviteCodeResponse> response = inviteCodeService.getInviteCodesByProfessional(authentication.getName())
                .stream()
                .map(InviteCodeResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }
}