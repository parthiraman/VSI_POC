package com.vsi.poc.gcs.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Cloud Storage configuration.
 *
 * <p>Authentication uses Application Default Credentials (ADC). In GCP environments
 * (GKE, Cloud Run, Compute Engine) the workload identity / service account attached
 * to the pod / VM is used automatically. For local development run:
 * {@code gcloud auth application-default login}
 *
 * <p>No signed URLs are generated; all operations go through the authenticated
 * Storage client, which relies on IAM permissions on the bucket.
 */
@Configuration
public class GcsConfig {

    @Value("${gcs.project-id}")
    private String projectId;

    /**
     * Creates a {@link Storage} bean backed by Application Default Credentials for
     * the configured GCP project. The bucket is located in {@code us-central1}.
     */
    @Bean
    public Storage storage() {
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }
}
