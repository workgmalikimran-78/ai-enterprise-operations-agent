---
description: Run the backend test suite with the correct environment variables pre-set
---

Run the backend test suite with every env var this repo's Testcontainers setup actually needs
(see `.claude/rules/testing.md` for why each one matters):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
cd backend && mvn test
```

If `$ARGUMENTS` names a specific test class or pattern, run only that:
```bash
mvn test -Dtest=$ARGUMENTS
```

Report the final `Tests run: X, Failures: Y, Errors: Z` summary. If anything failed, show the
actual assertion/stack trace for each failure, not just the count — and don't stop at the first
failure if more than one test failed, report all of them.
