package com.wxn.chatbot.department.repository;

import com.wxn.chatbot.department.entity.Department;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByDeletedAtIsNullOrderByName();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Department> findByIdAndDeletedAtIsNull(Long id);
}
