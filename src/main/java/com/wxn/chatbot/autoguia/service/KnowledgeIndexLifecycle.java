package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(KnowledgeEmbeddingProvider.class)
@ConditionalOnProperty(prefix = "wxn.autoguia.embedding", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "wxn.autoguia.indexing", name = "enabled", havingValue = "true")
public class KnowledgeIndexLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeIndexLifecycle.class);
    private final KnowledgeIndexJobProcessor processor;
    private final long delayMillis;
    private final int leaseSeconds;
    private ScheduledExecutorService executor;

    public KnowledgeIndexLifecycle(
        KnowledgeIndexJobProcessor processor,
        @Value("${wxn.autoguia.indexing.delay:PT5S}") java.time.Duration delay,
        @Value("${wxn.autoguia.indexing.lease:PT180S}") java.time.Duration lease
    ) {
        if (delay.isZero() || delay.isNegative() || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Indexing delay and lease must be positive");
        }
        this.processor = processor;
        this.delayMillis = delay.toMillis();
        this.leaseSeconds = Math.toIntExact(lease.toSeconds());
        if (delayMillis <= 0 || leaseSeconds <= 0) {
            throw new IllegalArgumentException("Indexing delay and lease must be at least one unit");
        }
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "wxn-knowledge-index");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            processor.runOnce(leaseSeconds);
        } catch (RuntimeException failure) {
            LOGGER.warn("Knowledge indexing tick failed");
        }
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
