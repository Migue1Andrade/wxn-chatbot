package com.wxn.chatbot.whatsapp.provider;

public class ProviderSendException extends RuntimeException {

    private final String code;

    public ProviderSendException(String code) {
        super("Falha no envio ao provedor");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
