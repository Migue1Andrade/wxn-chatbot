package com.wxn.chatbot.whatsapp.evolution;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bootstrap/whatsapp")
public class EvolutionSetupController {

    private final EvolutionSetupService service;

    public EvolutionSetupController(EvolutionSetupService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public EvolutionSetupService.Status status() {
        return service.status();
    }

    @PostMapping("/initialize")
    public EvolutionSetupService.Status initialize(HttpServletRequest request) {
        return service.initialize(request.getHeader("X-WXN-Bootstrap-Token"));
    }

    @PostMapping("/refresh")
    public EvolutionSetupService.Status refresh(HttpServletRequest request) {
        return service.refresh(request.getHeader("X-WXN-Bootstrap-Token"));
    }
}
