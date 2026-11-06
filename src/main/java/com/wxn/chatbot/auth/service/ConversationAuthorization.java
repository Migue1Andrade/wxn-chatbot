package com.wxn.chatbot.auth.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.user.entity.UserRole;
import java.util.Objects;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConversationAuthorization {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MemberService members;

    public ConversationAuthorization(
        ConversationRepository conversations,
        MessageRepository messages,
        MemberService members
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.members = members;
    }

    public Conversation require(String id, AccountSessions.Identity actor) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("Identificador de conversa inválido");
        }
        var conversation = conversations
            .findById(new ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Conversa não encontrada"));
        check(conversation, actor);
        return conversation;
    }

    public void check(Conversation conversation, AccountSessions.Identity actor) {
        if (actor.role() == UserRole.ADMIN) {
            return;
        }
        if (
            actor.memberId() == null || !Objects.equals(actor.memberId(), conversation.getAssignedMemberId())
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Conversa não atribuída a este atendente");
        }
        members.requireHumanActive(actor.memberId());
    }

    public void message(String id, AccountSessions.Identity actor) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("Identificador de mensagem inválido");
        }
        var message = messages
            .findById(new ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada"));
        require(message.getConversationId().toHexString(), actor);
    }
}
