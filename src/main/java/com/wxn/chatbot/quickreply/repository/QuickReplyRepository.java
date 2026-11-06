package com.wxn.chatbot.quickreply.repository;

import com.wxn.chatbot.quickreply.entity.QuickReply;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface QuickReplyRepository extends JpaRepository<QuickReply, Long> {
    java.util.Optional<QuickReply> findByScopeAndShortcutAndDeletedAtIsNull(String scope, String shortcut);

    @Query(
        "select q from QuickReply q where q.deletedAt is null and (:scope is null or q.scope = :scope) and (q.scope = 'GLOBAL' or q.ownerUserId = :owner) order by q.shortcut"
    )
    List<QuickReply> visible(Long owner, String scope, Pageable page);
}
