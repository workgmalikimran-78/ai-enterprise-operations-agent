# Kubernetes deploy checklist

Companion to `SKILL.md` - the detailed steps for deploying to a cluster. Full background in
`k8s/README.md`; this is the actionable sequence.

## 1. Secrets — edit before applying, never commit real values

`k8s/20-app-config.yaml`'s `Secret` (`aiops-secrets`) has `CHANGE_ME` placeholders for:
- `postgres-password`
- `openai-api-key` (or wire `ANTHROPIC_API_KEY` instead and update `20-app-config.yaml`'s
  `AIOPS_LLM_PROVIDER` ConfigMap entry to `anthropic`)
- `lake-access-key` / `lake-secret-key` (any string - LocalStack's community edition doesn't
  enforce them, but the app's S3 client needs *some* non-empty value)
- `mcp-api-key` (optional - the Deployment marks this key `optional: true`; leave `CHANGE_ME` if
  you don't want auth on the MCP server yet, but set a real value before exposing it beyond a
  trusted network, since several of its tools execute real cluster mutations)

Prefer a real secret manager (External Secrets Operator, Sealed Secrets) over plain `stringData` for
anything beyond a local/throwaway cluster.

## 2. Build and load both application images

```bash
docker build -t aiops-agent-app:latest ./backend
docker build -t aiops-mcp-server:latest ./mcp-server
```
For a local cluster sharing Docker's image store (Rancher Desktop, kind with `--load`), that's
enough - `22-app-deployment.yaml` and `16-mcp-server.yaml` both use `imagePullPolicy:
IfNotPresent` against these exact tags. For a remote cluster, push both to a registry and update
the image references in those two manifests.

## 3. RBAC scoping

`21-app-rbac.yaml` scopes the app's `ServiceAccount` to Deployments/Pods/Services in the `default`
namespace only, matching `AIOPS_EXECUTION_ALLOWED_NAMESPACES` in the ConfigMap. If the execution
tools (restart/scale/rollback) need to target a different namespace, update **both** together -
changing one without the other silently breaks remediation execution (ExecutionGuard will reject
the namespace) or over-grants RBAC.

## 4. Apply

```bash
kubectl apply -f k8s/
```
Files are numbered for a sane manual order (namespace/RBAC before dependents), but applying the
whole directory at once works too - Kubernetes resolves most ordering itself. Watch for the `lake`
(LocalStack) pod to be ready before expecting the warehouse ETL job to succeed; its readiness probe
is on `/_localstack/health`.

## 5. Verify

```bash
kubectl get pods -n aiops
kubectl port-forward -n aiops svc/aiops-app 8081:8080 &
curl -sf http://localhost:8081/actuator/health
kubectl port-forward -n aiops svc/mcp-server 8090:3000 &
curl -sf http://localhost:8090/healthz
```
