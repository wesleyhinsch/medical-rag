package com.medical.rag.infrastructure.adapter.outbound.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.medical.rag.domain.port.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class GcsStorageAdapter implements StoragePort {

    private final Storage storage;
    private final String bucket;

    public GcsStorageAdapter(Storage storage, @Value("${gcs.bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public InputStream download(String filePath) {
        Blob blob = storage.get(BlobId.of(bucket, filePath));
        if (blob == null) {
            throw new RuntimeException("Arquivo não encontrado: " + filePath);
        }
        return new ByteArrayInputStream(blob.getContent());
    }

    @Override
    public void upload(String filePath, byte[] content) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, filePath)).build();
        storage.create(blobInfo, content);
    }

    @Override
    public List<String> listFiles(String prefix) {
        var blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix));
        List<String> files = new ArrayList<>();
        blobs.iterateAll().forEach(blob -> {
            if (blob.getName().toLowerCase().endsWith(".pdf")) {
                files.add(blob.getName());
            }
        });
        return files;
    }
}
