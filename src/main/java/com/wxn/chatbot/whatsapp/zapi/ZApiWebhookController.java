package com.wxn.chatbot.whatsapp.zapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("legacy")
@RequestMapping("/api/webhooks/zapi")
public class ZApiWebhookController {

    private final ZApiWebhookService service;

    public ZApiWebhookController(ZApiWebhookService service) {
        this.service = service;
    }

    @PostMapping({ "/received", "/received-delivery" })
    public void received(@RequestParam(required = false) String secret, @RequestBody JsonNode body) {
        service.receive(secret, "received", body);
    }

    @PostMapping("/message-status")
    public void status(@RequestParam(required = false) String secret, @RequestBody JsonNode body) {
        service.receive(secret, "message-status", body);
    }

    @PostMapping({ "/sent", "/delivery" })
    public void sent(@RequestParam(required = false) String secret, @RequestBody JsonNode body) {
        service.receive(secret, "sent", body);
    }

    @PostMapping("/connected")
    public void connected(@RequestParam(required = false) String secret, @RequestBody JsonNode body) {
        service.receive(secret, "connected", body);
    }

    @PostMapping("/disconnected")
    public void disconnected(@RequestParam(required = false) String secret, @RequestBody JsonNode body) {
        service.receive(secret, "disconnected", body);
    }
}
