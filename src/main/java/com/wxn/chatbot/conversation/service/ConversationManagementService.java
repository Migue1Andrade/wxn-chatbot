package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.*;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.model.*;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversationManagementService {

    private final ConversationService conversations;
    private final ConversationTransitionService transitions;
    private final MemberService members;

    public ConversationManagementService(
        ConversationService conversations,
        ConversationTransitionService transitions,
        MemberService members
    ) {
        this.conversations = conversations;
        this.transitions = transitions;
        this.members = members;
    }

    public NormalizedConversation handoffToQueue(String id, Action request) {
        var actor = members.requireActive(request.actorMemberId());
        Conversation current = conversations.require(id);
        if (
            actor.getType() != com.wxn.chatbot.member.entity.MemberType.BOT ||
            !Objects.equals(current.getAssignedMemberId(), actor.getId())
        ) {
            throw ApiException.conflict("A conversa não está atribuída a este BOT");
        }
        requireOpen(current);
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.PENDING,
                null,
                null,
                ConversationActivityType.BOT_HANDOFF_TO_QUEUE,
                request.actorMemberId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_UPDATED
            )
        );
    }

    public NormalizedConversation cancelBotForInactivity(String id, Action request) {
        var actor = members.requireActive(request.actorMemberId());
        Conversation current = conversations.require(id);
        if (
            actor.getType() != com.wxn.chatbot.member.entity.MemberType.BOT ||
            !Objects.equals(current.getAssignedMemberId(), actor.getId())
        ) {
            throw ApiException.conflict("A conversa não está atribuída a este BOT");
        }
        requireOpen(current);
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.CANCELLED,
                current.getAssignedMemberId(),
                OffsetDateTime.now(),
                ConversationActivityType.BOT_INACTIVITY_CANCELLED,
                actor.getId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_CLOSED
            )
        );
    }

    public NormalizedConversation resolveAsBot(String id, Action request) {
        var actor = members.requireActive(request.actorMemberId());
        Conversation current = conversations.require(id);
        if (
            actor.getType() != com.wxn.chatbot.member.entity.MemberType.BOT ||
            !Objects.equals(current.getAssignedMemberId(), actor.getId())
        ) {
            throw ApiException.conflict("A conversa não está atribuída a este BOT");
        }
        requireOpen(current);
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.RESOLVED,
                current.getAssignedMemberId(),
                null,
                ConversationActivityType.BOT_CONFIRMED_RESOLUTION,
                actor.getId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_UPDATED
            )
        );
    }

    public NormalizedConversation status(String id, Action request, ConversationStatus target) {
        if (target == ConversationStatus.CLOSED) {
            return close(id, request);
        }
        if (
            target != ConversationStatus.IN_PROGRESS &&
            target != ConversationStatus.WAITING_CUSTOMER &&
            target != ConversationStatus.RESOLVED
        ) {
            throw ApiException.badRequest("Use a atribuição para triagem. Cancelamento é automático.");
        }
        var current = requireHumanOwner(id, request.actorMemberId());
        requireOpen(current);
        return changeStatus(current, request, target);
    }

    public NormalizedConversation waitForCustomerAsBot(String id, Action request) {
        var actor = members.requireActive(request.actorMemberId());
        var current = conversations.require(id);
        if (
            actor.getType() != com.wxn.chatbot.member.entity.MemberType.BOT ||
            !Objects.equals(current.getAssignedMemberId(), actor.getId())
        ) {
            throw ApiException.conflict("Conversa não está atribuída a este BOT");
        }
        requireOpen(current);
        return changeStatus(current, request, ConversationStatus.WAITING_CUSTOMER);
    }

    private NormalizedConversation changeStatus(
        Conversation current,
        Action request,
        ConversationStatus target
    ) {
        if (target == current.operationalStatus()) {
            return ConversationMapper.response(current);
        }
        if (
            (target == ConversationStatus.IN_PROGRESS || target == ConversationStatus.WAITING_CUSTOMER) &&
            current.getAssignedMemberId() == null
        ) {
            throw ApiException.conflict("Atribua um responsável antes de iniciar o atendimento");
        }
        if (target == ConversationStatus.PENDING && current.getAssignedMemberId() != null) {
            throw ApiException.conflict(
                "Uma conversa atribuída não pode voltar para triagem sem desatribuição"
            );
        }
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                target,
                current.getAssignedMemberId(),
                target == ConversationStatus.CLOSED ? OffsetDateTime.now() : null,
                target == ConversationStatus.RESOLVED
                    ? ConversationActivityType.CONVERSATION_RESOLVED
                    : ConversationActivityType.CONVERSATION_STATUS_CHANGED,
                request.actorMemberId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_UPDATED
            )
        );
    }

    public NormalizedConversation assume(String id, Action request) {
        members.requireHumanActive(request.actorMemberId());
        Conversation current = conversations.require(id);
        requireOpen(current);
        if (Objects.equals(current.getAssignedMemberId(), request.actorMemberId())) {
            return ConversationMapper.response(current);
        }
        if (current.getAssignedMemberId() != null) {
            throw ApiException.conflict("Atendimento já assumido por outro membro");
        }
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.IN_PROGRESS,
                request.actorMemberId(),
                null,
                ConversationActivityType.MEMBER_ASSUMED,
                request.actorMemberId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_ASSIGNED
            )
        );
    }

    public NormalizedConversation assign(String id, Assign request) {
        Conversation current = validateAssignment(id, request);
        if (Objects.equals(current.getAssignedMemberId(), request.memberId())) {
            return ConversationMapper.response(current);
        }
        if (current.getAssignedMemberId() != null) {
            throw ApiException.conflict("Conversa já atribuída; utilize transferência");
        }
        return assignMember(
            current,
            request,
            ConversationActivityType.MEMBER_ASSIGNED,
            ConversationEventTypes.CONVERSATION_ASSIGNED
        );
    }

    public NormalizedConversation transfer(String id, Assign request) {
        Conversation current = validateAssignment(id, request);
        if (Objects.equals(current.getAssignedMemberId(), request.memberId())) {
            return ConversationMapper.response(current);
        }
        if (current.getAssignedMemberId() == null) {
            throw ApiException.conflict("Conversa sem responsável; utilize atribuição");
        }
        return assignMember(
            current,
            request,
            ConversationActivityType.MEMBER_TRANSFERRED,
            ConversationEventTypes.CONVERSATION_TRANSFERRED
        );
    }

    private Conversation validateAssignment(String id, Assign request) {
        members.requireActive(request.actorMemberId());
        members.requireActive(request.memberId());
        Conversation current = conversations.require(id);
        requireOpen(current);
        return current;
    }

    private NormalizedConversation assignMember(
        Conversation current,
        Assign request,
        ConversationActivityType activity,
        String eventType
    ) {
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.IN_PROGRESS,
                request.memberId(),
                null,
                activity,
                request.actorMemberId(),
                request.reason(),
                eventType
            )
        );
    }

    public NormalizedConversation close(String id, Action request) {
        Conversation current = requireHumanOwner(id, request.actorMemberId());
        if (current.operationalStatus() == ConversationStatus.CLOSED) {
            return ConversationMapper.response(current);
        }
        requireOpen(current);
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                ConversationStatus.CLOSED,
                current.getAssignedMemberId(),
                OffsetDateTime.now(),
                ConversationActivityType.CONVERSATION_CLOSED,
                request.actorMemberId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_CLOSED
            )
        );
    }

    public NormalizedConversation reopen(String id, Action request) {
        Conversation current = requireHumanOwner(id, request.actorMemberId());
        if (
            current.getBotSessionEndedAt() != null ||
            current.operationalStatus() == ConversationStatus.CANCELLED
        ) {
            throw ApiException.conflict("A sessão encerrada pelo BOT permanece no histórico");
        }
        if (
            current.operationalStatus() != ConversationStatus.CLOSED &&
            current.operationalStatus() != ConversationStatus.RESOLVED
        ) {
            return ConversationMapper.response(current);
        }
        Long responsible = validResponsible(current);
        return apply(
            current,
            request.expectedVersion(),
            new ConversationTransition(
                responsible == null ? ConversationStatus.PENDING : ConversationStatus.IN_PROGRESS,
                responsible,
                null,
                ConversationActivityType.CONVERSATION_REOPENED,
                request.actorMemberId(),
                request.reason(),
                ConversationEventTypes.CONVERSATION_REOPENED
            )
        );
    }

    private Conversation requireHumanOwner(String id, Long actorId) {
        members.requireHumanActive(actorId);
        var current = conversations.require(id);
        if (!Objects.equals(current.getAssignedMemberId(), actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assuma o atendimento para alterar o status");
        }
        return current;
    }

    private Long validResponsible(Conversation current) {
        if (current.getAssignedMemberId() == null) {
            return null;
        }
        try {
            return members.requireActive(current.getAssignedMemberId()).getId();
        } catch (ApiException unavailable) {
            if (
                unavailable.getStatus() == HttpStatus.NOT_FOUND ||
                unavailable.getStatus() == HttpStatus.CONFLICT
            ) {
                return null;
            }
            throw unavailable;
        }
    }

    private void requireOpen(Conversation current) {
        if (
            current.operationalStatus() == ConversationStatus.CLOSED ||
            current.operationalStatus() == ConversationStatus.RESOLVED ||
            current.operationalStatus() == ConversationStatus.CANCELLED
        ) {
            throw ApiException.conflict("Reabra o atendimento antes de atribuir ou transferir");
        }
    }

    private NormalizedConversation apply(
        Conversation current,
        long version,
        ConversationTransition transition
    ) {
        return ConversationMapper.response(transitions.apply(current, version, transition));
    }
}
