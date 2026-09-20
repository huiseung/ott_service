package com.domain.backend.video.infrastructure.storage;

import java.net.URL;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface ObjectStorageClient {

    String createMultipartUpload(String objectKey, String contentType);

    PresignedUploadPart presignUploadPart(String objectKey, String uploadId, int partNumber);

    List<ListedPart> listParts(String objectKey, String uploadId);

    void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts);

    void abortMultipartUpload(String objectKey, String uploadId);

    ObjectMetadata headObject(String objectKey);

    void downloadObject(String objectKey, Path destination);

    void putObject(String objectKey, Path source, String contentType);

    List<String> listObjectKeys(String prefix);

    void deleteObjects(List<String> objectKeys);

    String readObjectAsString(String objectKey);

    byte[] readObjectBytes(String objectKey);

    StoredObject openObjectStream(String objectKey, String range);

    URL presignGetObject(String objectKey);

    record PresignedUploadPart(int partNumber, URL url, Instant expiresAt) {
    }

    record StoredObject(InputStream inputStream, long contentLength, String contentRange, boolean partial)
            implements AutoCloseable {

        @Override
        public void close() throws java.io.IOException {
            inputStream.close();
        }
    }
}
