# Kubernetes manifests

Plain YAML manifests (no Helm chart yet - packaging these as a chart with configurable values is
a natural next step). Mirrors `docker-compose.yml`'s local setup: single-node Postgres/Redis/Kafka
(KRaft)/OpenSearch/data-lake storage, not production-hardened (no replication, no persistent
volume for Kafka/OpenSearch, security plugin disabled on OpenSearch).

## Before applying

1. Edit `20-app-config.yaml`'s `Secret` - replace `CHANGE_ME` with a real Postgres password,
   OpenAI API key, data lake access/secret key, and (optionally) an MCP server bearer token (or
   wire this to a real secret manager instead of plain `stringData`). `14-lake.yaml` runs LocalStack
   as a local S3-compatible stand-in - see its header comment for why, and swap it for real object
   storage before anything beyond local use.
2. Build and load both images into your cluster. For a local cluster sharing Docker's image store
   (e.g. Rancher Desktop, kind with `--load`): `docker build -t aiops-agent-app:latest ./backend`
   and `docker build -t aiops-mcp-server:latest ./mcp-server` - `22-app-deployment.yaml` and
   `16-mcp-server.yaml` reference those tags with `imagePullPolicy: IfNotPresent`. For a remote
   cluster, push both to a registry and update the image references.
3. `21-app-rbac.yaml` scopes the app's `ServiceAccount` to operate on Deployments/Pods/Services in
   the `default` namespace only (matching `AIOPS_EXECUTION_ALLOWED_NAMESPACES` in the ConfigMap) -
   update both together if you want the execution tools to target a different namespace.

## Apply

```bash
kubectl apply -f k8s/
```

Files are numbered to apply in a sane order (namespace and RBAC before anything that depends on
them), though `kubectl apply -f k8s/` applying the whole directory at once also works since
Kubernetes resolves most ordering itself.
