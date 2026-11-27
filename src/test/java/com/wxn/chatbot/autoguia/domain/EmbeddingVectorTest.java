package com.wxn.chatbot.autoguia.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {

    @Test
    void acceptsFiniteNonZeroVectorWithDeclaredDimension() {
        assertDoesNotThrow(() -> new EmbeddingVector("test", "deterministic-v1", 2, List.of(0.5, -0.5)));
    }

    @Test
    void rejectsWrongDimension() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test", "deterministic-v1", 2, List.of(1.0))
        );
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test", "deterministic-v1", 1, List.of(Double.NaN))
        );
    }

    @Test
    void rejectsZeroVector() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test", "deterministic-v1", 2, List.of(0.0, 0.0))
        );
    }

    @Test
    void rejectsValuesThatUnderflowToZeroInPgvectorFloat32() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test", "deterministic-v1", 1, List.of(1.0e-50))
        );
    }

    @Test
    void rejectsValuesOutsidePgvectorFloat32Range() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test", "deterministic-v1", 1, List.of(1.0e40))
        );
    }

    @Test
    void rejectsUnstableProviderOrModelIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
            new EmbeddingVector("test provider", "deterministic-v1", 1, List.of(1.0))
        );
    }
}
