package com.medical.rag.infrastructure.adapter.inbound.rest;

import com.medical.rag.domain.model.IngestionRequest;
import com.medical.rag.domain.model.MedicalDocument;
import com.medical.rag.domain.port.IngestionPort;
import com.medical.rag.domain.port.StoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestão", description = "Upload e ingestão de documentos médicos")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionPort ingestionPort;
    private final StoragePort storagePort;

    public IngestionController(IngestionPort ingestionPort, StoragePort storagePort) {
        this.ingestionPort = ingestionPort;
        this.storagePort = storagePort;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload + ingestão de PDF",
            description = "Faz upload do PDF para o GCS e ingere no vector store")
    public MedicalDocument uploadAndIngest(
            @RequestParam MultipartFile file,
            @RequestParam String source,
            @RequestParam String type,
            @RequestParam(defaultValue = "geral") String specialty) throws Exception {

        String filePath = "documents/%s/%s".formatted(type, file.getOriginalFilename());

        // 1. Upload para GCS
        storagePort.upload(filePath, file.getBytes());

        // 2. Ingestão (chunking + embedding + pgvector)
        var request = new IngestionRequest(filePath, source, type, specialty);
        return ingestionPort.ingest(request);
    }

    @PostMapping("/gcs")
    @Operation(summary = "Ingerir PDF já existente no GCS",
            description = "Ingere um documento que já está no Google Cloud Storage")
    public MedicalDocument ingestFromGcs(@RequestBody IngestionRequest request) {
        return ingestionPort.ingest(request);
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingestão em lote de PDFs do GCS",
            description = "Lista todos os PDFs em um prefix do GCS e ingere todos no vector store")
    public Map<String, Object> batchIngestFromGcs(
            @RequestParam String prefix,
            @RequestParam String source,
            @RequestParam String type,
            @RequestParam(defaultValue = "geral") String specialty) {

        List<String> files = storagePort.listFiles(prefix);
        List<MedicalDocument> ingested = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String filePath : files) {
            try {
                var request = new IngestionRequest(filePath, source, type, specialty);
                ingested.add(ingestionPort.ingest(request));
                log.info("Ingerido: {}", filePath);
            } catch (Exception e) {
                log.error("Erro ao ingerir {}: {}", filePath, e.getMessage());
                errors.add("%s: %s".formatted(filePath, e.getMessage()));
            }
        }

        return Map.of(
                "total", files.size(),
                "ingested", ingested,
                "errors", errors
        );
    }
}
