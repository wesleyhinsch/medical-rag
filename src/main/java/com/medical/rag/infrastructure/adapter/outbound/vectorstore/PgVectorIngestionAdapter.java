package com.medical.rag.infrastructure.adapter.outbound.vectorstore;

import com.medical.rag.domain.model.IngestionRequest;
import com.medical.rag.domain.model.MedicalDocument;
import com.medical.rag.domain.port.IngestionPort;
import com.medical.rag.domain.port.StoragePort;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class PgVectorIngestionAdapter implements IngestionPort {

    private final VectorStore vectorStore;
    private final StoragePort storagePort;
    private final int chunkSize;
    private final int chunkOverlap;

    public PgVectorIngestionAdapter(
            VectorStore vectorStore,
            StoragePort storagePort,
            @Value("${ingestion.chunk-size}") int chunkSize,
            @Value("${ingestion.chunk-overlap}") int chunkOverlap) {
        this.vectorStore = vectorStore;
        this.storagePort = storagePort;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public MedicalDocument ingest(IngestionRequest request) {
        var inputStream = storagePort.download(request.gcsFilePath());
        var resource = new InputStreamResource(inputStream);

        // 1. Lê o PDF
        var reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.get();

        // 2. Chunking
        var splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
        List<Document> chunks = splitter.apply(pages);

        // 3. Adiciona metadados em cada chunk
        var doc = new MedicalDocument(
                UUID.randomUUID().toString(),
                request.gcsFilePath(),
                request.source(),
                request.type(),
                request.specialty(),
                LocalDateTime.now(),
                chunks.size()
        );

        chunks.forEach(chunk -> chunk.getMetadata().putAll(doc.toMetadata()));

        // 4. Embedding + armazena no pgvector (Spring AI faz automaticamente)
        vectorStore.add(chunks);

        return doc;
    }
}
