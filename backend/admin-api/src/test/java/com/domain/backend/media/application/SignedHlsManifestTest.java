package com.domain.backend.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domain.backend.video.infrastructure.storage.ObjectStorageClient;
import com.domain.backend.video.infrastructure.storage.SignedHlsManifest;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SignedHlsManifestTest {

    @Test
    void signsSegmentsButKeepsVariantOnAuthenticatedApi() throws Exception {
        var storage = mock(ObjectStorageClient.class);
        when(storage.presignGetObject("packages/1/720p/segment000050.ts"))
                .thenReturn(URI.create("https://media.example/segment000050.ts?signature=short-lived").toURL());

        String master = SignedHlsManifest.rewrite("#EXTM3U\n720p/index.m3u8\n", "master.m3u8",
                "packages/1", storage, path -> "/api/admin/preview/hls/" + path);
        String variant = SignedHlsManifest.rewrite("#EXTM3U\n#EXTINF:4,\nsegment000050.ts\n",
                "720p/index.m3u8", "packages/1", storage, path -> "/api/admin/preview/hls/" + path);

        assertThat(master).contains("/api/admin/preview/hls/720p/index.m3u8");
        assertThat(variant).contains("https://media.example/segment000050.ts?signature=short-lived");
        verify(storage).presignGetObject("packages/1/720p/segment000050.ts");
    }

    @Test
    void rejectsExternalAndTraversalUris() {
        var storage = mock(ObjectStorageClient.class);
        assertThatThrownBy(() -> SignedHlsManifest.rewrite("#EXTM3U\nhttps://evil.example/a.ts\n",
                "720p/index.m3u8", "packages/1", storage, path -> path))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SignedHlsManifest.rewrite("#EXTM3U\n../source.mp4\n",
                "720p/index.m3u8", "packages/1", storage, path -> path))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
