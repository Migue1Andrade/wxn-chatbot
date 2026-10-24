package com.wxn.chatbot.whatsapp.repository;

import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppChannelRepository extends JpaRepository<WhatsAppChannel, Long> {
    Optional<WhatsAppChannel> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);
    List<WhatsAppChannel> findByCompanyIdAndDeletedAtIsNullOrderById(Long companyId);
}
