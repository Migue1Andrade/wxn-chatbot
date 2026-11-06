package com.wxn.chatbot.sla.controller;

import com.wxn.chatbot.sla.service.SlaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings/sla")
public class SlaController {

    private final SlaService service;

    public SlaController(SlaService service) {
        this.service = service;
    }

    @GetMapping
    public SlaService.Policy get() {
        return service.policy();
    }

    @PutMapping
    public SlaService.Policy save(@Valid @RequestBody SlaService.Policy input) {
        return service.save(input);
    }
}
