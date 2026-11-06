package com.wxn.chatbot.user.repository;

import com.wxn.chatbot.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByCpf(String cpf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.wxn.chatbot.user.entity.UserRole.ADMIN order by u.id")
    List<User> lockAdministrators();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> lockById(Long id);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    List<User> findByDeletedAtIsNullOrderById();
    List<User> findByIdInAndDeletedAtIsNull(java.util.Collection<Long> ids);
}
