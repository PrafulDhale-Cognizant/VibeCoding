package com.simplifiedbilling.store.service.impl;

import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.domain.ShopProfile;
import com.simplifiedbilling.store.dto.StoreDetails;
import com.simplifiedbilling.store.dto.StoreLogo;
import com.simplifiedbilling.store.dto.UpdateStoreRequest;
import com.simplifiedbilling.store.repository.ShopProfileRepository;
import com.simplifiedbilling.store.service.StoreService;
import com.simplifiedbilling.store.mapper.StoreMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultStoreService implements StoreService {

    public static final long MAX_LOGO_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of("image/png", "image/jpeg");

    private final ShopProfileRepository shopRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final StoreMapper storeMapper;

    public DefaultStoreService(
            ShopProfileRepository shopRepository,
            AuditWriter auditWriter,
            Clock clock,
            StoreMapper storeMapper) {
        this.shopRepository = shopRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.storeMapper = storeMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDetails getStore() {
        return storeMapper.toDetails(requireStore());
    }

    @Override
    @Transactional
    public StoreDetails updateStore(String actorUserId, UpdateStoreRequest request) {
        ShopProfile store = requireStore();
        if (store.getVersion() != request.version()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_STORE_VERSION",
                    "Shop settings have changed. Refresh and try again.");
        }

        store.update(storeMapper.toDomain(request.profile()), Instant.now(clock));
        shopRepository.flush();
        auditWriter.write(
                actorUserId,
                "STORE_PROFILE_UPDATED",
                "SHOP_PROFILE",
                String.valueOf(ShopProfile.SINGLETON_ID),
                Map.of("shopName", store.getShopName()));
        return storeMapper.toDetails(store);
    }

    @Override
    @Transactional
    public StoreDetails updateLogo(String actorUserId, MultipartFile file) {
        validateLogo(file);
        ShopProfile store = requireStore();
        try {
            store.updateLogo(
                    sanitizeFileName(file.getOriginalFilename()),
                    file.getContentType(),
                    file.getBytes(),
                    Instant.now(clock));
            shopRepository.flush();
        } catch (IOException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "LOGO_READ_FAILED",
                    "The selected logo could not be read.");
        }

        auditWriter.write(
                actorUserId,
                "STORE_LOGO_UPDATED",
                "SHOP_PROFILE",
                String.valueOf(ShopProfile.SINGLETON_ID),
                Map.of("contentType", file.getContentType(), "size", file.getSize()));
        return storeMapper.toDetails(store);
    }

    @Override
    @Transactional
    public void deleteLogo(String actorUserId) {
        ShopProfile store = requireStore();
        store.removeLogo(Instant.now(clock));
        auditWriter.write(
                actorUserId,
                "STORE_LOGO_REMOVED",
                "SHOP_PROFILE",
                String.valueOf(ShopProfile.SINGLETON_ID),
                Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public StoreLogo getLogo() {
        ShopProfile store = requireStore();
        byte[] data = store.getLogoData();
        if (data == null) {
            throw new ApplicationException(
                    HttpStatus.NOT_FOUND,
                    "LOGO_NOT_FOUND",
                    "No shop logo has been uploaded.");
        }
        return new StoreLogo(store.getLogoFileName(), store.getLogoContentType(), data);
    }

    private ShopProfile requireStore() {
        return shopRepository.findById(ShopProfile.SINGLETON_ID)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.CONFLICT,
                        "SETUP_REQUIRED",
                        "Complete initial store setup first."));
    }

    private void validateLogo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST, "EMPTY_LOGO", "Select a logo file.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST, "LOGO_TOO_LARGE", "Logo size cannot exceed 2 MB.");
        }
        if (file.getContentType() == null || !ALLOWED_LOGO_TYPES.contains(file.getContentType())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_LOGO_TYPE",
                    "Logo must be a PNG or JPEG image.");
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "shop-logo";
        }
        String sanitized = name.replace("\\", "/");
        return sanitized.substring(sanitized.lastIndexOf('/') + 1);
    }

}
