package com.wxn.chatbot.whatsapp.service;

import com.wxn.chatbot.common.*;
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

    public ChannelService(WhatsAppChannelRepository repository) {
        this.repository = repository;
    }

    public ChannelDtos.Response create(ChannelDtos.Create request) {
        return ChannelMapper.response(
            repository.save(
                new WhatsAppChannel(
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
    public WhatsAppChannel require(Long id) {
        return repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Canal não encontrado"));
    }

    public WhatsAppChannel requireActive(Long id) {
        WhatsAppChannel channel = require(id);
        if (channel.getStatus() != ActiveStatus.ACTIVE) {
            throw ApiException.conflict("Canal inativo");
        }
        return channel;
    }

    @Transactional(readOnly = true)
    public WhatsAppChannel firstActive(com.wxn.chatbot.whatsapp.model.ProviderType provider) {
        return repository
            .findFirstByProviderAndDeletedAtIsNullOrderById(provider)
            .filter(channel -> channel.getStatus() == ActiveStatus.ACTIVE)
            .orElse(null);
    }

    public void updatePhone(WhatsAppChannel channel, String phoneNumber) {
        channel.updatePhoneNumber(PhoneNumbers.optional(phoneNumber));
        repository.save(channel);
    }

    @Transactional(readOnly = true)
    public List<ChannelDtos.Response> list() {
        return repository.findByDeletedAtIsNullOrderById().stream().map(ChannelMapper::response).toList();
    }
}
