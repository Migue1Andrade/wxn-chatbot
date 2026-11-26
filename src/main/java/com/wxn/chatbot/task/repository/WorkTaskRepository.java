package com.wxn.chatbot.task.repository;

import com.wxn.chatbot.task.entity.WorkTask;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {
    @Query(
        "select t from WorkTask t where (:all = true or t.assignedUserId = :user) and (:status is null or t.status = :status) order by t.dueAt asc, t.id asc"
    )
    List<WorkTask> visible(Long user, boolean all, String status, Pageable page);

    @Query(
        "select t from WorkTask t where (:all = true or t.assignedUserId = :user) and (:status = 'ANY' or t.status = :status) and (:type = 'ANY' or t.type = :type) and t.dueAt >= :fromAt and t.dueAt < :toAt order by t.dueAt asc, t.id asc"
    )
    List<WorkTask> visibleByPeriod(
        Long user,
        boolean all,
        String status,
        String type,
        Instant fromAt,
        Instant toAt,
        Pageable page
    );

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<WorkTask> findTop100ByStatusAndDueAtBeforeAndOverdueNotifiedFalseOrderByDueAt(
        String status,
        Instant now
    );
}
