package com.domain.backend.subscription;

import com.domain.backend.user.application.UserPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** No payment has taken place. The adapter does not exist unless explicitly enabled locally. */
@RestController
@RequestMapping("/api/user/subscriptions/local")
@ConditionalOnProperty(name = "app.subscription.local-activation-enabled", havingValue = "true")
public class LocalSubscriptionController {
    private final SubscriptionService service;
    public LocalSubscriptionController(SubscriptionService service) { this.service = service; }
    @PostMapping("/checkouts/{checkoutId}/activate")
    public SubscriptionService.Membership activate(@AuthenticationPrincipal UserPrincipal user, @PathVariable UUID checkoutId) {
        return service.activateLocal(user.userId(), checkoutId);
    }
}
