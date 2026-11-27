package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.domain.KnowledgeChunker;
import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository.Job;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(KnowledgeEmbeddingProvider.class)
@ConditionalOnProperty(prefix = "wxn.autoguia.indexing", name = "enabled", havingValue = "true")
public class KnowledgeIndexJobProcessor {

    private static final int CHUNK_CODE_POINTS = 1200;
    private static final int MAX_CHUNKS = 100;
    private final KnowledgeIndexJobRepository jobs;
    private final KnowledgeArticleService articles;
    private final KnowledgeEmbeddingProvider embeddings;
    private final KnowledgePublicationFinalizer finalizer;

    public KnowledgeIndexJobProcessor(
        KnowledgeIndexJobRepository jobs,
        KnowledgeArticleService articles,
        KnowledgeEmbeddingProvider embeddings,
        KnowledgePublicationFinalizer finalizer
    ) {
        this.jobs = jobs;
        this.articles = articles;
        this.embeddings = embeddings;
        this.finalizer = finalizer;
    }

    private boolean process(Job job, long deadlineNanos) {
        try {
            requireTime(job, deadlineNanos);
            var detail = articles.get(job.articleId());
            var version = detail
                .versions()
                .stream()
                .filter(item -> item.id() == job.versionId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Indexing version is unavailable"));
            List<String> pieces = KnowledgeChunker.split(version.content(), CHUNK_CODE_POINTS);
            if (pieces.isEmpty() || pieces.size() > MAX_CHUNKS) {
                throw new IllegalStateException("Indexing content is outside supported limits");
            }
            List<KnowledgeArticleService.IndexedChunk> chunks = new ArrayList<>(pieces.size());
            for (int position = 0; position < pieces.size(); position++) {
                requireTime(job, deadlineNanos);
                String content = pieces.get(position);
                chunks.add(
                    new KnowledgeArticleService.IndexedChunk(position, content, embeddings.embed(content))
                );
            }
            requireTime(job, deadlineNanos);
            return finalizer.completePublication(job, chunks);
        } catch (RuntimeException failure) {
            jobs.fail(job.id(), job.attempts(), "Knowledge indexing failed");
            return false;
        }
    }

    public int runOnce(int leaseSeconds) {
        if (leaseSeconds <= 1) {
            throw new IllegalArgumentException("Lease must exceed one second");
        }
        long started = System.nanoTime();
        long processingBudgetNanos = (java.util.concurrent.TimeUnit.SECONDS.toNanos(leaseSeconds) * 3) / 4;
        long deadlineNanos = started + processingBudgetNanos;
        List<Job> claimed = jobs.claim(1, leaseSeconds);
        int processed = 0;
        for (Job job : claimed) {
            if (process(job, deadlineNanos)) {
                processed++;
            }
        }
        return processed;
    }

    private void requireTime(Job job, long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new IllegalStateException("Knowledge indexing deadline exceeded");
        }
    }
}
