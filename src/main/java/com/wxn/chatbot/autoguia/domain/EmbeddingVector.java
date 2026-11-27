package com.wxn.chatbot.autoguia.domain;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated embedding values and their stable provider/model identity. */
public record EmbeddingVector(String provider, String model, int dimension, List<Double> values) {
    private static final Pattern IDENTITY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,159}");

    public EmbeddingVector {
        if (!validIdentity(provider) || !validIdentity(model)) {
            throw new IllegalArgumentException("Embedding provider and model must be stable identifiers");
        }
        if (dimension <= 0 || values == null || values.size() != dimension) {
            throw new IllegalArgumentException("Embedding dimension does not match its values");
        }
        boolean nonZero = false;
        for (Double value : values) {
            Objects.requireNonNull(value, "Embedding values cannot be null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Embedding values must be finite");
            }
            float storedValue = value.floatValue();
            if (!Float.isFinite(storedValue)) {
                throw new IllegalArgumentException("Embedding values must fit pgvector float32 storage");
            }
            nonZero |= storedValue != 0.0f;
        }
        if (!nonZero) {
            throw new IllegalArgumentException("Embedding vector must remain non-zero in pgvector storage");
        }
        values = List.copyOf(values);
    }

    private static boolean validIdentity(String value) {
        return value != null && IDENTITY_PATTERN.matcher(value).matches();
    }
}
