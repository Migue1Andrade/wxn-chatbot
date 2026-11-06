package com.wxn.chatbot.quickreply.service;

import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.quickreply.dto.QuickReplyDtos.*;
import com.wxn.chatbot.quickreply.entity.QuickReply;
import com.wxn.chatbot.quickreply.repository.QuickReplyRepository;
import com.wxn.chatbot.user.entity.UserRole;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuickReplyService {

    private static final Set<String> RESERVED = Set.of(
        "nome",
        "primeiro_nome",
        "email",
        "telefone",
        "atendente",
        "atendente_email",
        "empresa",
        "endereco",
        "setor",
        "data",
        "hora",
        "protocolo"
    );
    private static final Pattern VARIABLE = Pattern.compile("@@([a-zA-Z0-9_]+)");
    private final QuickReplyRepository repository;
    private final ConversationAuthorization authorization;

    public QuickReplyService(QuickReplyRepository repository, ConversationAuthorization authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    private Response response(QuickReply q) {
        return new Response(
            q.getId(),
            q.getName(),
            q.getShortcut(),
            q.getDescription(),
            q.getContent(),
            q.getScope(),
            q.isActive(),
            q.getVersion()
        );
    }

    public List<Response> list(AccountSessions.Identity actor, int page) {
        return list(actor, page, null);
    }

    public List<Response> list(AccountSessions.Identity actor, int page, String scope) {
        if (scope != null && !java.util.Set.of("GLOBAL", "PERSONAL").contains(scope)) {
            throw ApiException.badRequest("Escopo de template inválido");
        }
        if (page < 0) {
            throw ApiException.badRequest("Página inválida");
        }
        return repository
            .visible(actor.id(), scope, PageRequest.of(page, 100))
            .stream()
            .map(this::response)
            .toList();
    }

    private QuickReply require(Long id, AccountSessions.Identity actor) {
        var q = repository
            .findById(id)
            .filter(row -> row.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Resposta rápida não encontrada"));
        if (
            ("GLOBAL".equals(q.getScope()) && actor.role() != UserRole.ADMIN) ||
            ("PERSONAL".equals(q.getScope()) && !Objects.equals(q.getOwnerUserId(), actor.id()))
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sem permissão para alterar este template");
        }
        return q;
    }

    public Response save(Long id, Input input, AccountSessions.Identity actor) {
        String shortcut = input.shortcut().toLowerCase(Locale.ROOT);
        if (RESERVED.contains(shortcut)) {
            throw ApiException.conflict("Atalho reservado para variável do sistema");
        }
        if ("GLOBAL".equals(input.scope()) && actor.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Somente ADMIN gerencia templates globais");
        }
        var matcher = VARIABLE.matcher(input.content());
        while (matcher.find()) {
            if (!RESERVED.contains(matcher.group(1))) {
                throw ApiException.badRequest(
                    "Templates só podem referenciar variáveis nativas; não é permitida recursão"
                );
            }
        }
        QuickReply q =
            id == null
                ? new QuickReply(input.scope(), "GLOBAL".equals(input.scope()) ? null : actor.id())
                : require(id, actor);
        if (
            id != null &&
            (!Objects.equals(q.getVersion(), input.expectedVersion()) || !q.getScope().equals(input.scope()))
        ) {
            throw ApiException.conflict("Template alterado ou escopo incompatível; atualize");
        }
        q.update(input.name(), shortcut, input.description(), input.content(), input.active());
        try {
            return response(repository.saveAndFlush(q));
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("Atalho já cadastrado neste escopo");
        }
    }

    public void delete(Long id, AccountSessions.Identity actor) {
        require(id, actor).delete();
    }

    public Preview preview(PreviewInput input, AccountSessions.Identity actor) {
        var c = authorization.require(input.conversationId(), actor);
        var values = new HashMap<String, String>();
        values.put("nome", c.getDisplayName());
        if (c.getDisplayName() != null) {
            values.put("primeiro_nome", c.getDisplayName().trim().split("\\s+")[0]);
        }
        values.put("telefone", c.getContactPhone());
        values.put("atendente", actor.name());
        values.put("protocolo", c.getId().toHexString());
        values.put("data", LocalDate.now(ZoneOffset.UTC).toString());
        values.put("hora", LocalTime.now(ZoneOffset.UTC).withSecond(0).withNano(0).toString());
        var matcher = VARIABLE.matcher(input.content());
        var result = new StringBuffer();
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
                value = matcher.group();
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return new Preview(result.toString(), missing.stream().distinct().toList());
    }
}
