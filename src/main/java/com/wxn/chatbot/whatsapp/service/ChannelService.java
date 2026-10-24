package com.wxn.chatbot.whatsapp.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.company.service.CompanyService;
import com.wxn.chatbot.whatsapp.dto.ChannelDtos;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.mapper.ChannelMapper;
import com.wxn.chatbot.whatsapp.repository.WhatsAppChannelRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChannelService {

    private final WhatsAppChannelRepository repository;
    private final CompanyService companies;

    public ChannelService(WhatsAppChannelRepository repository, CompanyService companies) {
        this.repository = repository;
        this.companies = companies;
    }

    public ChannelDtos.Response create(Long companyId, ChannelDtos.Create request) {
        companies.require(companyId);
        return ChannelMapper.response(
            repository.save(
                new WhatsAppChannel(
                    companyId,
                    request.name().trim(),
                    request.provider(),
                    PhoneNumbers.optional(request.phoneNumber()),
                    request.externalChannelId(),
                    request.status() == null ? ActiveStatus.ACTIVE : request.status()
                )
            )
        );
    }

    @Transactional(readOnly = true)
    public WhatsAppChannel require(Long companyId, Long id) {
        companies.require(companyId);
        return repository
            .findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
            .orElseThrow(() -> ApiException.notFound("Canal não encontrado na empresa"));
    }

    public WhatsAppChannel requireActive(Long companyId, Long id) {
        WhatsAppChannel channel = require(companyId, id);
        if (channel.getStatus() != ActiveStatus.ACTIVE) throw ApiException.conflict("Canal inativo");
        return channel;
    }

    @Transactional(readOnly = true)
    public List<ChannelDtos.Response> list(Long companyId) {
        companies.require(companyId);
        return repository
            .findByCompanyIdAndDeletedAtIsNullOrderById(companyId)
            .stream()
            .map(ChannelMapper::response)
            .toList();
    }
}
