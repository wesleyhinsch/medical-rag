package com.medical.rag.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.medical.rag.domain.model.IngestionRequest;
import com.medical.rag.domain.model.MedicalDocument;
import com.medical.rag.domain.port.IngestionPort;
import com.medical.rag.domain.port.StoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestão", description = "Upload e ingestão de documentos médicos")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final IngestionPort ingestionPort;
    private final StoragePort storagePort;
    private final String projectId;
    private final String topicId;

    public IngestionController(
            IngestionPort ingestionPort,
            StoragePort storagePort,
            @Value("${pubsub.project}") String projectId,
            @Value("${pubsub.topic}") String topicId) {
        this.ingestionPort = ingestionPort;
        this.storagePort = storagePort;
        this.projectId = projectId;
        this.topicId = topicId;
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
        storagePort.upload(filePath, file.getBytes());
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
    @Operation(summary = "Ingestão em lote via Pub/Sub",
            description = "Lista PDFs no GCS e publica mensagens no Pub/Sub para processamento distribuído")
    public Map<String, Object> batchIngestFromGcs(
            @RequestParam String prefix,
            @RequestParam String source,
            @RequestParam String type,
            @RequestParam(defaultValue = "geral") String specialty) throws Exception {

        List<String> files = storagePort.listFiles(prefix);
        TopicName topicName = TopicName.of(projectId, topicId);
        Publisher publisher = Publisher.newBuilder(topicName).build();

        int published = 0;
        try {
            for (String filePath : files) {
                String json = mapper.writeValueAsString(Map.of(
                        "gcsFilePath", filePath,
                        "source", source,
                        "type", type,
                        "specialty", specialty
                ));

                PubsubMessage message = PubsubMessage.newBuilder()
                        .setData(ByteString.copyFromUtf8(json))
                        .build();

                ApiFuture<String> future = publisher.publish(message);
                future.get();
                published++;
            }
        } finally {
            publisher.shutdown();
        }

        log.info("Batch: {} mensagens publicadas no Pub/Sub", published);

        return Map.of(
                "total", files.size(),
                "published", published,
                "status", "PUBLISHED",
                "message", "Mensagens publicadas no Pub/Sub. Processamento distribuído iniciado."
        );
    }

    @PostMapping("/process")
    @Operation(summary = "Processa mensagem do Pub/Sub",
            description = "Endpoint chamado pelo Pub/Sub push subscription para processar 1 PDF")
    public Map<String, String> processPubSubMessage(@RequestBody JsonNode body) {
        try {
            String data = body.path("message").path("data").asText();
            String decoded = new String(Base64.getDecoder().decode(data));
            JsonNode payload = mapper.readTree(decoded);

            var request = new IngestionRequest(
                    payload.get("gcsFilePath").asText(),
                    payload.get("source").asText(),
                    payload.get("type").asText(),
                    payload.get("specialty").asText()
            );

            ingestionPort.ingest(request);
            log.info("Processado via Pub/Sub: {}", request.gcsFilePath());
            return Map.of("status", "OK");
        } catch (Exception e) {
            log.error("Erro ao processar mensagem Pub/Sub: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/clear")
    @Operation(summary = "Limpar vector store",
            description = "Remove todos os documentos do vector store")
    public Map<String, String> clearVectorStore() {
        ingestionPort.clearAll();
        log.info("Vector store limpo");
        return Map.of("status", "OK", "message", "Vector store limpo com sucesso");
    }

    @GetMapping("/stats")
    @Operation(summary = "Estatísticas do vector store",
            description = "Retorna total de documentos e chunks no vector store")
    public Map<String, Object> stats() {
        return ingestionPort.stats();
    }
}
