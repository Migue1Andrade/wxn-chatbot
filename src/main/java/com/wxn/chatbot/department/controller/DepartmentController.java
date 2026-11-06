package com.wxn.chatbot.department.controller;

import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DepartmentDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/{id}/members")
    public List<DepartmentDtos.Member> members(@PathVariable Long id) {
        return service.members(id);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public DepartmentDtos.Response create(@Valid @RequestBody DepartmentDtos.Save request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public DepartmentDtos.Response update(
        @PathVariable Long id,
        @Valid @RequestBody DepartmentDtos.Save request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
