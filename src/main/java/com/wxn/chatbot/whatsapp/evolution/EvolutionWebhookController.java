package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/evolution")
public class EvolutionWebhookController {

    private final EvolutionWebhookService service;

    public EvolutionWebhookController(EvolutionWebhookService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> receive(HttpServletRequest request, @RequestBody JsonNode body) {
        service.receive(request.getHeader("x-wxn-webhook-secret"), body);
        return ResponseEntity.ok().build();
    }
}
