package it.zuperman.support_trainer.professional.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.professional.dto.response.ProfessionalDetailResponse;
import it.zuperman.support_trainer.professional.dto.response.ProfessionalSummaryResponse;
import it.zuperman.support_trainer.professional.service.ProfessionalService;

@RestController
@RequestMapping("/api/v1/professionals")
public class ProfessionalController {

    private final ProfessionalService professionalService;

    public ProfessionalController(ProfessionalService professionalService) {
        this.professionalService = professionalService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<ProfessionalSummaryResponse>> getMyProfessionals() {
        List<ProfessionalSummaryResponse> response = professionalService.getMyProfessionals();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{professionalId}")
    public ResponseEntity<ProfessionalDetailResponse> getProfessionalDetail(
            @PathVariable Long professionalId
    ) {
        ProfessionalDetailResponse response = professionalService.getProfessionalDetail(professionalId);
        return ResponseEntity.ok(response);
    }
}