package com.stockflow.users.presentation;

import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.shared.domain.Page;
import com.stockflow.users.application.UserService;
import com.stockflow.users.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/users")
public class UserController {
    private final UserService service;
    private final BusinessRepository businesses;

    public UserController(UserService service, BusinessRepository businesses) {
        this.service = service;
        this.businesses = businesses;
    }

    public record CreateUserRequest(
            @NotNull UUID businessId,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 64) String temporaryPassword) {
        @Override
        public String toString() {
            return "CreateUserRequest[REDACTED]";
        }
    }

    public record EditUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String displayName) {}

    public record ResetPasswordRequest(@NotBlank @Size(max = 64) String temporaryPassword) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[REDACTED]";
        }
    }

    public record UserResponse(
            UUID id,
            UUID businessId,
            String businessName,
            String email,
            String displayName,
            String role,
            boolean active,
            boolean mustChangePassword,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        public static UserResponse of(Account account, String businessName) {
            return new UserResponse(
                    account.id(),
                    account.businessId(),
                    businessName,
                    account.email(),
                    account.displayName(),
                    account.role(),
                    account.active(),
                    account.mustChangePassword(),
                    account.lastLoginAt(),
                    account.createdAt(),
                    account.updatedAt());
        }
    }

    @GetMapping
    Page<UserResponse> list(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.list(businessId, active, search, page, size);
        return new Page<>(
                result.items().stream().map(this::response).toList(), result.total(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest input) {
        return response(
                service.create(
                        input.businessId(),
                        input.email(),
                        input.displayName(),
                        input.temporaryPassword()));
    }

    @PutMapping("/{id}")
    UserResponse edit(@PathVariable UUID id, @Valid @RequestBody EditUserRequest input) {
        return response(service.edit(id, input.email(), input.displayName()));
    }

    @PostMapping("/{id}/suspend")
    UserResponse suspend(@PathVariable UUID id) {
        return response(service.suspend(id));
    }

    @PostMapping("/{id}/reactivate")
    UserResponse reactivate(@PathVariable UUID id) {
        return response(service.reactivate(id));
    }

    @PostMapping("/{id}/reset-password")
    UserResponse resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest input) {
        return response(service.resetPassword(id, input.temporaryPassword()));
    }

    private UserResponse response(Account account) {
        String businessName =
                account.businessId() == null
                        ? null
                        : businesses.find(account.businessId()).orElseThrow().name();
        return UserResponse.of(account, businessName);
    }
}
