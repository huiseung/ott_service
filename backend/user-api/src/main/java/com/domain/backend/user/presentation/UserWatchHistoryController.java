package com.domain.backend.user.presentation;

import com.domain.backend.user.application.UserPrincipal;
import com.domain.backend.user.application.UserWatchHistoryDtos.WatchHistoryResponse;
import com.domain.backend.user.application.UserWatchHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/me/watch-history")
public class UserWatchHistoryController {

    private final UserWatchHistoryService watchHistoryService;

    public UserWatchHistoryController(UserWatchHistoryService watchHistoryService) {
        this.watchHistoryService = watchHistoryService;
    }

    @GetMapping
    public WatchHistoryResponse list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer size
    ) {
        return watchHistoryService.list(principal, size);
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long videoId) {
        watchHistoryService.delete(principal, videoId);
    }
}
