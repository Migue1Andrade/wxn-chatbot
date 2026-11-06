package com.wxn.chatbot.contact;

import com.wxn.chatbot.common.PhoneNumbers;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class ContactRegistry {

    private final ExternalContactRepository repository;

    public ContactRegistry(ExternalContactRepository repository) {
        this.repository = repository;
    }

    public ObjectId resolve(Long channelId, String phone) {
        return repository.resolve(channelId, PhoneNumbers.required(phone));
    }

    public void observe(ObjectId id, String name) {
        if (id != null && name != null && !name.isBlank()) {
            repository.observe(id, name.trim().substring(0, Math.min(200, name.trim().length())));
        }
    }
}
