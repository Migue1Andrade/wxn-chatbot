package com.wxn.chatbot.user.controller;

import com.wxn.chatbot.user.dto.UserDtos.Create;
import com.wxn.chatbot.user.service.LocalUserSeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev & !prod")
@RequestMapping("/api/dev/users/seed")
public class LocalUserSeedController {

    private final LocalUserSeedService service;

    public LocalUserSeedController(LocalUserSeedService service) {
        this.service = service;
    }

    @PostMapping
    public List<LocalUserSeedService.Result> seed(
        @Valid @NotNull @RequestBody @Size(min = 2, max = 2) List<@NotNull @Valid Create> requests
    ) {
        return service.seed(requests);
    }
}
