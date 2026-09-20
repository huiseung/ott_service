package com.domain.backend.video.infrastructure.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    @NotBlank
    private String bucket;

    @NotBlank
    private String region = "us-east-1";

    private URI endpoint;

    private URI publicEndpoint;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    private boolean pathStyleAccess = true;

    @NotNull
    private Duration presignedUrlTtl = Duration.ofMinutes(15);

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize minimumPartSize = DataSize.ofMegabytes(16);

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maximumPartSize = DataSize.ofMegabytes(64);

    @DataSizeUnit(DataUnit.GIGABYTES)
    private DataSize maximumFileSize = DataSize.ofGigabytes(500);

    @Min(1)
    private int maximumRequestedPresignedUrls = 100;

    @NotNull
    private Duration incompleteUploadExpiration = Duration.ofDays(7);

    @NotNull
    private Duration reconciliationInterval = Duration.ofMinutes(5);

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

    public URI getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(URI publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public Duration getPresignedUrlTtl() {
        return presignedUrlTtl;
    }

    public void setPresignedUrlTtl(Duration presignedUrlTtl) {
        this.presignedUrlTtl = presignedUrlTtl;
    }

    public DataSize getMinimumPartSize() {
        return minimumPartSize;
    }

    public void setMinimumPartSize(DataSize minimumPartSize) {
        this.minimumPartSize = minimumPartSize;
    }

    public DataSize getMaximumPartSize() {
        return maximumPartSize;
    }

    public void setMaximumPartSize(DataSize maximumPartSize) {
        this.maximumPartSize = maximumPartSize;
    }

    public DataSize getMaximumFileSize() {
        return maximumFileSize;
    }

    public void setMaximumFileSize(DataSize maximumFileSize) {
        this.maximumFileSize = maximumFileSize;
    }

    public int getMaximumRequestedPresignedUrls() {
        return maximumRequestedPresignedUrls;
    }

    public void setMaximumRequestedPresignedUrls(int maximumRequestedPresignedUrls) {
        this.maximumRequestedPresignedUrls = maximumRequestedPresignedUrls;
    }

    public Duration getIncompleteUploadExpiration() {
        return incompleteUploadExpiration;
    }

    public void setIncompleteUploadExpiration(Duration incompleteUploadExpiration) {
        this.incompleteUploadExpiration = incompleteUploadExpiration;
    }

    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }
}
