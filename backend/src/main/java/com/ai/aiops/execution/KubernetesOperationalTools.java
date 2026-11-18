package com.ai.aiops.execution;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.common.exception.ValidationException;

/**
 * Predefined, parameterized Kubernetes operations - deliberately not a generic command executor.
 * Every method takes typed parameters, never a raw command string, so nothing calling these tools
 * (including an LLM-driven agent) can run arbitrary cluster operations. Every mutating method
 * first goes through {@link ExecutionGuard} (kill switch, namespace allowlist, rate limit).
 */
@Component
public class KubernetesOperationalTools {

    private static final int MAX_LOG_LINES = 500;

    private final KubernetesClient client;
    private final ExecutionGuard executionGuard;

    public KubernetesOperationalTools(KubernetesClient client, ExecutionGuard executionGuard) {
        this.client = client;
        this.executionGuard = executionGuard;
    }

    // ---- read-only tools: no approval or rate limit required ----

    public ApplicationHealth checkApplicationHealth(String namespace, String deploymentName) {
        Deployment deployment = getDeploymentOrThrow(namespace, deploymentName);
        int desired = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(0);
        int available = deployment.getStatus() != null && deployment.getStatus().getAvailableReplicas() != null
                ? deployment.getStatus().getAvailableReplicas() : 0;
        int ready = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas() : 0;
        return new ApplicationHealth(deploymentName, desired, available, ready, desired > 0 && available >= desired);
    }

    public PodInfo getPodInfo(String namespace, String deploymentName) {
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabel("app", deploymentName)
                .list().getItems();
        List<PodSummary> summaries = pods.stream()
                .map(p -> new PodSummary(
                        p.getMetadata().getName(),
                        p.getStatus() != null ? p.getStatus().getPhase() : "Unknown",
                        isReady(p),
                        restartCount(p)))
                .toList();
        return new PodInfo(deploymentName, summaries);
    }

    public ServiceStatus getServiceStatus(String namespace, String serviceName) {
        Service service = client.services().inNamespace(namespace).withName(serviceName).get();
        if (service == null) {
            throw new ResourceNotFoundException("Service not found: " + namespace + "/" + serviceName);
        }
        List<Integer> ports = service.getSpec().getPorts().stream()
                .map(p -> p.getPort())
                .toList();
        return new ServiceStatus(serviceName, service.getSpec().getType(), service.getSpec().getClusterIP(), ports);
    }

    public RecentLogs getRecentLogs(String namespace, String podName, int tailLines) {
        int cappedLines = Math.min(Math.max(tailLines, 1), MAX_LOG_LINES);
        String raw = client.pods().inNamespace(namespace).withName(podName).tailingLines(cappedLines).getLog();
        List<String> lines = raw == null || raw.isBlank() ? List.of() : List.of(raw.split("\n"));
        return new RecentLogs(podName, lines);
    }

    // ---- mutating tools: require ExecutionGuard clearance (caller must have already verified approval) ----

    public RestartResult restartDeployment(String namespace, String deploymentName) {
        executionGuard.validateTarget(namespace, deploymentName);
        executionGuard.checkRateLimit(namespace, deploymentName);
        getDeploymentOrThrow(namespace, deploymentName);

        Instant restartedAt = Instant.now();
        client.apps().deployments().inNamespace(namespace).withName(deploymentName)
                .edit(d -> new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder(d)
                        .editSpec().editTemplate().editMetadata()
                        .addToAnnotations("kubectl.kubernetes.io/restartedAt", restartedAt.toString())
                        .endMetadata().endTemplate().endSpec()
                        .build());
        return new RestartResult(deploymentName, restartedAt);
    }

    public ScaleResult scaleDeployment(String namespace, String deploymentName, int targetReplicas) {
        executionGuard.validateTarget(namespace, deploymentName);
        executionGuard.checkRateLimit(namespace, deploymentName);
        if (targetReplicas < 0 || targetReplicas > 20) {
            throw new ValidationException("targetReplicas must be between 0 and 20, got " + targetReplicas);
        }
        Deployment deployment = getDeploymentOrThrow(namespace, deploymentName);
        int previousReplicas = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(0);

        client.apps().deployments().inNamespace(namespace).withName(deploymentName).scale(targetReplicas);
        return new ScaleResult(deploymentName, previousReplicas, targetReplicas);
    }

    public RollbackResult rollbackDeployment(String namespace, String deploymentName) {
        executionGuard.validateTarget(namespace, deploymentName);
        executionGuard.checkRateLimit(namespace, deploymentName);
        Deployment deployment = getDeploymentOrThrow(namespace, deploymentName);

        List<ReplicaSet> replicaSets = client.apps().replicaSets().inNamespace(namespace)
                .withLabelSelector(deployment.getSpec().getSelector())
                .list().getItems();

        List<ReplicaSet> withRevision = replicaSets.stream()
                .filter(rs -> revisionOf(rs) != null)
                .sorted(Comparator.comparing(KubernetesOperationalTools::revisionOf).reversed())
                .toList();

        if (withRevision.size() < 2) {
            throw new ValidationException(
                    "No previous revision available to roll back to for deployment " + deploymentName);
        }
        ReplicaSet previous = withRevision.get(1);

        client.apps().deployments().inNamespace(namespace).withName(deploymentName)
                .edit(d -> new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder(d)
                        .editSpec().withTemplate(previous.getSpec().getTemplate()).endSpec()
                        .build());

        return new RollbackResult(deploymentName, revisionOf(previous).toString());
    }

    private Deployment getDeploymentOrThrow(String namespace, String deploymentName) {
        Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null) {
            throw new ResourceNotFoundException("Deployment not found: " + namespace + "/" + deploymentName);
        }
        return deployment;
    }

    private static Long revisionOf(ReplicaSet rs) {
        Map<String, String> annotations = rs.getMetadata().getAnnotations();
        String value = annotations != null ? annotations.get("deployment.kubernetes.io/revision") : null;
        return value != null ? Long.valueOf(value) : null;
    }

    private static boolean isReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    private static int restartCount(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        return pod.getStatus().getContainerStatuses().stream()
                .mapToInt(cs -> cs.getRestartCount() != null ? cs.getRestartCount() : 0)
                .sum();
    }

    public record ApplicationHealth(String deploymentName, int desiredReplicas, int availableReplicas,
                                     int readyReplicas, boolean healthy) {
    }

    public record PodSummary(String name, String phase, boolean ready, int restartCount) {
    }

    public record PodInfo(String deploymentName, List<PodSummary> pods) {
    }

    public record ServiceStatus(String serviceName, String type, String clusterIp, List<Integer> ports) {
    }

    public record RecentLogs(String podName, List<String> lines) {
    }

    public record RestartResult(String deploymentName, Instant restartedAt) {
    }

    public record ScaleResult(String deploymentName, int previousReplicas, int newReplicas) {
    }

    public record RollbackResult(String deploymentName, String rolledBackToRevision) {
    }
}
