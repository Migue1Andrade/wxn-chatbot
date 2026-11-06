package com.wxn.chatbot.collaboration.service;

import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.collaboration.document.TeamEntry;
import com.wxn.chatbot.collaboration.dto.TeamDtos.*;
import com.wxn.chatbot.collaboration.repository.*;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.user.entity.UserRole;
import java.time.Instant;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TeamService {

    private final ConversationAuthorization access;
    private final TeamEntryRepository repository;
    private final TeamMutationRepository mutations;
    private final ConversationEventPublisher events;

    public TeamService(
        ConversationAuthorization access,
        TeamEntryRepository repository,
        TeamMutationRepository mutations,
        ConversationEventPublisher events
    ) {
        this.access = access;
        this.repository = repository;
        this.mutations = mutations;
        this.events = events;
    }

    private Entry response(TeamEntry e) {
        return new Entry(
            e.id().toHexString(),
            e.conversationId().toHexString(),
            e.authorUserId(),
            e.authorName(),
            e.kind(),
            e.content(),
            e.pinned(),
            e.createdAt(),
            e.updatedAt(),
            e.version()
        );
    }

    public List<Entry> list(String conversation, String kind, int page, AccountSessions.Identity actor) {
        var c = access.require(conversation, actor);
        if (page < 0 || page > 10000) {
            throw ApiException.badRequest("Página inválida");
        }
        return repository
            .findByConversationIdAndKindAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(
                c.getId(),
                kind,
                PageRequest.of(page, 30)
            )
            .stream()
            .map(this::response)
            .toList();
    }

    public Entry create(String conversation, String kind, Create input, AccountSessions.Identity actor) {
        var c = access.require(conversation, actor);
        var previous = repository.findByConversationIdAndAuthorUserIdAndOperationId(
            c.getId(),
            actor.id(),
            input.operationId()
        );
        if (previous.isPresent()) {
            return response(previous.get());
        }
        var now = Instant.now();
        TeamEntry entry = new TeamEntry(
            null,
            c.getId(),
            actor.id(),
            actor.name(),
            kind,
            input.content().trim(),
            false,
            now,
            now,
            null,
            0,
            List.of(),
            input.operationId()
        );
        try {
            entry = repository.insert(entry);
        } catch (org.springframework.dao.DuplicateKeyException concurrent) {
            return response(
                repository
                    .findByConversationIdAndAuthorUserIdAndOperationId(
                        c.getId(),
                        actor.id(),
                        input.operationId()
                    )
                    .orElseThrow(() -> concurrent)
            );
        }
        publish(conversation);
        return response(entry);
    }

    private TeamEntry require(String id, AccountSessions.Identity actor) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("ID inválido");
        }
        var entry = repository
            .findById(new ObjectId(id))
            .filter(e -> e.deletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Registro não encontrado"));
        access.require(entry.conversationId().toHexString(), actor);
        return entry;
    }

    public Entry edit(String id, Edit input, boolean delete, AccountSessions.Identity actor) {
        var e = require(id, actor);
        if (
            !Objects.equals(actor.id(), e.authorUserId()) &&
            ("INTERNAL".equals(e.kind()) || actor.role() != UserRole.ADMIN)
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Somente o autor pode alterar esta mensagem");
        }
        var result = mutations.update(
            e,
            input.expectedVersion(),
            input.content().trim(),
            input.pinned(),
            delete,
            actor.id()
        );
        publish(e.conversationId().toHexString());
        return response(result);
    }

    public List<Revision> history(String id, AccountSessions.Identity actor) {
        return require(id, actor)
            .revisions()
            .stream()
            .map(r -> new Revision(r.content(), r.actorUserId(), r.at()))
            .toList();
    }

    public String draft(String id, String mode, AccountSessions.Identity actor) {
        var c = access.require(id, actor);
        if (!Set.of("EXTERNAL", "INTERNAL").contains(mode)) {
            throw ApiException.badRequest("Modo inválido");
        }
        return mutations.draft(c.getId(), actor.id(), mode);
    }

    public void draft(String id, Draft input, AccountSessions.Identity actor) {
        mutations.draft(access.require(id, actor).getId(), actor.id(), input.mode(), input.content());
    }

    private void publish(String conversation) {
        events.publish(ConversationEventTypes.TEAM_UPDATED, new TeamUpdate(conversation));
    }

    public record TeamUpdate(String conversationId) implements CanonicalEventPayload {}
}
