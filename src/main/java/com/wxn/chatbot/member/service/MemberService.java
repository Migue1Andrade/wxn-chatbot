package com.wxn.chatbot.member.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.company.service.CompanyService;
import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.entity.*;
import com.wxn.chatbot.member.mapper.MemberMapper;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.user.service.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private final MemberRepository repository;
    private final CompanyService companies;
    private final UserService users;

    public MemberService(MemberRepository repository, CompanyService companies, UserService users) {
        this.repository = repository;
        this.companies = companies;
        this.users = users;
    }

    public MemberDtos.Response create(Long companyId, MemberDtos.Create request) {
        companies.require(companyId);
        if (request.type() == MemberType.HUMAN && request.userId() == null) throw ApiException.badRequest(
            "Membro humano exige userId"
        );
        if (request.userId() != null) users.require(companyId, request.userId());
        return MemberMapper.response(
            repository.save(
                new Member(
                    companyId,
                    request.userId(),
                    request.name().trim(),
                    request.type(),
                    request.status() == null ? ActiveStatus.ACTIVE : request.status()
                )
            )
        );
    }

    @Transactional(readOnly = true)
    public Member require(Long companyId, Long id) {
        companies.require(companyId);
        return repository
            .findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
            .orElseThrow(() -> ApiException.notFound("Membro não encontrado na empresa"));
    }

    public Member requireActive(Long companyId, Long id) {
        Member member = require(companyId, id);
        if (member.getStatus() != ActiveStatus.ACTIVE) throw ApiException.conflict("Membro inativo");
        if (member.getUserId() != null) users.require(companyId, member.getUserId());
        return member;
    }

    public Member requireHumanActive(Long companyId, Long id) {
        Member member = requireActive(companyId, id);
        if (member.getType() != MemberType.HUMAN) throw ApiException.badRequest(
            "Operação exige membro HUMAN ativo"
        );
        return member;
    }

    public MemberDtos.Response get(Long companyId, Long id) {
        return MemberMapper.response(require(companyId, id));
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.Response> list(Long companyId) {
        companies.require(companyId);
        return repository
            .findByCompanyIdAndDeletedAtIsNullOrderById(companyId)
            .stream()
            .map(MemberMapper::response)
            .toList();
    }
}
