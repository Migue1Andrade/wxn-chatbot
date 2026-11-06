package com.wxn.chatbot.websocket;

public final class StompDestinations {

    private StompDestinations() {}

    public static String conversations() {
        return "/topic/conversations";
    }

    public static boolean permitted(String destination) {
        return conversations().equals(destination);
    }
}
