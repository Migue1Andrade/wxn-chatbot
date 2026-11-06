package com.wxn.chatbot.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.dto.ConversationQuery;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import com.wxn.chatbot.member.service.MemberService;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConversationQueryServiceTest {

    private final ConversationRepository repository = mock(ConversationRepository.class);
    private final MemberService members = mock(MemberService.class);
    private final ConversationReadService reads = mock(ConversationReadService.class);
    private final ConversationActivityService activities = mock(ConversationActivityService.class);
    private final ConversationQueryService service = new ConversationQueryService(
        repository,

        members,
        reads,
        activities
    );

    private Conversation row() {
        Conversation row = new Conversation();
        row.setId(new ObjectId());

        row.setStatus("PENDING");
        return row;
    }

    @Test
    void directServiceCallsRejectInvalidPaginationAndSearch() {
        for (ConversationQuery query : List.of(
            new ConversationQuery(-1, 30, null, null, null, null, null),
            new ConversationQuery(0, 0, null, null, null, null, null),
            new ConversationQuery(0, 201, null, null, null, null, null),
            new ConversationQuery(0, 30, null, null, null, null, "x".repeat(101))
        )) {
            assertThatThrownBy(() -> service.page(query)).isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
        verifyNoInteractions(repository, reads, activities);
    }

    @Test
    void incompatibleAssigneeFiltersNeverReachRepository() {
        assertThatThrownBy(() ->
            service.page(new ConversationQuery(null, null, null, 2L, true, null, null))
        ).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
        verify(members).require(2L);
        verifyNoInteractions(repository, reads, activities);
    }

    @Test
    void extraRowIsOnlyPaginationProbeAndIsNotMappedOrCounted() {
        Conversation first = row();
        Conversation probe = row();
        when(repository.search(null, null, false, 0, 1, null, true)).thenReturn(List.of(first, probe));
        when(reads.counts(2L, List.of(first))).thenReturn(Map.of(first.getId(), 4L));
        var page = service.page(new ConversationQuery(0, 1, null, null, false, 2L, null));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(first.getId().toHexString());
        assertThat(page.items().get(0).unreadCount()).isEqualTo(4L);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPage()).isEqualTo(1);
        verify(activities).flush(first);
        verify(activities, never()).flush(probe);
    }

    @Test
    void emptyPageWithoutReaderDoesNotCalculateUnreadCounts() {
        when(repository.search(null, null, false, 0, 30, null, true)).thenReturn(List.of());
        var page = service.page(new ConversationQuery(null, null, null, null, null, null, null));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextPage()).isNull();
        verifyNoInteractions(reads, activities);
    }

    @Test
    void legacyListUsesDefaultLimitAndOmitsUnreadCountWithoutReader() {
        Conversation first = row();
        when(repository.search(null, null, false, 0, 100, null, false)).thenReturn(List.of(first));
        var rows = service.list(new ConversationQuery(null, null, null, null, null, null, null));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).unreadCount()).isNull();
        verifyNoInteractions(reads);
    }
}
