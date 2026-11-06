package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.dto.*;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConversationQueryService {

    private final ConversationActivityService activities;
    private final ConversationRepository repository;
    private final MemberService members;
    private final ConversationReadService reads;

    public ConversationQueryService(
        ConversationRepository repository,
        MemberService members,
        ConversationReadService reads,
        ConversationActivityService activities
    ) {
        this.activities = activities;
        this.repository = repository;

        this.members = members;
        this.reads = reads;
    }

    public List<NormalizedConversation> list(ConversationQuery query) {
        return responses(query.readerMemberId(), search(query, query.pageSize(100), false));
    }

    public ConversationPage page(ConversationQuery query) {
        int size = query.pageSize(30);
        var rows = new ArrayList<>(search(query, size, true));
        boolean more = rows.size() > size;
        if (more) {
            rows.remove(rows.size() - 1);
        }
        return new ConversationPage(
            responses(query.readerMemberId(), rows),
            more ? query.pageNumber() + 1 : null,
            more
        );
    }

    private List<Conversation> search(ConversationQuery query, int size, boolean probe) {
        ConversationService.page(query.pageNumber(), size);
        if (query.assignedMemberId() != null) {
            members.require(query.assignedMemberId());
        }
        if (query.onlyUnassigned() && query.assignedMemberId() != null) {
            throw ApiException.badRequest("Filtros de responsável incompatíveis");
        }
        if (query.search() != null && query.search().length() > 100) {
            throw ApiException.badRequest("Busca deve ter até 100 caracteres");
        }
        return repository.search(
            query.status(),
            query.assignedMemberId(),
            query.onlyUnassigned(),
            query.pageNumber(),
            size,
            query.search(),
            probe
        );
    }

    private List<NormalizedConversation> responses(Long reader, List<Conversation> rows) {
        rows.forEach(activities::flush);
        var counts = reader == null ? Map.<org.bson.types.ObjectId, Long>of() : reads.counts(reader, rows);
        return rows
            .stream()
            .map(conversation ->
                ConversationMapper.response(
                    conversation,
                    reader == null ? null : counts.getOrDefault(conversation.getId(), 0L)
                )
            )
            .toList();
    }
}
