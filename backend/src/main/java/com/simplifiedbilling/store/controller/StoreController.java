package com.simplifiedbilling.store.controller;

import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreLogo;
import com.simplifiedbilling.store.dto.UpdateStoreRequest;
import com.simplifiedbilling.store.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/store")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public StoreDetails getStore() {
        return storeService.getStore();
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public StoreDetails updateStore(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStoreRequest request) {
        return storeService.updateStore(jwt.getSubject(), request);
    }

    @PutMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public StoreDetails updateLogo(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        return storeService.updateLogo(jwt.getSubject(), file);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> deleteLogo(@AuthenticationPrincipal Jwt jwt) {
        storeService.deleteLogo(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logo")
    public ResponseEntity<ByteArrayResource> getLogo() {
        StoreLogo logo = storeService.getLogo();
        ByteArrayResource resource = new ByteArrayResource(logo.data());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .contentLength(logo.data().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(logo.fileName()).build().toString())
                .body(resource);
    }
}
