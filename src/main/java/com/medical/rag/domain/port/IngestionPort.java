package com.medical.rag.domain.port;

import com.medical.rag.domain.model.IngestionRequest;
import com.medical.rag.domain.model.MedicalDocument;

import java.util.Map;

public interface IngestionPort {

    MedicalDocument ingest(IngestionRequest request);

    void clearAll();

    boolean isAlreadyIngested(String filePath);

    Map<String, Object> stats();
}
