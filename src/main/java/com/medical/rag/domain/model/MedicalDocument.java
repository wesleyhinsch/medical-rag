package com.medical.rag.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

public record MedicalDocument(
        String id,
        String fileName,
        String source,
        String type,
        String specialty,
        LocalDateTime ingestedAt,
        int totalChunks
) {
    public Map<String, Object> toMetadata() {
        return Map.of(
                "source", source,
                "type", type,
                "specialty", specialty,
                "fileName", fileName
        );
    }
}
