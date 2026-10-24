package com.wxn.chatbot.member;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.member.entity.MemberType;
import com.wxn.chatbot.member.dto.MemberDtos;
import com.wxn.chatbot.company.service.CompanyService;
import com.wxn.chatbot.user.service.UserService;
import com.wxn.chatbot.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MemberServiceTest {
    @Test void rejectsForeignTenantUserWithoutPersistingMember() {
        var repository = mock(MemberRepository.class); var users = mock(UserService.class);
        when(users.require(1L, 10L)).thenThrow(ApiException.notFound("Usuário não encontrado na empresa"));
        var service = new MemberService(repository, mock(CompanyService.class), users);
        assertThatThrownBy(() -> service.create(1L, new MemberDtos.Create("Humano", MemberType.HUMAN, 10L, null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
    @Test void humanRequiresUser() {
        var repository = mock(MemberRepository.class);
        var service = new MemberService(repository, mock(CompanyService.class), mock(UserService.class));
        assertThatThrownBy(() -> service.create(1L, new MemberDtos.Create("Humano", MemberType.HUMAN, null, null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
}
