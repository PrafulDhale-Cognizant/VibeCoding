package com.simplifiedbilling.auth.mapper;

import com.simplifiedbilling.auth.domain.UserAccount;
import com.simplifiedbilling.auth.dto.UserSummary;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserSummary toSummary(UserAccount user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoles(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getVersion());
    }
}
