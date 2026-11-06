package com.wxn.chatbot.user.validation;

import com.wxn.chatbot.common.ApiException;

public final class Cpf {

    private Cpf() {}

    public static String normalize(String value) {
        if (value == null || !value.trim().matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")) {
            throw ApiException.badRequest("CPF inválido; informe 11 dígitos ou use 000.000.000-00");
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.chars().distinct().count() == 1) {
            throw ApiException.badRequest("CPF inválido; verifique os dígitos verificadores");
        }
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int index = 0; index < size; index++) {
                sum += (digits.charAt(index) - '0') * (size + 1 - index);
            }
            int remainder = sum % 11;
            int expected = remainder < 2 ? 0 : 11 - remainder;
            if (digits.charAt(size) - '0' != expected) {
                throw ApiException.badRequest("CPF inválido; verifique os dígitos verificadores");
            }
        }
        return digits;
    }
}
