package com.wxn.chatbot.member.repository;

import com.wxn.chatbot.member.entity.Member;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);
    List<Member> findByCompanyIdAndDeletedAtIsNullOrderById(Long companyId);
}
