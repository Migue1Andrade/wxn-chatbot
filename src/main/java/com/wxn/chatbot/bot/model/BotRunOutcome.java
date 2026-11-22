package com.wxn.chatbot.bot.model;

public enum BotRunOutcome {
    /** O bot respondeu e a conversa continua com ele. */
    REPLIED,
    /** O bot respondeu e devolveu a conversa à fila, no setor escolhido. */
    HANDED_OFF,
    /** O bot falhou; o cliente recebeu a mensagem fixa e a conversa voltou à fila. */
    FALLBACK_HANDOFF,
    /** Um humano assumiu durante a chamada; a resposta não foi enviada. */
    DISCARDED,
    /** Nada pôde ser feito; ver o código de erro. */
    FAILED,
}
