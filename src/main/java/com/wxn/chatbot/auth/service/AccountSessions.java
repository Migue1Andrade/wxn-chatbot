package com.wxn.chatbot.auth.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.service.UserService;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountSessions {

    public static final String USER_ID = "wxn.userId";
    public static final String SOCKET_SESSION = "wxn.httpSession";
    private static final String CSRF = "wxn.csrf";
    private final SecureRandom random = new SecureRandom();
    private final UserService users;

    public AccountSessions(UserService users) {
        this.users = users;
    }

    public record Identity(Long id, String name, String username, UserRole role, Long memberId) {}

    public record State(Identity user, String csrfToken) {}

    public Identity require(HttpSession session) {
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Entre para continuar");
        }
        Object id;
        try {
            id = session.getAttribute(USER_ID);
        } catch (IllegalStateException expired) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão encerrada");
        }
        if (!(id instanceof Long userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Entre para continuar");
        }
        users.requireActive(userId);
        return identity(users.get(userId));
    }

    private Identity identity(UserDtos.Response user) {
        return new Identity(user.id(), user.name(), user.username(), user.role(), user.memberId());
    }

    public State state(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Identity identity = null;
        if (session.getAttribute(USER_ID) != null) {
            try {
                identity = require(session);
            } catch (ApiException unavailable) {
                session.invalidate();
                session = request.getSession(true);
            }
        }
        if (session.getAttribute(CSRF) == null) {
            session.setAttribute(CSRF, token());
        }
        return new State(identity, (String) session.getAttribute(CSRF));
    }

    public State login(HttpServletRequest request, String username, String password) {
        UserDtos.Response user = users.authenticate(username, password);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(USER_ID, user.id());
        session.setAttribute(CSRF, token());
        session.setMaxInactiveInterval(1800);
        return new State(identity(user), (String) session.getAttribute(CSRF));
    }

    public void csrf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(CSRF);
        String supplied = request.getHeader("X-CSRF-Token");
        if (
            expected == null ||
            supplied == null ||
            !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
            )
        ) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Sessão de formulário expirada; recarregue a página"
            );
        }
    }

    public void requireActor(HttpServletRequest request, Long memberId) {
        Identity user = require(request.getSession(false));
        if (!Objects.equals(user.memberId(), memberId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Operação exige o membro humano do usuário autenticado"
            );
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
