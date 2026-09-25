package com.domain.backend.user.presentation;

import com.domain.backend.user.application.PublicContentQueryService;
import com.domain.backend.user.application.PublicContentQueryService.ContentPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/contents")
public class PublicContentController {
    private final PublicContentQueryService service;

    public PublicContentController(PublicContentQueryService service) { this.service = service; }

    @GetMapping
    public ContentPage list(@RequestParam(required = false) Long cursor,
                            @RequestParam(required = false) Integer size) {
        return service.list(cursor, size);
    }
}
