package com.wxn.chatbot.user.controller;

import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public UserDtos.Response create(
        @PathVariable Long companyId,
        @Valid @RequestBody UserDtos.Create request
    ) {
        return service.create(companyId, request);
    }

    @GetMapping
    public List<UserDtos.Response> list(@PathVariable Long companyId) {
        return service.list(companyId);
    }
}
