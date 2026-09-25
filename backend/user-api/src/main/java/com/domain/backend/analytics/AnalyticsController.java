package com.domain.backend.analytics;

import com.domain.backend.user.application.UserPrincipal;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics/events")
public class AnalyticsController {
    private final AnalyticsCollector collector;

    public AnalyticsController(AnalyticsCollector collector) { this.collector = collector; }

    @PostMapping(value = "/batch", consumes = "application/json")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<AnalyticsDtos.Accepted> collect(@Valid @RequestBody AnalyticsDtos.Batch batch,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return collector.collect(batch, principal);
    }
}
