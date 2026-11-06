package com.wxn.chatbot.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.repository.ConversationWriteLeaseRepository;
import org.junit.jupiter.api.Test;

class ConversationWriteCoordinatorTest {

    @Test
    void nestedServiceCallsShareLeaseAndReleaseAfterOutermostOperation() {
        var store = mock(ConversationWriteLeaseRepository.class);
        var coordinator = new ConversationWriteCoordinator(store);
        assertThat(
            coordinator.execute("conversation", () -> coordinator.execute("conversation", () -> 7))
        ).isEqualTo(7);
        verify(store, times(1)).acquire(eq("conversation"), anyString());
        verify(store, times(1)).release(eq("conversation"), anyString());
    }

    @Test
    void exceptionAlwaysReleasesLeaseAndContentionIsControlled() {
        var store = mock(ConversationWriteLeaseRepository.class);
        var coordinator = new ConversationWriteCoordinator(store);
        assertThatThrownBy(() ->
            coordinator.execute("conversation", () -> {
                throw new IllegalStateException("fixture");
            })
        ).isInstanceOf(IllegalStateException.class);
        verify(store).release(eq("conversation"), anyString());
        doThrow(new org.springframework.dao.DuplicateKeyException("fixture contention"))
            .when(store)
            .acquire(eq("busy"), anyString());
        assertThatThrownBy(() -> coordinator.execute("busy", () -> 1)).isInstanceOf(ApiException.class);
    }
}
