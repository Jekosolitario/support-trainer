package it.zuperman.support_trainer.client.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.zuperman.support_trainer.client.dto.response.ClientDetailResponse;
import it.zuperman.support_trainer.client.dto.response.ClientSummaryResponse;
import it.zuperman.support_trainer.client.service.ClientService;

@RestController
@RequestMapping("/api/v1/clients")
@Validated
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<ClientSummaryResponse>> getMyClients() {
        List<ClientSummaryResponse> response = clientService.getMyClients();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDetailResponse> getClientDetail(@PathVariable Long clientId) {
        ClientDetailResponse response = clientService.getClientDetail(clientId);
        return ResponseEntity.ok(response);
    }
}