package com.wxn.chatbot.whatsapp.model;

public record ProviderCapabilities(
    boolean individualText,
    boolean groups,
    boolean media,
    boolean statusCallbacks,
    boolean simulated
) {}
