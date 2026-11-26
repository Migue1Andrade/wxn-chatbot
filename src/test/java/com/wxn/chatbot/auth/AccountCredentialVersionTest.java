package com.wxn.chatbot.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.User;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class AccountCredentialVersionTest {

    @Test
    void loginStoresGenerationFromAuthenticationAndRejectsOldSessionAfterReset() {
        var users = mock(UserService.class);
        var response = mock(UserDtos.Response.class);
        when(response.id()).thenReturn(7L);
        when(response.name()).thenReturn("Fixture");
        when(response.username()).thenReturn("fixture");
        when(response.role()).thenReturn(UserRole.ATTENDANT);
        when(users.authenticate("fixture", "password")).thenReturn(
            new UserService.Authenticated(response, 0)
        );
        User account = mock(User.class);
        when(account.getCredentialVersion()).thenReturn(0L, 1L);
        when(users.requireActive(7L)).thenReturn(account);
        when(users.get(7L)).thenReturn(response);

        var sessions = new AccountSessions(users);
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        sessions.login(request, "fixture", "password");
        var session = request.getSession(false);

        assertEquals(0L, session.getAttribute("wxn.credentialVersion"));
        assertEquals(7L, sessions.require(session).id());
        assertThrows(com.wxn.chatbot.common.ApiException.class, () -> sessions.require(session));
        assertThrows(IllegalStateException.class, session::getCreationTime);
    }

    @Test
    void legacySessionWithoutGenerationIsAcceptedOnlyAtGenerationZero() {
        var users = mock(UserService.class);
        var response = mock(UserDtos.Response.class);
        when(response.id()).thenReturn(8L);
        when(response.name()).thenReturn("Legacy");
        when(response.username()).thenReturn("legacy");
        when(response.role()).thenReturn(UserRole.ADMIN);
        User account = mock(User.class);
        when(account.getCredentialVersion()).thenReturn(0L, 1L);
        when(users.requireActive(8L)).thenReturn(account);
        when(users.get(8L)).thenReturn(response);
        var sessions = new AccountSessions(users);
        var session = new MockHttpSession();
        session.setAttribute(AccountSessions.USER_ID, 8L);

        assertEquals(8L, sessions.require(session).id());
        assertThrows(com.wxn.chatbot.common.ApiException.class, () -> sessions.require(session));
    }
}
