package com.wxn.chatbot.whatsapp.controller;

import com.wxn.chatbot.whatsapp.dto.MockInboundRequest;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import com.wxn.chatbot.whatsapp.service.MockIngressService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev & !prod")
@RequestMapping("/api/dev/whatsapp/mock")
public class MockWebhookController {

    private final MockIngressService service;

    public MockWebhookController(MockIngressService service) {
        this.service = service;
    }

    @PostMapping("/inbound")
    public NormalizedMessage receive(@Valid @RequestBody MockInboundRequest request) {
        return service.receive(request);
    }
}
