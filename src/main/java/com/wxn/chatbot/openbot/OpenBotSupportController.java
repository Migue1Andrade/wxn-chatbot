package com.wxn.chatbot.openbot;

import com.wxn.chatbot.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/openbot/support/replies")
@ConditionalOnProperty(prefix = "openbot.support", name = "enabled", havingValue = "true")
public class OpenBotSupportController {

    private final OpenBotSupportService service;
    private final SupportProperties properties;

    public OpenBotSupportController(OpenBotSupportService service, SupportProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    public record Reply(
        @NotBlank String requestId,
        @Size(max = 1600) String reply,
        boolean handoff,
        Boolean askResolution
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reply(
        @RequestHeader(value = "x-openbot-service-token", required = false) String token,
        @Valid @RequestBody Reply input
    ) {
        if (
            token == null ||
            token.isBlank() ||
            properties.serviceToken() == null ||
            !java.security.MessageDigest.isEqual(
                properties.serviceToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Token de retorno inválido");
        }
        service.reply(input.requestId(), input.reply(), input.handoff(), input.askResolution());
    }
}
