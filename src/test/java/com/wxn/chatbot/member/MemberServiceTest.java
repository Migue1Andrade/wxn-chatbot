package com.wxn.chatbot.member;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.member.entity.MemberType;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.user.service.UserService;
import org.junit.jupiter.api.Test;

class MemberServiceTest {

    @Test
    void humanRequiresCompleteUserRegistration() {
        var repository = mock(MemberRepository.class);
        var service = new MemberService(repository, mock(UserService.class));
        assertThatThrownBy(() ->
            service.create(new MemberDtos.Create("Humano", MemberType.HUMAN, null, null, null, null))
        ).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
}
