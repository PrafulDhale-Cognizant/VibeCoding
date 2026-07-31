package com.simplifiedbilling.system.controller;

import com.simplifiedbilling.system.dto.SystemHealthResponse;
import com.simplifiedbilling.system.service.SystemHealthService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    private final SystemHealthService healthService;

    public SystemHealthController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> getHealth() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(healthService.getHealth());
    }
}
