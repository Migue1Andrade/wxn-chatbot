package com.wxn.chatbot.user.mapper;

import com.wxn.chatbot.department.mapper.DepartmentMapper;
import com.wxn.chatbot.member.entity.Member;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.User;
import java.util.List;

public final class UserMapper {

    private UserMapper() {}

    public static UserDtos.Response response(User user, Member member) {
        return new UserDtos.Response(
            user.getId(),
            user.getName(),
            user.getUsername(),
            user.getEmail(),
            user.getCpf(),
            user.getPhone(),
            user.getRole(),
            user.getAccountStatus(),
            member == null ? null : member.getId(),
            member == null
                ? List.of()
                : member.getDepartments().stream().map(DepartmentMapper::summary).toList(),
            user.isRegistrationIncomplete(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
