package com.wxn.chatbot.whatsapp.controller;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.service.TwilioIngressService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/whatsapp/twilio/{channelKey}")
public class TwilioWebhookController {

    private final TwilioIngressService service;

    public TwilioWebhookController(TwilioIngressService service) {
        this.service = service;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.APPLICATION_XML_VALUE
    )
    public String inbound(
        @PathVariable String channelKey,
        @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
        @RequestBody MultiValueMap<String, String> form,
        HttpServletRequest request
    ) {
        service.receive(channelKey, signature, parameters(form, request), false);
        return "<Response/>";
    }

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void status(
        @PathVariable String channelKey,
        @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
        @RequestBody MultiValueMap<String, String> form,
        HttpServletRequest request
    ) {
        service.receive(channelKey, signature, parameters(form, request), true);
    }

    private Map<String, String> parameters(MultiValueMap<String, String> form, HttpServletRequest request) {
        // This first configuration uses exact URLs without query strings or repeated form keys.
        if (
            request.getQueryString() != null ||
            form
                .values()
                .stream()
                .anyMatch(v -> v.size() != 1)
        ) throw ApiException.badRequest("Formato de webhook não suportado");
        return form.toSingleValueMap();
    }
}
