package com.ai.aiops.approval;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.domain.Tenant;
import com.ai.aiops.domain.User;
import com.ai.aiops.repository.TenantRepository;
import com.ai.aiops.repository.UserRepository;

/**
 * Resolves the user approving/rejecting a remediation, auto-registering them by email on first
 * sight. There is no real authentication/identity provider yet (OAuth2/JWT is a later phase), so
 * the approver identity is whatever email the caller supplies in the request body - this is a
 * placeholder for real identity, not an access-control mechanism; it exists purely to satisfy the
 * "who decided" audit trail requirement until Phase 7+ wires up real auth.
 */
@Component
public class UserResolver {

    private static final String DEFAULT_TENANT_SLUG = "default";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public UserResolver(TenantRepository tenantRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public User resolve(String email) {
        Tenant tenant = tenantRepository.findBySlug(DEFAULT_TENANT_SLUG)
                .orElseGet(this::createDefaultTenant);

        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setTenant(tenant);
                    user.setEmail(email);
                    user.setDisplayName(email);
                    return userRepository.save(user);
                });
    }

    private Tenant createDefaultTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Default");
        tenant.setSlug(DEFAULT_TENANT_SLUG);
        return tenantRepository.save(tenant);
    }
}
