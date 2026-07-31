package com.simplifiedbilling.shared.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsOnlyPasswordsMeetingEveryRule() {
        assertThat(validator.isValid("StrongLocal#123", null)).isTrue();

        assertThat(validator.isValid(null, null)).isFalse();
        assertThat(validator.isValid("Short#1A", null)).isFalse();
        assertThat(validator.isValid("a".repeat(73) + "A1#", null)).isFalse();
        assertThat(validator.isValid("nouppercase#123", null)).isFalse();
        assertThat(validator.isValid("NOLOWERCASE#123", null)).isFalse();
        assertThat(validator.isValid("NoDigitsHere###", null)).isFalse();
        assertThat(validator.isValid("NoSpecialHere123", null)).isFalse();
        assertThat(validator.isValid("Whitespace 123Aa", null)).isFalse();
    }
}
