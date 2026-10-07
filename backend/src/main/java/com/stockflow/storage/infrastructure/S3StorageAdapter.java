package com.stockflow.storage.infrastructure;

import com.stockflow.shared.domain.BusinessException;
import com.stockflow.storage.domain.StorageAdapter;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3StorageAdapter implements StorageAdapter {
    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageAdapter(
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.region}") String region) {
        this.bucket = bucket;
        this.client =
                bucket.isBlank() ? null : S3Client.builder().region(Region.of(region)).build();
        this.presigner =
                bucket.isBlank() ? null : S3Presigner.builder().region(Region.of(region)).build();
    }

    public void put(String key, byte[] bytes, String contentType) {
        requireConfigured();
        client.putObject(
                request ->
                        request.bucket(bucket)
                                .key(key)
                                .contentType(contentType)
                                .serverSideEncryption("AES256"),
                RequestBody.fromBytes(bytes));
    }

    public URI signedRead(String key) {
        requireConfigured();
        return URI.create(
                presigner
                        .presignGetObject(
                                request ->
                                        request.signatureDuration(Duration.ofMinutes(5))
                                                .getObjectRequest(
                                                        object -> object.bucket(bucket).key(key)))
                        .url()
                        .toString());
    }

    private void requireConfigured() {
        if (bucket.isBlank()) throw new BusinessException("STORAGE_NOT_CONFIGURED", 503);
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
        if (presigner != null) presigner.close();
    }
}
