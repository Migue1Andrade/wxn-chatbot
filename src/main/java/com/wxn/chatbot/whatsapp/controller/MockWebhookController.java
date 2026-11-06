package com.wxn.chatbot.whatsapp.controller;

import com.wxn.chatbot.whatsapp.dto.MockInboundRequest;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import com.wxn.chatbot.whatsapp.service.MockIngressService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("legacy & dev & !prod")
@RequestMapping("/api/dev/whatsapp/mock")
public class MockWebhookController {

    private final MockIngressService service;

    public MockWebhookController(MockIngressService service) {
        this.service = service;
    }

    public record Status(
        @jakarta.validation.constraints.NotNull Long channelId,
        @jakarta.validation.constraints.NotBlank String externalMessageId,
        @jakarta.validation.constraints.NotNull com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus status
    ) {}

    @PostMapping("/status")
    public void status(@Valid @RequestBody Status request) {
        service.status(
            request.channelId(),
            new com.wxn.chatbot.whatsapp.model.NormalizedStatusEvent(
                request.externalMessageId(),
                request.status(),
                null
            )
        );
    }

    public record Scenario(
        @jakarta.validation.constraints.NotNull Long channelId,
        @jakarta.validation.constraints.Pattern(regexp = "SUCCESS|FAIL|TIMEOUT") String scenario
    ) {}

    @PostMapping("/scenario")
    public void scenario(@Valid @RequestBody Scenario input) {
        service.scenario(input.channelId(), input.scenario());
    }

    @PostMapping("/api-echo")
    public void apiEcho(@Valid @RequestBody MockInboundRequest input) {
        service.apiEcho(input);
    }

    @PostMapping("/from-me")
    public void outside(@Valid @RequestBody MockInboundRequest input) {
        service.outside(input);
    }

    @PostMapping("/inbound")
    public NormalizedMessage receive(@Valid @RequestBody MockInboundRequest request) {
        return service.receive(request);
    }
}
