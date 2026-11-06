package com.wxn.chatbot.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;

class DevelopmentApiGuardTest {

    private static final String KEY = "unit-test-development-key-32-chars-minimum";

    @Test
    void requiresDevProfileAndKeyAndBlocksProdEvenWithDev() throws Exception {
        var env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThat(request(env, KEY, KEY)).isEqualTo(200);
        assertThat(request(env, KEY, "wrong")).isEqualTo(401);
        assertThat(request(env, "", "")).isEqualTo(404);
        env.setActiveProfiles("prod", "dev");
        assertThat(request(env, KEY, KEY)).isEqualTo(404);
        env.setActiveProfiles("default");
        assertThat(request(env, KEY, KEY)).isEqualTo(404);
    }

    private int request(MockEnvironment env, String configured, String supplied) throws Exception {
        var guard = new DevelopmentApiGuard(new DevelopmentAccess(env, configured));
        var request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setServletPath("/api/conversations");
        request.addHeader("X-Dev-Api-Key", supplied);
        var response = new MockHttpServletResponse();
        guard.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
