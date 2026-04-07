package com.medical.rag.domain.port;

import com.medical.rag.domain.model.MedicalResponse;

public interface QueryPort {

    MedicalResponse query(String question, String specialty);

    MedicalResponse query(String question, String specialty, String sessionId);
}
