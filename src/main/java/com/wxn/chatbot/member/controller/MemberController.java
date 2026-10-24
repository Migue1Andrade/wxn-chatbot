package com.wxn.chatbot.member.controller;

import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public MemberDtos.Response create(
        @PathVariable Long companyId,
        @Valid @RequestBody MemberDtos.Create request
    ) {
        return service.create(companyId, request);
    }

    @GetMapping
    public List<MemberDtos.Response> list(@PathVariable Long companyId) {
        return service.list(companyId);
    }

    @GetMapping("/{memberId}")
    public MemberDtos.Response get(@PathVariable Long companyId, @PathVariable Long memberId) {
        return service.get(companyId, memberId);
    }
}
