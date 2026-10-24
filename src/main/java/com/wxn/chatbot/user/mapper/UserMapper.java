package com.wxn.chatbot.user.mapper;

import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserDtos.Response response(User u) {
        return new UserDtos.Response(u.getId(), u.getCompany().getId(), u.getName(), u.getPhone());
    }
}
