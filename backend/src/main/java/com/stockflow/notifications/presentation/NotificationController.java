package com.stockflow.notifications.presentation;

import com.stockflow.notifications.application.NotificationService;
import com.stockflow.notifications.domain.Notification;
import com.stockflow.shared.domain.Page;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    Page<Notification> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void read(@PathVariable UUID id) {
        service.read(id);
    }
}
