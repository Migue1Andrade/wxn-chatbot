package com.wxn.chatbot.whatsapp.repository;

import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppChannelRepository extends JpaRepository<WhatsAppChannel, Long> {
    Optional<WhatsAppChannel> findByIdAndDeletedAtIsNull(Long id);
    Optional<WhatsAppChannel> findFirstByProviderAndDeletedAtIsNullOrderById(
        com.wxn.chatbot.whatsapp.model.ProviderType provider
    );
    List<WhatsAppChannel> findByDeletedAtIsNullOrderById();
}
