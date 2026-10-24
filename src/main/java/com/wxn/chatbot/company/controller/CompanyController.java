package com.wxn.chatbot.company.controller;

import com.wxn.chatbot.company.dto.CompanyDtos;
import com.wxn.chatbot.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CompanyDtos.Response create(@Valid @RequestBody CompanyDtos.Create request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public CompanyDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }
}
