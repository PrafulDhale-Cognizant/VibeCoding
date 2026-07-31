package com.simplifiedbilling.khata.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerPhoneNormalizerTest {

    private final CustomerPhoneNormalizer normalizer = new CustomerPhoneNormalizer();

    @Test
    void normalizesLocalCountryCodeAndLeadingZeroFormats() {
        assertThat(normalizer.normalize("98765 43210")).isEqualTo("9876543210");
        assertThat(normalizer.normalize("+91-98765-43210")).isEqualTo("9876543210");
        assertThat(normalizer.normalize("09876543210")).isEqualTo("9876543210");
    }

    @Test
    void rejectsMissingOrInvalidNumbers() {
        assertInvalid(null);
        assertInvalid("12345");
        assertInvalid("1234567890123");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> normalizer.normalize(value))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_CUSTOMER_PHONE"));
    }
}
