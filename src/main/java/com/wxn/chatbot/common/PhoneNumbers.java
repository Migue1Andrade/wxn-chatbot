package com.wxn.chatbot.common;

public final class PhoneNumbers {

    private PhoneNumbers() {}

    public static String optional(String value) {
        return value == null || value.isBlank() ? null : required(value);
    }

    public static String required(String value) {
        if (value == null) {
            throw ApiException.badRequest("Telefone obrigatório em E.164");
        }
        String phone = value.trim().replaceFirst("^whatsapp:", "").replaceAll("[ ()-]", "");
        if (!phone.matches("^[+][1-9][0-9]{7,14}$")) {
            throw ApiException.badRequest("Telefone inválido; informe código do país e número em E.164");
        }
        return phone;
    }
}
