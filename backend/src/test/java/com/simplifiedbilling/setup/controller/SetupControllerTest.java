package com.simplifiedbilling.setup.controller;

import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.setup.dto.InitialSetupRequest;
import com.simplifiedbilling.setup.dto.SetupStatusResponse;
import com.simplifiedbilling.setup.service.SetupService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetupControllerTest {

    @Test
    void delegatesStatusAndInitialization() {
        SetupService service = mock(SetupService.class);
        SetupController controller = new SetupController(service);
        InitialSetupRequest request = mock(InitialSetupRequest.class);
        AuthResponse response = mock(AuthResponse.class);
        when(service.getStatus()).thenReturn(new SetupStatusResponse(true, "Shop"));
        when(service.initialize(request)).thenReturn(response);

        assertThat(controller.getStatus()).isEqualTo(new SetupStatusResponse(true, "Shop"));
        assertThat(controller.initialize(request)).isSameAs(response);
    }
}
