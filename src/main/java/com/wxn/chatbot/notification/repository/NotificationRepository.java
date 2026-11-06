package com.wxn.chatbot.notification.repository;

import com.wxn.chatbot.notification.entity.UserNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long user, Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        value = "INSERT INTO user_notifications(user_id,type,title,resource_type,resource_id,created_at,deduplication_key) VALUES (:user,:type,:title,:resource,:id,CURRENT_TIMESTAMP,:key) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertOnce(
        @org.springframework.data.repository.query.Param("user") Long user,
        @org.springframework.data.repository.query.Param("type") String type,
        @org.springframework.data.repository.query.Param("title") String title,
        @org.springframework.data.repository.query.Param("resource") String resource,
        @org.springframework.data.repository.query.Param("id") String id,
        @org.springframework.data.repository.query.Param("key") String key
    );

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT * FROM user_notifications WHERE deduplication_key=:key",
        nativeQuery = true
    )
    java.util.Optional<com.wxn.chatbot.notification.entity.UserNotification> byDeliveryKey(
        @org.springframework.data.repository.query.Param("key") String key
    );

    long countByUserIdAndReadAtIsNull(Long user);

    @Modifying
    @Query(
        "update UserNotification n set n.readAt = CURRENT_TIMESTAMP where n.userId = :user and n.readAt is null"
    )
    void readAll(Long user);
}
