package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.repository.ConversationWriteLeaseRepository;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ConversationWriteCoordinator {

    private final ConversationWriteLeaseRepository repository;
    private final ThreadLocal<Map<String, Held>> owned = ThreadLocal.withInitial(HashMap::new);

    private static class Held {

        String token = UUID.randomUUID().toString();
        int count;
    }

    public interface Lease extends AutoCloseable {
        void close();
    }

    public ConversationWriteCoordinator(ConversationWriteLeaseRepository repository) {
        this.repository = repository;
    }

    public Lease acquire(String id) {
        var locks = owned.get();
        var held = locks.get(id);
        if (held == null) {
            held = new Held();
            try {
                repository.acquire(id, held.token);
            } catch (org.springframework.dao.DuplicateKeyException busy) {
                throw ApiException.conflict("Outra operação está em andamento neste atendimento");
            }
            locks.put(id, held);
        }
        held.count++;
        final Held current = held;
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true) && --current.count == 0) {
                locks.remove(id);
                try {
                    repository.release(id, current.token);
                } finally {
                    if (locks.isEmpty()) {
                        owned.remove();
                    }
                }
            }
        };
    }

    public <T> T execute(String id, Supplier<T> operation) {
        try (var lease = acquire(id)) {
            return operation.get();
        }
    }
}
