package com.wxn.chatbot.member.mapper;

import com.wxn.chatbot.department.mapper.DepartmentMapper;
import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.entity.Member;

public final class MemberMapper {

    private MemberMapper() {}

    public static MemberDtos.Response response(
        Member m,
        String phone,
        com.wxn.chatbot.common.ActiveStatus accountStatus
    ) {
        return new MemberDtos.Response(
            m.getId(),
            m.getUserId(),
            m.getName(),
            m.getType(),
            m.getStatus(),
            phone,
            accountStatus,
            m.getDepartments().stream().map(DepartmentMapper::summary).toList()
        );
    }
}
