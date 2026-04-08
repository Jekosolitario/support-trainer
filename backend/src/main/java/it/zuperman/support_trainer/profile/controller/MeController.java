package it.zuperman.support_trainer.profile.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.profile.dto.request.UpdateMyProfileRequest;
import it.zuperman.support_trainer.profile.dto.request.UpdateOperationalStatusRequest;
import it.zuperman.support_trainer.profile.dto.response.MyAccountResponse;
import it.zuperman.support_trainer.profile.dto.response.MyProfileResponse;
import it.zuperman.support_trainer.profile.service.MeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
@Validated
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/profile")
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        MyProfileResponse response = meService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account")
    public ResponseEntity<MyAccountResponse> getMyAccount() {
        MyAccountResponse response = meService.getMyAccount();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/profile")
    public ResponseEntity<MyProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        MyProfileResponse response = meService.updateMyProfile(request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/profile/operational-status")
    public ResponseEntity<MyProfileResponse> updateMyOperationalStatus(
            @Valid @RequestBody UpdateOperationalStatusRequest request
    ) {
        MyProfileResponse response = meService.updateMyOperationalStatus(request);
        return ResponseEntity.ok(response);
    }
}