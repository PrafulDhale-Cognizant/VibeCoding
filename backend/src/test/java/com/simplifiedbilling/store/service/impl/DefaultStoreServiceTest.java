package com.simplifiedbilling.store.service.impl;

import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.domain.ReceiptWidth;
import com.simplifiedbilling.store.domain.A4InvoiceTemplate;
import com.simplifiedbilling.store.domain.InvoicePrintFormat;
import com.simplifiedbilling.store.domain.ThermalReceiptTemplate;
import com.simplifiedbilling.store.domain.ShopProfile;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreLogo;
import com.simplifiedbilling.store.dto.StoreProfileRequest;
import com.simplifiedbilling.store.dto.UpdateStoreRequest;
import com.simplifiedbilling.store.mapper.StoreMapper;
import com.simplifiedbilling.store.repository.ShopProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStoreServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    @Mock
    private ShopProfileRepository shopRepository;

    @Mock
    private AuditWriter auditWriter;

    private StoreMapper storeMapper;
    private DefaultStoreService service;

    @BeforeEach
    void setUp() {
        storeMapper = new StoreMapper();
        service = new DefaultStoreService(
                shopRepository,
                auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                storeMapper);
    }

    @Test
    void readsAndUpdatesStoreWithMatchingVersion() {
        ShopProfile profile = profile();
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.of(profile));

        assertThat(service.getStore().shopName()).isEqualTo("Local Grocery");

        StoreProfileRequest updatedRequest = request("Updated Grocery");
        StoreDetails updated = service.updateStore(
                "actor-1",
                new UpdateStoreRequest(updatedRequest, 0L));

        assertThat(updated.shopName()).isEqualTo("Updated Grocery");
        assertThat(updated.invoicePrintFormat()).isEqualTo(InvoicePrintFormat.A4);
        assertThat(updated.a4InvoiceTemplate()).isEqualTo(A4InvoiceTemplate.MODERN);
        assertThat(updated.thermalReceiptTemplate()).isEqualTo(ThermalReceiptTemplate.BORDERED);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        verify(shopRepository).flush();
        verify(auditWriter).write(
                "actor-1",
                "STORE_PROFILE_UPDATED",
                "SHOP_PROFILE",
                "1",
                java.util.Map.of("shopName", "Updated Grocery"));
    }

    @Test
    void rejectsMissingStoreAndStaleUpdate() {
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.empty());

        assertApplicationError(
                () -> service.getStore(),
                HttpStatus.CONFLICT,
                "SETUP_REQUIRED");

        ShopProfile profile = profile();
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.of(profile));

        assertApplicationError(
                () -> service.updateStore(
                        "actor",
                        new UpdateStoreRequest(request("Changed"), 99L)),
                HttpStatus.CONFLICT,
                "STALE_STORE_VERSION");
        verify(shopRepository, never()).flush();
    }

    @Test
    void uploadsReadsAndDeletesLogo() {
        ShopProfile profile = profile();
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.of(profile));
        MockMultipartFile logo = new MockMultipartFile(
                "file",
                "C:\\images\\receipt-logo.png",
                "image/png",
                new byte[]{1, 2, 3});

        StoreDetails updated = service.updateLogo("actor", logo);
        StoreLogo stored = service.getLogo();

        assertThat(updated.logoAvailable()).isTrue();
        assertThat(stored.fileName()).isEqualTo("receipt-logo.png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.data()).containsExactly(1, 2, 3);

        byte[] callerCopy = stored.data();
        callerCopy[0] = 99;
        assertThat(stored.data()).containsExactly(1, 2, 3);

        service.deleteLogo("actor");
        assertThat(profile.hasLogo()).isFalse();
        verify(auditWriter).write(
                "actor",
                "STORE_LOGO_REMOVED",
                "SHOP_PROFILE",
                "1",
                java.util.Map.of());
    }

    @Test
    void usesFallbackNameForUnnamedLogo() {
        ShopProfile profile = profile();
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.of(profile));
        MockMultipartFile logo = new MockMultipartFile(
                "file",
                "",
                "image/jpeg",
                new byte[]{7});

        service.updateLogo("actor", logo);

        assertThat(service.getLogo().fileName()).isEqualTo("shop-logo");
    }

    @Test
    void rejectsMissingAndInvalidLogoFiles() throws IOException {
        ShopProfile profile = profile();
        when(shopRepository.findById(ShopProfile.SINGLETON_ID))
                .thenReturn(Optional.of(profile));

        assertApplicationError(
                service::getLogo,
                HttpStatus.NOT_FOUND,
                "LOGO_NOT_FOUND");

        assertApplicationError(
                () -> service.updateLogo(
                        "actor",
                        new MockMultipartFile("file", new byte[0])),
                HttpStatus.BAD_REQUEST,
                "EMPTY_LOGO");

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(DefaultStoreService.MAX_LOGO_BYTES + 1);
        assertApplicationError(
                () -> service.updateLogo("actor", oversized),
                HttpStatus.BAD_REQUEST,
                "LOGO_TOO_LARGE");

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "logo.txt",
                "text/plain",
                new byte[]{1});
        assertApplicationError(
                () -> service.updateLogo("actor", textFile),
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_LOGO_TYPE");

        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(2L);
        when(unreadable.getContentType()).thenReturn("image/png");
        when(unreadable.getBytes()).thenThrow(new IOException("read failed"));
        assertApplicationError(
                () -> service.updateLogo("actor", unreadable),
                HttpStatus.BAD_REQUEST,
                "LOGO_READ_FAILED");
    }

    private ShopProfile profile() {
        return ShopProfile.create(storeMapper.toDomain(request("Local Grocery")), NOW.minusSeconds(60));
    }

    private StoreProfileRequest request(String shopName) {
        return new StoreProfileRequest(
                " " + shopName + " ",
                " Asha Kumar ",
                " 12 Market Road ",
                " ",
                " Pune ",
                " Maharashtra ",
                "27",
                "411001",
                "9876543210",
                " ",
                false,
                "",
                "INR",
                "Asia/Kolkata",
                " inv ",
                4,
                ReceiptWidth.MM_80,
                InvoicePrintFormat.A4,
                A4InvoiceTemplate.MODERN,
                ThermalReceiptTemplate.BORDERED);
    }

    private void assertApplicationError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            HttpStatus status,
            String code) {

        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }
}
