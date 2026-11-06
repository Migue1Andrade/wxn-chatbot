package com.wxn.chatbot.user.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.department.entity.Department;
import com.wxn.chatbot.department.service.DepartmentService;
import com.wxn.chatbot.member.entity.*;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.*;
import com.wxn.chatbot.user.mapper.UserMapper;
import com.wxn.chatbot.user.repository.UserRepository;
import com.wxn.chatbot.user.validation.Cpf;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository repository;
    private final MemberRepository members;
    private final DepartmentService departments;
    private final Validator validator;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String unknownPasswordHash = passwords.encode(UUID.randomUUID().toString());

    public UserService(
        UserRepository repository,
        MemberRepository members,
        DepartmentService departments,
        Validator validator
    ) {
        this.repository = repository;
        this.members = members;
        this.departments = departments;
        this.validator = validator;
    }

    private record Identity(String name, String username, String email, String cpf, String phone) {}

    private Identity identity(String name, String username, String email, String cpf, String phone) {
        if (
            name == null ||
            name.isBlank() ||
            username == null ||
            !username.trim().matches("[A-Za-z0-9_.-]{3,100}")
        ) {
            throw ApiException.badRequest(
                "Nome obrigatório; nome de usuário deve ter de 3 a 100 caracteres, sem espaços"
            );
        }
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (
            normalizedEmail.isEmpty() ||
            !normalizedEmail.matches("[^\\s@]+@[^\\s@]+[.][^\\s@]+") ||
            normalizedEmail.length() > 254 ||
            !validator.validateValue(EmailValue.class, "email", normalizedEmail).isEmpty()
        ) {
            throw ApiException.badRequest("E-mail inválido");
        }
        String normalizedCpf = Cpf.normalize(cpf);
        if (username.replaceAll("[^0-9]", "").equals(normalizedCpf)) {
            throw ApiException.badRequest("CPF não pode ser usado como nome de usuário");
        }
        return new Identity(
            name.trim(),
            username.trim(),
            normalizedEmail,
            normalizedCpf,
            PhoneNumbers.optional(phone)
        );
    }

    private static class EmailValue {

        @jakarta.validation.constraints.Email
        private String email;
    }

    private void unique(Identity identity, Long ownId) {
        duplicate(
            repository.findByUsernameIgnoreCase(identity.username()),
            ownId,
            "Nome de usuário já cadastrado"
        );
        duplicate(repository.findByEmailIgnoreCase(identity.email()), ownId, "E-mail já cadastrado");
        duplicate(repository.findByCpf(identity.cpf()), ownId, "CPF já cadastrado");
    }

    private void duplicate(Optional<User> found, Long ownId, String message) {
        if (found.isPresent() && !Objects.equals(found.get().getId(), ownId)) {
            throw ApiException.conflict(message);
        }
    }

    private String password(String value, String confirmation, String cpf) {
        if (
            value == null ||
            value.length() < 8 ||
            value.getBytes(StandardCharsets.UTF_8).length > 72 ||
            !value.equals(confirmation)
        ) {
            throw ApiException.badRequest(
                "Senha deve ter pelo menos 8 caracteres, até 72 bytes, e coincidir com a confirmação"
            );
        }
        if (value.replaceAll("[.\\-\\s]", "").equals(cpf)) {
            throw ApiException.badRequest("CPF não pode ser usado como senha");
        }
        return passwords.encode(value);
    }

    private Set<Department> selection(UserRole role, List<Long> ids) {
        if (role == null) {
            throw ApiException.badRequest("Perfil obrigatório");
        }
        if (role == UserRole.ADMIN && (ids == null || ids.isEmpty())) {
            return Set.of();
        }
        return departments.selection(ids);
    }

    private void validate(Object request) {
        if (!validator.validate(request).isEmpty()) {
            throw ApiException.badRequest(
                "Verifique os campos obrigatórios e os limites de tamanho do cadastro"
            );
        }
    }

    public UserDtos.Response create(UserDtos.Create request) {
        validate(request);
        repository.lockAdministrators();
        Identity identity = identity(
            request.name(),
            request.username(),
            request.email(),
            request.cpf(),
            request.phone()
        );
        unique(identity, null);
        Set<Department> selected = selection(request.role(), request.departmentIds());
        String hash = password(request.password(), request.passwordConfirmation(), identity.cpf());
        if (request.accountStatus() == null) {
            throw ApiException.badRequest("Status obrigatório");
        }
        User user = new User(identity.name(), identity.phone());
        user.updateAccount(
            identity.name(),
            identity.username(),
            identity.email(),
            identity.cpf(),
            identity.phone(),
            request.role(),
            request.accountStatus()
        );
        user.initializePassword(hash);
        repository.saveAndFlush(user);
        return synchronize(user, selected);
    }

    public UserDtos.Response update(Long id, UserDtos.Update request) {
        validate(request);
        List<User> admins = repository.lockAdministrators();
        User user = repository
            .lockById(id)
            .orElseThrow(() -> ApiException.notFound("Usuário não encontrado"));
        if (
            user.getRole() == UserRole.ADMIN &&
            user.getAccountStatus() == ActiveStatus.ACTIVE &&
            (request.role() != UserRole.ADMIN || request.accountStatus() != ActiveStatus.ACTIVE) &&
            admins
                .stream()
                .filter(a -> a.getAccountStatus() == ActiveStatus.ACTIVE && a.getDeletedAt() == null)
                .count() <= 1
        ) {
            throw ApiException.conflict("O último ADMIN ativo não pode ser inativado ou rebaixado");
        }
        Identity identity = identity(
            request.name(),
            request.username(),
            request.email(),
            request.cpf(),
            request.phone()
        );
        unique(identity, id);
        Set<Department> selected = selection(request.role(), request.departmentIds());
        if (request.accountStatus() == null) {
            throw ApiException.badRequest("Status obrigatório");
        }
        if (user.getPasswordHash() == null) {
            user.initializePassword(
                password(request.initialPassword(), request.passwordConfirmation(), identity.cpf())
            );
        } else if (request.initialPassword() != null) {
            throw ApiException.badRequest("A edição cadastral não altera senhas existentes");
        }
        user.updateAccount(
            identity.name(),
            identity.username(),
            identity.email(),
            identity.cpf(),
            identity.phone(),
            request.role(),
            request.accountStatus()
        );
        repository.flush();
        return synchronize(user, selected);
    }

    private UserDtos.Response synchronize(User user, Set<Department> selected) {
        Member member = member(user.getId());
        if (member == null && (user.getRole() == UserRole.ADMIN || !selected.isEmpty())) {
            member = new Member(user.getId(), user.getName(), MemberType.HUMAN, user.getAccountStatus());
        }
        if (member != null) {
            member.update(
                user.getName(),
                selected.isEmpty() && user.getRole() != UserRole.ADMIN
                    ? ActiveStatus.INACTIVE
                    : user.getAccountStatus(),
                selected
            );
            members.saveAndFlush(member);
        }
        return UserMapper.response(user, member);
    }

    private Member member(Long userId) {
        var rows = members.findByUserIdAndTypeAndDeletedAtIsNullOrderById(userId, MemberType.HUMAN);
        if (rows.size() > 1) {
            throw ApiException.conflict(
                "Usuário possui múltiplos membros HUMAN; revise os vínculos existentes"
            );
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public User requireActive(Long id) {
        User user = require(id);
        if (user.getAccountStatus() != ActiveStatus.ACTIVE || user.isRegistrationIncomplete()) {
            throw new ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Conta inativa ou cadastro incompleto"
            );
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserDtos.Response get(Long id) {
        return UserMapper.response(require(id), member(id));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.Response> list() {
        java.util.Map<Long, Member> operational = new java.util.HashMap<>();
        for (Member member : members.directoryHumans()) {
            if (operational.putIfAbsent(member.getUserId(), member) != null) {
                throw ApiException.conflict(
                    "Usuário possui múltiplos membros HUMAN; revise os vínculos existentes"
                );
            }
        }
        return repository
            .findByDeletedAtIsNullOrderById()
            .stream()
            .map(u -> UserMapper.response(u, operational.get(u.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, User> operationalAccounts(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return repository
            .findByIdInAndDeletedAtIsNull(ids)
            .stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, java.util.function.Function.identity()));
    }

    @Transactional(readOnly = true)
    public UserDtos.Response authenticate(String username, String password) {
        User user =
            username == null ? null : repository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        String hash =
            user == null || user.getPasswordHash() == null ? unknownPasswordHash : user.getPasswordHash();
        boolean matches =
            password != null &&
            password.getBytes(StandardCharsets.UTF_8).length <= 72 &&
            passwords.matches(password, hash);
        if (
            !matches ||
            user == null ||
            user.getDeletedAt() != null ||
            user.getAccountStatus() != ActiveStatus.ACTIVE ||
            user.isRegistrationIncomplete()
        ) {
            throw new ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Usuário ou senha inválidos, ou conta indisponível"
            );
        }
        return UserMapper.response(user, member(user.getId()));
    }
}
