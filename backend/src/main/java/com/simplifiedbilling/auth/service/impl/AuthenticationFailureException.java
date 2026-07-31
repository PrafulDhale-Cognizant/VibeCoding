package com.simplifiedbilling.auth.service.impl;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;

final class AuthenticationFailureException extends ApplicationException {

    AuthenticationFailureException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
