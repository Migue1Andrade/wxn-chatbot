package com.wxn.chatbot.member.mapper;

import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.entity.Member;

public final class MemberMapper {

    private MemberMapper() {}

    public static MemberDtos.Response response(Member m) {
        return new MemberDtos.Response(
            m.getId(),
            m.getCompanyId(),
            m.getUserId(),
            m.getName(),
            m.getType(),
            m.getStatus()
        );
    }
}
