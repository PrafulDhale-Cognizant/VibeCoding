package com.simplifiedbilling.user.controller;

import com.simplifiedbilling.auth.dto.UserSummary;
import com.simplifiedbilling.user.dto.CreateUserRequest;
import com.simplifiedbilling.user.dto.ResetPasswordRequest;
import com.simplifiedbilling.user.dto.UpdateUserRequest;
import com.simplifiedbilling.user.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void delegatesUserManagementAndResolvesOwnerAuthority() {
        UserManagementService service = mock(UserManagementService.class);
        UserController controller = new UserController(service);
        UserSummary summary = mock(UserSummary.class);
        CreateUserRequest create = mock(CreateUserRequest.class);
        UpdateUserRequest update = mock(UpdateUserRequest.class);
        ResetPasswordRequest reset = mock(ResetPasswordRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        var owner = new TestingAuthenticationToken(
                "owner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        var admin = new TestingAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.listUsers()).thenReturn(List.of(summary));
        when(service.createUser("actor", true, create)).thenReturn(summary);
        when(service.updateUser("actor", false, "target", update)).thenReturn(summary);

        assertThat(controller.listUsers()).containsExactly(summary);
        assertThat(controller.createUser(jwt, owner, create)).isSameAs(summary);
        assertThat(controller.updateUser(jwt, admin, "target", update)).isSameAs(summary);
        controller.resetPassword(jwt, owner, "target", reset);

        verify(service).resetPassword("actor", true, "target", reset);
    }
}
