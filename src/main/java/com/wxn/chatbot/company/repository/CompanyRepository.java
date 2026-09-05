package com.wxn.chatbot.company.repository;

import com.wxn.chatbot.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
