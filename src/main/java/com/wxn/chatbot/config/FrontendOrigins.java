package com.wxn.chatbot.config;

import java.util.*;

public final class FrontendOrigins {

    private FrontendOrigins() {}

    public static boolean allowed(String configured, String supplied) {
        if (supplied == null || supplied.isBlank() || Objects.equals(configured, supplied)) {
            return supplied == null || supplied.isBlank() || Objects.equals(configured, supplied);
        }
        if (!isLocal(configured)) {
            return false;
        }
        return supplied.matches("https?://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?");
    }

    public static String[] patterns(String configured) {
        if (!isLocal(configured)) {
            return new String[] { configured };
        }
        return new String[] { configured, "http://localhost:*", "http://127.0.0.1:*" };
    }

    private static boolean isLocal(String origin) {
        return (
            origin != null && (origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1"))
        );
    }
}
