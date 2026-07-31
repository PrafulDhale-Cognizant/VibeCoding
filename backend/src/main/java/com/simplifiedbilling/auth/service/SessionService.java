package com.simplifiedbilling.auth.service;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.dto.AuthResponse;

public interface SessionService {

    AuthResponse createSession(UserAccount user, String eventType);

    AuthResponse rotate(String rawRefreshToken);

    void revoke(String rawRefreshToken);

    void revokeAllForUser(String userId);
}
