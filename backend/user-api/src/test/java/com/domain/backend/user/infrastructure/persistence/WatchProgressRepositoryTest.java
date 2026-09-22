package com.domain.backend.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class WatchProgressRepositoryTest {

    @Test
    void updatesPlaybackSessionIdAfterColumnsThatCompareAgainstExistingSession() throws Exception {
        var method = WatchProgressRepository.class.getMethod(
                "upsertProgress",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                long.class,
                long.class,
                long.class,
                Instant.class,
                Instant.class
        );
        String sql = method.getAnnotation(Query.class).value();

        assertThat(sql.indexOf("updated_at = if("))
                .isLessThan(sql.lastIndexOf("playback_session_id = if("));
    }
}
