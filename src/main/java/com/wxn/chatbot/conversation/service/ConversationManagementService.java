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

    public NormalizedConversation assume(Long companyId, String id, Action request) {
        members.requireHumanActive(companyId, request.actorMemberId());
        Conversation current = conversations.require(companyId, id);
        requireOpen(current);
        if (
            Objects.equals(current.getAssignedMemberId(), request.actorMemberId())
        ) return ConversationMapper.response(current);
        if (current.getAssignedMemberId() != null) throw ApiException.conflict(
            "Atendimento já assumido por outro membro"
        );
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

    public NormalizedConversation assign(Long companyId, String id, Assign request) {
        Conversation current = validateAssignment(companyId, id, request);
        if (
            Objects.equals(current.getAssignedMemberId(), request.memberId())
        ) return ConversationMapper.response(current);
        if (current.getAssignedMemberId() != null) throw ApiException.conflict(
            "Conversa já atribuída; utilize transferência"
        );
        return assignMember(
            current,
            request,
            ConversationActivityType.MEMBER_ASSIGNED,
            ConversationEventTypes.CONVERSATION_ASSIGNED
        );
    }

    public NormalizedConversation transfer(Long companyId, String id, Assign request) {
        Conversation current = validateAssignment(companyId, id, request);
        if (
            Objects.equals(current.getAssignedMemberId(), request.memberId())
        ) return ConversationMapper.response(current);
        if (current.getAssignedMemberId() == null) throw ApiException.conflict(
            "Conversa sem responsável; utilize atribuição"
        );
        return assignMember(
            current,
            request,
            ConversationActivityType.MEMBER_TRANSFERRED,
            ConversationEventTypes.CONVERSATION_TRANSFERRED
        );
    }

    private Conversation validateAssignment(Long companyId, String id, Assign request) {
        members.requireActive(companyId, request.actorMemberId());
        members.requireActive(companyId, request.memberId());
        Conversation current = conversations.require(companyId, id);
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

    public NormalizedConversation close(Long companyId, String id, Action request) {
        members.requireActive(companyId, request.actorMemberId());
        Conversation current = conversations.require(companyId, id);
        if (current.operationalStatus() == ConversationStatus.CLOSED) return ConversationMapper.response(
            current
        );
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

    public NormalizedConversation reopen(Long companyId, String id, Action request) {
        members.requireActive(companyId, request.actorMemberId());
        Conversation current = conversations.require(companyId, id);
        if (current.operationalStatus() != ConversationStatus.CLOSED) return ConversationMapper.response(
            current
        );
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

    private Long validResponsible(Conversation current) {
        if (current.getAssignedMemberId() == null) return null;
        try {
            return members.requireActive(current.getCompanyId(), current.getAssignedMemberId()).getId();
        } catch (ApiException unavailable) {
            if (
                unavailable.getStatus() == HttpStatus.NOT_FOUND ||
                unavailable.getStatus() == HttpStatus.CONFLICT
            ) return null;
            throw unavailable;
        }
    }

    private void requireOpen(Conversation current) {
        if (current.operationalStatus() == ConversationStatus.CLOSED) throw ApiException.conflict(
            "Reabra o atendimento antes de atribuir ou transferir"
        );
    }

    private NormalizedConversation apply(
        Conversation current,
        long version,
        ConversationTransition transition
    ) {
        return ConversationMapper.response(transitions.apply(current, version, transition));
    }
}
