package com.wxn.chatbot.member.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.department.entity.Department;
import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.entity.*;
import com.wxn.chatbot.member.mapper.MemberMapper;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.user.service.UserService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private final MemberRepository repository;
    private final UserService users;

    public MemberService(MemberRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }

    public MemberDtos.Response create(MemberDtos.Create request) {
        if (request.type() == MemberType.HUMAN) {
            throw ApiException.badRequest(
                "Cadastre o atendente pelo formulário de usuários com CPF, e-mail e senha inicial"
            );
        }
        if (request.departmentIds() != null && !request.departmentIds().isEmpty()) {
            throw ApiException.badRequest("BOT não recebe setores");
        }
        if (request.userId() != null) {
            throw ApiException.badRequest("BOT não deve possuir usuário");
        }
        Long userId = null;
        Set<Department> departments = Set.of();
        Member member = new Member(userId, request.name().trim(), request.type(), ActiveStatus.ACTIVE);
        member.update(request.name(), ActiveStatus.ACTIVE, departments);
        return response(repository.save(member));
    }

    private MemberDtos.Response response(Member member) {
        var user = member.getUserId() == null ? null : users.require(member.getUserId());
        return MemberMapper.response(
            member,
            user == null ? null : user.getPhone(),
            user == null ? null : user.getAccountStatus()
        );
    }

    @Transactional(readOnly = true)
    public Member require(Long id) {
        return repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Membro não encontrado"));
    }

    public Member requireActive(Long id) {
        Member member = require(id);
        if (member.getStatus() != ActiveStatus.ACTIVE) {
            throw ApiException.conflict("Membro inativo");
        }
        if (member.getUserId() != null) {
            users.requireActive(member.getUserId());
        }
        return member;
    }

    public void requireMessageActor(Long userId, Long memberId, Long assignedMemberId) {
        var user = users.requireActive(userId);
        if (
            user.getRole() != com.wxn.chatbot.user.entity.UserRole.ADMIN &&
            (memberId == null || !java.util.Objects.equals(memberId, assignedMemberId))
        ) {
            throw new ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Atendimento transferido ou acesso revogado"
            );
        }
        if (memberId != null && !java.util.Objects.equals(requireHumanActive(memberId).getUserId(), userId)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Autoria incompatível");
        }
    }

    public Member requireHumanActive(Long id) {
        Member member = requireActive(id);
        if (member.getType() != MemberType.HUMAN) {
            throw ApiException.badRequest("Operação exige membro HUMAN ativo");
        }
        return member;
    }

    public MemberDtos.Response get(Long id) {
        return response(require(id));
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.Response> list() {
        var rows = repository.findByDeletedAtIsNullOrderById();
        var ids = rows
            .stream()
            .map(Member::getUserId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        var accounts = users.operationalAccounts(ids);
        return rows
            .stream()
            .map(member -> {
                var user = member.getUserId() == null ? null : accounts.get(member.getUserId());
                if (member.getUserId() != null && user == null) {
                    throw ApiException.notFound("Usuário vinculado ao membro não encontrado");
                }
                return MemberMapper.response(
                    member,
                    user == null ? null : user.getPhone(),
                    user == null ? null : user.getAccountStatus()
                );
            })
            .toList();
    }
}
