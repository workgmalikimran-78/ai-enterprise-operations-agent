#!/usr/bin/env bash
# PreToolUse hook for Bash: blocks the one destructive pattern specific to this repo's real risk -
# wiping the docker-compose named volumes (aiops-postgres-data, aiops-lake-data) that hold real
# local dev state (incidents, warehouse data, catalog seed re-runs aside). General git/rm safety is
# already handled by the agent's own built-in caution; this hook only covers what's specific here.
#
# Wired via .claude/settings.json's hooks.PreToolUse. Reads the tool call as JSON on stdin, exits 2
# (blocks the call, message goes to the agent) on a match, exits 0 otherwise.

input=$(cat)
command=$(echo "$input" | python3 -c "import json,sys; print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))" 2>/dev/null)

if echo "$command" | grep -qE 'docker( |-)compose[^|&;]*down[^|&;]*(-v\b|--volumes\b)'; then
    echo "Blocked: '$command' would delete this repo's docker-compose volumes (aiops-postgres-data, aiops-lake-data) - real local incident/warehouse data, not reproducible by re-seeding. If you actually mean to wipe local state, ask the user explicitly first, then re-run with the user's confirmation noted." >&2
    exit 2
fi

exit 0
