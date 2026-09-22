package com.domain.backend.playbackworker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WatchHistoryBatchWriterTest {

    @Test
    void updatesPlaybackSessionIdAfterColumnsThatCompareAgainstExistingSession() {
        String sql = WatchHistoryBatchWriter.upsertSql();

        assertThat(sql.indexOf("updated_at = if("))
                .isLessThan(sql.lastIndexOf("playback_session_id = if("));
    }
}
