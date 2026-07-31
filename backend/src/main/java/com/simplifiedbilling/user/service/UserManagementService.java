package com.simplifiedbilling.user.service;

import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.user.dto.CreateUserRequest;
import com.simplifiedbilling.user.dto.ResetPasswordRequest;
import com.simplifiedbilling.user.dto.UpdateUserRequest;

import java.util.List;

public interface UserManagementService {

    List<UserSummary> listUsers();

    UserSummary createUser(
            String actorUserId,
            boolean actorIsOwner,
            CreateUserRequest request);

    UserSummary updateUser(
            String actorUserId,
            boolean actorIsOwner,
            String targetUserId,
            UpdateUserRequest request);

    void resetPassword(
            String actorUserId,
            boolean actorIsOwner,
            String targetUserId,
            ResetPasswordRequest request);
}
