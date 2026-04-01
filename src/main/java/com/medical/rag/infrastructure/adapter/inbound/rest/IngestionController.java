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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestão", description = "Upload e ingestão de documentos médicos")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionPort ingestionPort;
    private final StoragePort storagePort;
    private final Map<String, Map<String, Object>> batchStatus = new ConcurrentHashMap<>();

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
    @Operation(summary = "Ingestão em lote de PDFs do GCS (assíncrono)",
            description = "Lista todos os PDFs em um prefix do GCS e ingere em background. Use GET /api/ingest/batch/status/{id} para acompanhar.")
    public Map<String, Object> batchIngestFromGcs(
            @RequestParam String prefix,
            @RequestParam String source,
            @RequestParam String type,
            @RequestParam(defaultValue = "geral") String specialty) {

        List<String> files = storagePort.listFiles(prefix);
        String batchId = java.util.UUID.randomUUID().toString().substring(0, 8);

        var status = new ConcurrentHashMap<String, Object>();
        status.put("batchId", batchId);
        status.put("total", files.size());
        status.put("processed", new AtomicInteger(0));
        status.put("errors", new AtomicInteger(0));
        status.put("status", "PROCESSING");
        batchStatus.put(batchId, status);

        Thread.startVirtualThread(() -> {
            for (String filePath : files) {
                try {
                    var request = new IngestionRequest(filePath, source, type, specialty);
                    ingestionPort.ingest(request);
                    ((AtomicInteger) status.get("processed")).incrementAndGet();
                    log.info("[{}] Ingerido: {}", batchId, filePath);
                } catch (Exception e) {
                    ((AtomicInteger) status.get("errors")).incrementAndGet();
                    log.error("[{}] Erro ao ingerir {}: {}", batchId, filePath, e.getMessage());
                }
            }
            status.put("status", "COMPLETED");
            log.info("[{}] Batch concluido. Processados: {}, Erros: {}", batchId,
                    ((AtomicInteger) status.get("processed")).get(),
                    ((AtomicInteger) status.get("errors")).get());
        });

        return Map.of(
                "batchId", batchId,
                "total", files.size(),
                "status", "PROCESSING",
                "message", "Ingestão iniciada em background. Acompanhe em GET /api/ingest/batch/status/" + batchId
        );
    }

    @GetMapping("/batch/status/{batchId}")
    @Operation(summary = "Status da ingestão em lote")
    public Map<String, Object> batchStatus(@PathVariable String batchId) {
        var status = batchStatus.get(batchId);
        if (status == null) {
            return Map.of("error", "Batch não encontrado: " + batchId);
        }
        return Map.of(
                "batchId", batchId,
                "total", status.get("total"),
                "processed", ((AtomicInteger) status.get("processed")).get(),
                "errors", ((AtomicInteger) status.get("errors")).get(),
                "status", status.get("status")
        );
    }
}
