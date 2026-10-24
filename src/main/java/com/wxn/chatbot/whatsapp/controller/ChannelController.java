package com.wxn.chatbot.whatsapp.controller;

import com.wxn.chatbot.whatsapp.dto.ChannelDtos;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/whatsapp/channels")
public class ChannelController {

    private final ChannelService service;

    public ChannelController(ChannelService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ChannelDtos.Response create(
        @PathVariable Long companyId,
        @Valid @RequestBody ChannelDtos.Create request
    ) {
        return service.create(companyId, request);
    }

    @GetMapping
    public List<ChannelDtos.Response> list(@PathVariable Long companyId) {
        return service.list(companyId);
    }
}
