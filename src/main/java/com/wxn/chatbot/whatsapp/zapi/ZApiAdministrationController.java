package com.wxn.chatbot.whatsapp.zapi;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("legacy")
@RequestMapping("/api/whatsapp/channels/{id}/zapi")
public class ZApiAdministrationController {

    private final com.wxn.chatbot.auth.service.AccountSessions sessions;
    private final ZApiAdministrationService service;

    public ZApiAdministrationController(
        ZApiAdministrationService service,
        com.wxn.chatbot.auth.service.AccountSessions sessions
    ) {
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping("/connection")
    public ZApiDtos.Connection connection(@PathVariable Long id) {
        return service.connection(id);
    }

    @PostMapping("/webhooks")
    public void register(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        service.register(id, sessions.require(request.getSession(false)).id());
    }
}
