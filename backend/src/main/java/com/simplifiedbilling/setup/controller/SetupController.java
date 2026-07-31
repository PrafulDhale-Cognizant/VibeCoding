package com.simplifiedbilling.setup.controller;

import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.setup.dto.InitialSetupRequest;
import com.simplifiedbilling.setup.dto.SetupStatusResponse;
import com.simplifiedbilling.setup.service.SetupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupStatusResponse getStatus() {
        return setupService.getStatus();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse initialize(@Valid @RequestBody InitialSetupRequest request) {
        return setupService.initialize(request);
    }
}
