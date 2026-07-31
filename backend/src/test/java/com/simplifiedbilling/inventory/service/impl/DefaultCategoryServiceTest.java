package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.domain.Category;
import com.simplifiedbilling.inventory.dto.CategoryCreateRequest;
import com.simplifiedbilling.inventory.dto.CategoryUpdateRequest;
import com.simplifiedbilling.inventory.mapper.CategoryMapper;
import com.simplifiedbilling.inventory.repository.CategoryRepository;
import com.simplifiedbilling.inventory.repository.ProductRepository;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCategoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditWriter auditWriter;

    private DefaultCategoryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCategoryService(
                categoryRepository,
                productRepository,
                new CategoryMapper(),
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsActiveOrAllCategories() {
        Category category = Category.create("Grocery", NOW);
        when(categoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(category));
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(category));

        assertThat(service.listCategories(false)).extracting("name").containsExactly("Grocery");
        assertThat(service.listCategories(true)).extracting("name").containsExactly("Grocery");
    }

    @Test
    void createsNormalizedCategoryAndAuditsIt() {
        var response = service.createCategory("actor", new CategoryCreateRequest(" Grocery "));

        assertThat(response.name()).isEqualTo("Grocery");
        assertThat(response.active()).isTrue();
        verify(categoryRepository).saveAndFlush(any(Category.class));
        verify(auditWriter).write(
                "actor",
                "CATEGORY_CREATED",
                "CATEGORY",
                response.id(),
                Map.of("name", "Grocery"));
    }

    @Test
    void updatesCategoryWhenVersionAndUsageAreValid() {
        Category category = Category.create("Grocery", NOW.minusSeconds(5));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        var response = service.updateCategory(
                "actor",
                category.getId(),
                new CategoryUpdateRequest(" Daily Needs ", true, 0));

        assertThat(response.name()).isEqualTo("Daily Needs");
        verify(categoryRepository).flush();
        verify(auditWriter).write(
                "actor",
                "CATEGORY_UPDATED",
                "CATEGORY",
                category.getId(),
                Map.of("name", "Daily Needs", "active", true));
    }

    @Test
    void rejectsDuplicateMissingStaleAndInUseCategories() {
        when(categoryRepository.existsByNameIgnoreCase("Grocery")).thenReturn(true);
        assertError(
                () -> service.createCategory("actor", new CategoryCreateRequest("Grocery")),
                "CATEGORY_NAME_EXISTS");

        assertError(
                () -> service.updateCategory(
                        "actor", "missing", new CategoryUpdateRequest("Missing", true, 0)),
                "CATEGORY_NOT_FOUND");

        Category category = Category.create("Grocery", NOW);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        assertError(
                () -> service.updateCategory(
                        "actor", category.getId(), new CategoryUpdateRequest("Grocery", true, 9)),
                "STALE_CATEGORY_VERSION");

        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Other", category.getId()))
                .thenReturn(true);
        assertError(
                () -> service.updateCategory(
                        "actor", category.getId(), new CategoryUpdateRequest("Other", true, 0)),
                "CATEGORY_NAME_EXISTS");

        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Grocery", category.getId()))
                .thenReturn(false);
        when(productRepository.existsByCategoryIdAndActiveTrue(category.getId())).thenReturn(true);
        assertError(
                () -> service.updateCategory(
                        "actor", category.getId(), new CategoryUpdateRequest("Grocery", false, 0)),
                "CATEGORY_IN_USE");
        verify(categoryRepository, never()).flush();
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(code));
    }
}
