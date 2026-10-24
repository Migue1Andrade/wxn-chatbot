package com.wxn.chatbot.websocket;

public final class StompDestinations {

    private StompDestinations() {}

    public static String conversations(long companyId) {
        return "/topic/companies/" + companyId + "/conversations";
    }

    public static boolean permitted(String destination, long companyId) {
        return conversations(companyId).equals(destination);
    }
}
