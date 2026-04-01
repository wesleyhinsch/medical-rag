package com.medical.rag.domain.port;

import com.medical.rag.domain.model.IngestionRequest;
import com.medical.rag.domain.model.MedicalDocument;

public interface IngestionPort {

    MedicalDocument ingest(IngestionRequest request);
}
