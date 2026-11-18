package com.ai.aiops.logs;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Environment;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.repository.ApplicationRepository;
import com.ai.aiops.repository.EnvironmentRepository;
import com.ai.aiops.repository.TenantRepository;

/**
 * Resolves the (tenant, application, environment) a log event belongs to, auto-registering
 * applications/environments the first time a given service/environment name is seen.
 *
 * <p>There is no authentication/tenant-resolution layer yet (that's a later phase), so every log
 * event is attributed to a single bootstrapped "default" tenant. Applications and environments
 * are lightweight metadata registries, not access-controlled resources, so auto-vivifying them
 * from the "service"/"environment" fields on first sight (rather than requiring a separate admin
 * API call before any log can be ingested) keeps the system usable end-to-end in these phases.
 */
@Component
public class ApplicationResolver {

    private static final String DEFAULT_TENANT_SLUG = "default";

    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;

    public ApplicationResolver(TenantRepository tenantRepository, ApplicationRepository applicationRepository,
                                EnvironmentRepository environmentRepository) {
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public Environment resolve(String serviceName, String environmentName) {
        Tenant tenant = tenantRepository.findBySlug(DEFAULT_TENANT_SLUG)
                .orElseGet(this::createDefaultTenant);

        Application application = applicationRepository.findByTenantIdAndSlug(tenant.getId(), serviceName)
                .orElseGet(() -> createApplication(tenant, serviceName));

        return environmentRepository.findByApplicationIdAndName(application.getId(), environmentName)
                .orElseGet(() -> createEnvironment(application, environmentName));
    }

    private Tenant createDefaultTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Default");
        tenant.setSlug(DEFAULT_TENANT_SLUG);
        return tenantRepository.save(tenant);
    }

    private Application createApplication(Tenant tenant, String serviceName) {
        Application application = new Application();
        application.setTenant(tenant);
        application.setName(serviceName);
        application.setSlug(serviceName);
        return applicationRepository.save(application);
    }

    private Environment createEnvironment(Application application, String environmentName) {
        Environment environment = new Environment();
        environment.setApplication(application);
        environment.setName(environmentName);
        return environmentRepository.save(environment);
    }
}
