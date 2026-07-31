package com.simplifiedbilling.setup.service;

import com.simplifiedbilling.auth.dto.AuthResponse;
import com.simplifiedbilling.setup.dto.InitialSetupRequest;
import com.simplifiedbilling.setup.dto.SetupStatusResponse;

public interface SetupService {

    SetupStatusResponse getStatus();

    AuthResponse initialize(InitialSetupRequest request);
}
