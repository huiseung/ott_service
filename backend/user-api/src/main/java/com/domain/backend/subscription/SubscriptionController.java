package com.domain.backend.subscription;

import com.domain.backend.user.application.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;
    public SubscriptionController(SubscriptionService service) { this.service = service; }
    public record CreateCheckout(@NotNull UUID requestId, @Positive Long contentId, UUID ctaEventId) {}
    @PostMapping("/checkouts")
    public SubscriptionService.Checkout checkout(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody CreateCheckout request) {
        return service.checkout(user.userId(), request.requestId(), request.contentId(), request.ctaEventId());
    }
    @GetMapping("/me")
    public SubscriptionService.Membership current(@AuthenticationPrincipal UserPrincipal user) { return service.membership(user.userId()); }
    @PostMapping("/cancel")
    public SubscriptionService.Membership cancel(@AuthenticationPrincipal UserPrincipal user) { return service.cancel(user.userId()); }
}
