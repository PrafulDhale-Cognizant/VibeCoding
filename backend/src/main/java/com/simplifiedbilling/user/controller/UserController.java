package com.simplifiedbilling.user.controller;

import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.user.dto.CreateUserRequest;
import com.simplifiedbilling.user.dto.ResetPasswordRequest;
import com.simplifiedbilling.user.dto.UpdateUserRequest;
import com.simplifiedbilling.user.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserSummary> listUsers() {
        return userManagementService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary createUser(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(
                jwt.getSubject(),
                isOwner(authentication),
                request);
    }

    @PatchMapping("/{userId}")
    public UserSummary updateUser(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return userManagementService.updateUser(
                jwt.getSubject(),
                isOwner(authentication),
                userId,
                request);
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
                @PathVariable String userId,
                @Valid @RequestBody ResetPasswordRequest request) {
            userManagementService.resetPassword(
                    jwt.getSubject(),
                    isOwner(authentication),
                    userId,
                    request);
        }

        private boolean isOwner(Authentication authentication) {
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        }
    }
