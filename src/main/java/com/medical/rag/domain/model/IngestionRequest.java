package com.medical.rag.domain.model;

public record IngestionRequest(
        String gcsFilePath,
        String source,
        String type,
        String specialty
) {}
