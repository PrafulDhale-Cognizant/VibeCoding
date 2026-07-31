package com.simplifiedbilling.inventory.controller;

import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.CategoryResponse;
import com.simplifiedbilling.inventory.dto.CategoryUpdateRequest;
import com.simplifiedbilling.inventory.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/categories")
public class CategoryController {

    private static final String INVENTORY_READ =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER', 'VIEWER')";
    private static final String INVENTORY_WRITE =
            "hasAnyRole('OWNER', 'ADMIN', 'INVENTORY_MANAGER')";

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize(INVENTORY_READ)
    public List<CategoryResponse> listCategories(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.listCategories(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(INVENTORY_WRITE)
    public CategoryResponse createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.createCategory(jwt.getSubject(), request);
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize(INVENTORY_WRITE)
    public CategoryResponse updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.updateCategory(jwt.getSubject(), categoryId, request);
    }
}
