package com.wxn.chatbot.company.mapper;

import com.wxn.chatbot.company.dto.CompanyDtos;
import com.wxn.chatbot.company.entity.Company;

public final class CompanyMapper {

    private CompanyMapper() {}

    public static CompanyDtos.Response response(Company c) {
        return new CompanyDtos.Response(c.getId(), c.getName(), c.getCreatedAt());
    }
}
