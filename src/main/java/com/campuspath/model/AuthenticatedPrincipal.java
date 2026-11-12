package com.campuspath.model;

import java.util.Objects;

public final class AuthenticatedPrincipal extends User {
    public AuthenticatedPrincipal(UserAccount account) {
        super(
                Objects.requireNonNull(account, "account").getUserId(),
                account.getEmail(),
                null,
                account.getRole(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
