package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.*;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.repository.ConversationReadRepository;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.message.model.MessageCursor;
import com.wxn.chatbot.message.repository.MessageRepository;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class ConversationReadService {

    private final ConversationService conversations;
    private final ConversationReadRepository reads;
    private final MessageRepository messages;
    private final MemberService members;
    private final ConversationEventPublisher events;

    public ConversationReadService(
        ConversationService conversations,
        ConversationReadRepository reads,
        MessageRepository messages,
        MemberService members,
        ConversationEventPublisher events
    ) {
        this.conversations = conversations;
        this.reads = reads;
        this.messages = messages;
        this.members = members;
        this.events = events;
    }

    public void requireReader(Long companyId, Long memberId) {
        members.requireHumanActive(companyId, memberId);
    }

    public Map<ObjectId, Long> counts(Long companyId, Long memberId, List<Conversation> rows) {
        requireReader(companyId, memberId);
        var ids = rows.stream().map(Conversation::getId).toList();
        return reads.unreadCounts(
            companyId,
            ids,
            reads.findByCompanyIdAndMemberIdAndConversationIdIn(companyId, memberId, ids)
        );
    }

    public ReadUpdate mark(Long companyId, String id, Read request) {
        Conversation conversation = conversations.require(companyId, id);
        requireReader(companyId, request.memberId());
        if (!ObjectId.isValid(request.throughMessageId())) throw ApiException.badRequest(
            "throughMessageId inválido"
        );
        var message = messages
            .findByIdAndCompanyIdAndConversationId(
                new ObjectId(request.throughMessageId()),
                companyId,
                conversation.getId()
            )
            .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada na conversa"));
        var changed = reads.advance(
            companyId,
            conversation.getId(),
            request.memberId(),
            MessageCursor.of(message)
        );
        var current = changed.orElseGet(() ->
            reads
                .findByCompanyIdAndMemberIdAndConversationIdIn(
                    companyId,
                    request.memberId(),
                    List.of(conversation.getId())
                )
                .get(0)
        );
        long count = reads
            .unreadCounts(companyId, List.of(conversation.getId()), List.of(current))
            .getOrDefault(conversation.getId(), 0L);
        var response = new ReadUpdate(
            companyId,
            id,
            request.memberId(),
            current.getThroughMessageId().toHexString(),
            current.getLastReadAt().toInstant(),
            count
        );
        if (changed.isPresent()) events.publish(
            ConversationEventTypes.CONVERSATION_READ_UPDATED,
            companyId,
            response
        );
        return response;
    }
}
