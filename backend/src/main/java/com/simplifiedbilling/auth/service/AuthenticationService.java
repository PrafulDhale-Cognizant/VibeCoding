package com.simplifiedbilling.auth.service;

import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.auth.dto.ChangePasswordRequest;
import com.simplifiedbilling.auth.dto.LoginRequest;
import com.simplifiedbilling.auth.dto.UserSummary;

public interface AuthenticationService {

    AuthResponse login(LoginRequest request);

    UserSummary getCurrentUser(String userId);

    void changePassword(String userId, ChangePasswordRequest request);
}
