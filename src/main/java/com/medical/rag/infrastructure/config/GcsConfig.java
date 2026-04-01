package com.medical.rag.infrastructure.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Bean
    public Storage googleCloudStorage(@Value("${GCP_PROJECT_ID:}") String projectId) {
        var builder = StorageOptions.newBuilder();
        if (!projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        return builder.build().getService();
    }
}
