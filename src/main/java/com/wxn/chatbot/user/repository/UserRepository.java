package com.wxn.chatbot.user.repository;

import com.wxn.chatbot.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
