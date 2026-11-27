package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository.Job;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService.IndexedChunk;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePublicationFinalizer {

    private final KnowledgeIndexJobRepository jobs;
    private final KnowledgeArticleService articles;

    public KnowledgePublicationFinalizer(KnowledgeIndexJobRepository jobs, KnowledgeArticleService articles) {
        this.jobs = jobs;
        this.articles = articles;
    }

    @Transactional
    public boolean completePublication(Job job, List<IndexedChunk> chunks) {
        if (!jobs.complete(job.id(), job.attempts())) {
            return false;
        }
        articles.publish(job.articleId(), job.versionId(), chunks);
        return true;
    }
}
