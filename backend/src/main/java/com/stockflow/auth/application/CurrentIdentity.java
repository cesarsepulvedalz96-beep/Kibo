package com.stockflow.auth.application;

import com.stockflow.auth.domain.SessionIdentity;
import com.stockflow.shared.domain.BusinessException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentIdentity {
    public SessionIdentity get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
            throw new BusinessException("UNAUTHORIZED", 401);
        }
        return identity;
    }

    public UUID businessId() {
        var businessId = get().businessId();
        if (businessId == null) throw new BusinessException("BUSINESS_REQUIRED", 403);
        return businessId;
    }

    public UUID userId() {
        return get().userId();
    }

    public void requireManager() {
        if (!get().role().equals("BUSINESS_USER")) throw new BusinessException("FORBIDDEN", 403);
    }

    public void requireSuperAdmin() {
        if (!get().role().equals("SUPER_ADMIN")) throw new BusinessException("FORBIDDEN", 403);
    }
}
