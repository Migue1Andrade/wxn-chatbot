package com.wxn.chatbot.company.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.company.dto.CompanyDtos;
import com.wxn.chatbot.company.entity.Company;
import com.wxn.chatbot.company.mapper.CompanyMapper;
import com.wxn.chatbot.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    public CompanyDtos.Response create(CompanyDtos.Create request) {
        return CompanyMapper.response(repository.save(new Company(request.name().trim())));
    }

    @Transactional(readOnly = true)
    public Company require(Long id) {
        return repository
            .findById(id)
            .filter(c -> c.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Empresa não encontrada"));
    }

    public CompanyDtos.Response get(Long id) {
        return CompanyMapper.response(require(id));
    }
}
