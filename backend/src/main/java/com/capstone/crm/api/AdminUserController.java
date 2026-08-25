package com.capstone.crm.api;

import com.capstone.crm.api.dto.CreateUserRequest;
import com.capstone.crm.api.dto.UpdateUserRequest;
import com.capstone.crm.api.dto.UserResponse;
import com.capstone.crm.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService userService;

    public AdminUserController(AdminUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.list());
    }

    // Declared before the /{userId} mapping so "pending" is not swallowed as an
    // id — that would fail type conversion rather than reaching this method.
    @GetMapping("/pending")
    public ResponseEntity<List<UserResponse>> listPending() {
        return ResponseEntity.ok(userService.listPending());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> get(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.get(userId));
    }

    /**
     * Approves a pending account (issue #16). PATCH rather than PUT: it changes
     * one flag and needs no body, where the full update contract requires email,
     * role and enabled together.
     */
    @PatchMapping("/{userId}/enable")
    public ResponseEntity<UserResponse> enable(
            @PathVariable Long userId,
            Authentication authentication) {
        return ResponseEntity.ok(userService.enable(userId, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                userService.update(userId, request, authentication.getName())
        );
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long userId,
            Authentication authentication) {
        userService.delete(userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}