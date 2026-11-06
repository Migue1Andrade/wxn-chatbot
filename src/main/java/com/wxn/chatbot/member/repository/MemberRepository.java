package com.wxn.chatbot.member.repository;

import com.wxn.chatbot.member.entity.Member;
import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @org.springframework.data.jpa.repository.Query(
        "select count(distinct m.id) from Member m join m.departments d where d.id = :departmentId and m.deletedAt is null"
    )
    long countDepartment(Long departmentId);

    interface DepartmentCount {
        Long getDepartmentId();
        Long getTotal();
    }

    @org.springframework.data.jpa.repository.Query(
        "select d.id as departmentId, count(distinct m.id) as total from Member m join m.departments d where m.deletedAt is null group by d.id"
    )
    List<DepartmentCount> countDepartments();

    @org.springframework.data.jpa.repository.Query(
        "select m from Member m join m.departments d where d.id = :departmentId and m.deletedAt is null order by m.name"
    )
    List<Member> departmentMembers(Long departmentId);

    @EntityGraph(attributePaths = "departments")
    List<Member> findByUserIdAndTypeAndDeletedAtIsNullOrderById(
        Long userId,
        com.wxn.chatbot.member.entity.MemberType type
    );

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = "departments")
    List<Member> findByDeletedAtIsNullOrderById();

    @EntityGraph(attributePaths = "departments")
    @Query(
        "select distinct m from Member m where m.type = com.wxn.chatbot.member.entity.MemberType.HUMAN and m.deletedAt is null order by m.id"
    )
    List<Member> directoryHumans();
}
