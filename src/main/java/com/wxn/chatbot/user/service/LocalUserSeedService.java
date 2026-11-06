package com.wxn.chatbot.user.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.service.DepartmentService;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.*;
import com.wxn.chatbot.user.repository.UserRepository;
import com.wxn.chatbot.user.validation.Cpf;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LocalUserSeedService {

    private final UserRepository repository;
    private final UserService users;
    private final DepartmentService departments;

    public LocalUserSeedService(UserRepository repository, UserService users, DepartmentService departments) {
        this.repository = repository;
        this.users = users;
        this.departments = departments;
    }

    public record Result(
        String username,
        String outcome,
        Long id,
        com.wxn.chatbot.common.ActiveStatus accountStatus
    ) {}

    public List<Result> seed(List<UserDtos.Create> requests) {
        if (
            requests == null ||
            requests.size() != 2 ||
            requests.stream().anyMatch(Objects::isNull) ||
            requests
                .stream()
                .filter(r -> r.role() == UserRole.ADMIN)
                .count() != 1 ||
            requests
                .stream()
                .filter(r -> r.role() == UserRole.ATTENDANT)
                .count() != 1
        ) {
            throw ApiException.badRequest("Seed exige exatamente um ADMIN e um ATTENDANT");
        }
        repository.lockAdministrators();
        Map<String, User> existing = new HashMap<>();
        Set<String> usernames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> cpfs = new HashSet<>();
        for (var request : requests) {
            String cpf = Cpf.normalize(request.cpf());
            String username = request.username().trim();
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!usernames.add(username.toLowerCase(Locale.ROOT)) || !emails.add(email) || !cpfs.add(cpf)) {
                throw ApiException.conflict("Conflito entre as identidades informadas no seed");
            }
            var byUsername = repository.findByUsernameIgnoreCase(username);
            var byEmail = repository.findByEmailIgnoreCase(email);
            var byCpf = repository.findByCpf(cpf);
            if (byUsername.isPresent() || byEmail.isPresent() || byCpf.isPresent()) {
                if (
                    byUsername.isEmpty() ||
                    byEmail.isEmpty() ||
                    byCpf.isEmpty() ||
                    !byUsername.get().getId().equals(byEmail.get().getId()) ||
                    !byUsername.get().getId().equals(byCpf.get().getId()) ||
                    byUsername.get().getRole() != request.role() ||
                    byUsername.get().getDeletedAt() != null
                ) {
                    throw ApiException.conflict(
                        "Seed interrompido: conflito de username, e-mail ou CPF para " +
                            username +
                            "; nenhuma conta alterada"
                    );
                }
                existing.put(username, byUsername.get());
            }
        }
        List<Result> result = new ArrayList<>();
        for (var request : requests) {
            User user = existing.get(request.username().trim());
            if (user != null) {
                result.add(
                    new Result(
                        request.username(),
                        "EXISTING_UNCHANGED",
                        user.getId(),
                        user.getAccountStatus()
                    )
                );
                continue;
            }
            List<Long> ids = request.departmentIds();
            if (request.role() == UserRole.ATTENDANT && (ids == null || ids.isEmpty())) {
                var available = departments.list();
                long id = available.isEmpty()
                    ? departments.create(new DepartmentDtos.Save("Atendimento", "#2563EB")).id()
                    : available.get(0).id();
                ids = List.of(id);
            }
            var created = users.create(
                new UserDtos.Create(
                    request.name(),
                    request.username(),
                    request.email(),
                    request.cpf(),
                    null,
                    request.password(),
                    request.passwordConfirmation(),
                    request.role(),
                    request.accountStatus(),
                    ids
                )
            );
            result.add(new Result(request.username(), "CREATED", created.id(), created.accountStatus()));
        }
        return result;
    }
}
