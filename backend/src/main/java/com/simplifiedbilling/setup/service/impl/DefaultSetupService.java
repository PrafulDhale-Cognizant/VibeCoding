package com.simplifiedbilling.setup.service.impl;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.domain.UserRole;
import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.auth.service.SessionService;
import com.simplifiedbilling.auth.service.UsernameNormalizer;
import com.simplifiedbilling.setup.dto.InitialSetupRequest;
import com.simplifiedbilling.setup.dto.SetupStatusResponse;
import com.simplifiedbilling.setup.service.SetupService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import com.simplifiedbilling.store.domain.ShopProfile;
import com.simplifiedbilling.store.mapper.StoreMapper;
import com.simplifiedbilling.store.repository.ShopProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultSetupService implements SetupService {

    private final ShopProfileRepository shopRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final UsernameNormalizer usernameNormalizer;
    private final StoreMapper storeMapper;

    public DefaultSetupService(
            ShopProfileRepository shopRepository,
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            AuditWriter auditWriter,
            Clock clock,
            UsernameNormalizer usernameNormalizer,
            StoreMapper storeMapper) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.usernameNormalizer = usernameNormalizer;
        this.storeMapper = storeMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SetupStatusResponse getStatus() {
        return shopRepository.findById(ShopProfile.SINGLETON_ID)
                .map(profile -> new SetupStatusResponse(
                        true,
                        storeMapper.toDetails(profile).shopName()))
                .orElseGet(() -> new SetupStatusResponse(false, null));
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuthResponse initialize(InitialSetupRequest request) {
        if (shopRepository.existsById(ShopProfile.SINGLETON_ID) || userRepository.count() > 0) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "SETUP_ALREADY_COMPLETED",
                    "Initial setup has already been completed.");
        }

        String username = usernameNormalizer.normalize(request.owner().username());
        if (userRepository.existsByUsername(username)) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "USERNAME_ALREADY_EXISTS",
                    "That username is already in use.");
        }

        Instant now = Instant.now(clock);
        ShopProfile shop = shopRepository.save(ShopProfile.create(
                storeMapper.toDomain(request.store()),
                now));
        UserAccount owner = userRepository.save(UserAccount.create(
                username,
                request.owner().displayName().trim(),
                passwordEncoder.encode(request.owner().password()),
                Set.of(UserRole.OWNER, UserRole.ADMIN),
                now));

        auditWriter.write(
                owner.getId(),
                "STORE_SETUP_COMPLETED",
                "SHOP_PROFILE",
                String.valueOf(ShopProfile.SINGLETON_ID),
                Map.of(
                        "shopName",
                        storeMapper.toDetails(shop).shopName(),
                        "ownerUsername",
                        username));
        return sessionService.createSession(owner, "AUTH_SETUP_SESSION_CREATED");
    }
}
