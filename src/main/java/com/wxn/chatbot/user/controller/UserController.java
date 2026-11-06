package com.wxn.chatbot.user.controller;

import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public UserDtos.Response create(@Valid @RequestBody UserDtos.Create request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public UserDtos.Response update(@PathVariable Long id, @Valid @RequestBody UserDtos.Update request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public UserDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<UserDtos.Response> list() {
        return service.list();
    }
}
