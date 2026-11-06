package com.wxn.chatbot.department.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.entity.Department;
import com.wxn.chatbot.department.mapper.DepartmentMapper;
import com.wxn.chatbot.department.repository.DepartmentRepository;
import com.wxn.chatbot.member.repository.MemberRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository repository;
    private final MemberRepository members;
    private final com.wxn.chatbot.conversation.repository.ConversationRepository conversations;

    public DepartmentService(
        DepartmentRepository repository,
        MemberRepository members,
        com.wxn.chatbot.conversation.repository.ConversationRepository conversations
    ) {
        this.repository = repository;
        this.members = members;
        this.conversations = conversations;
    }

    public Department require(Long id) {
        return repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.badRequest("Setor inválido ou excluído"));
    }

    public Set<Department> selection(List<Long> ids) {
        if (
            ids == null ||
            ids.isEmpty() ||
            ids.stream().anyMatch(Objects::isNull) ||
            new HashSet<>(ids).size() != ids.size()
        ) {
            throw ApiException.badRequest("Selecione pelo menos um setor, sem IDs repetidos");
        }
        Set<Department> result = new LinkedHashSet<>();
        ids.stream()
            .sorted()
            .forEach(id -> result.add(require(id)));
        return result;
    }

    public List<DepartmentDtos.Response> list() {
        Map<Long, Long> counts = new HashMap<>();
        members.countDepartments().forEach(row -> counts.put(row.getDepartmentId(), row.getTotal()));
        return repository
            .findByDeletedAtIsNullOrderByName()
            .stream()
            .map(d -> DepartmentMapper.response(d, counts.getOrDefault(d.getId(), 0L)))
            .toList();
    }

    public List<DepartmentDtos.Member> members(Long id) {
        require(id);
        return members
            .departmentMembers(id)
            .stream()
            .map(m ->
                new DepartmentDtos.Member(m.getId(), m.getName(), m.getType().name(), m.getStatus().name())
            )
            .toList();
    }

    public DepartmentDtos.Response create(DepartmentDtos.Save request) {
        Department d = new Department(request.name(), request.color());
        d.details(request.description(), request.status());
        repository.saveAndFlush(d);
        return DepartmentMapper.response(d, 0);
    }

    public DepartmentDtos.Response update(Long id, DepartmentDtos.Save request) {
        Department d = require(id);
        if (request.expectedVersion() != null && !Objects.equals(request.expectedVersion(), d.getVersion())) {
            throw ApiException.conflict("Setor alterado por outro usuário; atualize antes de salvar");
        }
        if (
            request.status() == com.wxn.chatbot.common.ActiveStatus.INACTIVE &&
            conversations.existsByDepartmentIdAndStatusNotIn(id, List.of("CLOSED", "RESOLVED"))
        ) {
            throw ApiException.conflict("Não é possível inativar um setor com atendimentos ativos");
        }
        d.update(request.name(), request.color());
        d.details(request.description(), request.status());
        repository.flush();
        return DepartmentMapper.response(d, members.countDepartment(id));
    }

    public void delete(Long id) {
        Department d = require(id);
        if (conversations.existsByDepartmentId(id)) {
            throw ApiException.conflict("O setor possui histórico de atendimentos e deve ser inativado");
        }
        if (members.countDepartment(id) > 0) {
            throw ApiException.conflict(
                "O setor possui atendentes vinculados, inclusive inativos, e não pode ser excluído"
            );
        }
        d.delete();
    }
}
