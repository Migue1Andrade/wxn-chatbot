package com.wxn.chatbot.kanban.repository;

import com.wxn.chatbot.kanban.entity.KanbanView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KanbanViewRepository extends JpaRepository<KanbanView, Long> {
    List<KanbanView> findByOwnerUserIdOrderByName(Long owner);
}
