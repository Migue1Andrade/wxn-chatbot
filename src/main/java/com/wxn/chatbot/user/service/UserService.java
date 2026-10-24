package com.wxn.chatbot.user.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.company.service.CompanyService;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.User;
import com.wxn.chatbot.user.mapper.UserMapper;
import com.wxn.chatbot.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository repository;
    private final CompanyService companies;

    public UserService(UserRepository repository, CompanyService companies) {
        this.repository = repository;
        this.companies = companies;
    }

    public UserDtos.Response create(Long companyId, UserDtos.Create request) {
        return UserMapper.response(
            repository.save(
                new User(
                    companies.require(companyId),
                    request.name().trim(),
                    PhoneNumbers.optional(request.phone())
                )
            )
        );
    }

    @Transactional(readOnly = true)
    public User require(Long companyId, Long id) {
        companies.require(companyId);
        return repository
            .findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
            .orElseThrow(() -> ApiException.notFound("Usuário não encontrado na empresa"));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.Response> list(Long companyId) {
        companies.require(companyId);
        return repository
            .findByCompanyIdAndDeletedAtIsNullOrderById(companyId)
            .stream()
            .map(UserMapper::response)
            .toList();
    }
}
