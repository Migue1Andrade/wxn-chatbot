package com.wxn.chatbot.bot.client;

/** O serviço do bot não respondeu, ou respondeu fora do contrato. */
public class BotUnavailableException extends RuntimeException {

    private final String code;

    public BotUnavailableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
