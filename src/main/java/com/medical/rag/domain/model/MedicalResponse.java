package com.medical.rag.domain.model;

import java.util.List;

public record MedicalResponse(
        String answer,
        List<String> sources,
        String disclaimer
) {
    private static final String DEFAULT_DISCLAIMER =
            "⚠️ Informação de referência. Não substitui avaliação médica profissional.";

    public MedicalResponse(String answer, List<String> sources) {
        this(answer, sources, DEFAULT_DISCLAIMER);
    }
}
