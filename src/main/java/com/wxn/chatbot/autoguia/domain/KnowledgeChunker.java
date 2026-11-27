package com.wxn.chatbot.autoguia.domain;

import java.util.ArrayList;
import java.util.List;

public final class KnowledgeChunker {

    private KnowledgeChunker() {}

    public static List<String> split(String text, int maxCodePoints) {
        if (maxCodePoints <= 0) {
            throw new IllegalArgumentException("Maximum chunk size must be positive");
        }
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.codePointCount(0, trimmed.length()) > maxCodePoints) {
                flush(current, chunks);
                splitLong(trimmed, maxCodePoints, chunks);
            } else {
                int currentCount = current.codePointCount(0, current.length());
                int addedCount = trimmed.codePointCount(0, trimmed.length()) + (currentCount == 0 ? 0 : 2);
                if (currentCount > 0 && currentCount + addedCount > maxCodePoints) {
                    flush(current, chunks);
                }
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(trimmed);
            }
        }
        flush(current, chunks);
        return List.copyOf(chunks);
    }

    private static void splitLong(String text, int maxCodePoints, List<String> chunks) {
        int start = 0;
        int total = text.codePointCount(0, text.length());
        while (start < total) {
            int end = Math.min(start + maxCodePoints, total);
            int startChar = text.offsetByCodePoints(0, start);
            int endChar = text.offsetByCodePoints(startChar, end - start);
            chunks.add(text.substring(startChar, endChar));
            start = end;
        }
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (current.length() > 0) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
