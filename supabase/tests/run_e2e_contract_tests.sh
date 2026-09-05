#!/usr/bin/env bash
set -euo pipefail

# Ensure script runs from workspace root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

# If SUPABASE_URL or SUPABASE_ANON_KEY are not set in environment, obtain from local supabase status
if [ -z "${SUPABASE_URL:-}" ] || [ -z "${SUPABASE_ANON_KEY:-}" ]; then
    if command -v supabase >/dev/null 2>&1; then
        STATUS_JSON=$(supabase status -o json 2>/dev/null || true)
        if [ -n "$STATUS_JSON" ]; then
            PARSED_URL=$(python3 -c "import json, sys; d=json.loads(sys.argv[1]); print(d.get('API_URL') or d.get('api_url') or '')" "$STATUS_JSON" 2>/dev/null || true)
            PARSED_KEY=$(python3 -c "import json, sys; d=json.loads(sys.argv[1]); print(d.get('ANON_KEY') or d.get('anon_key') or '')" "$STATUS_JSON" 2>/dev/null || true)
            PARSED_SERVICE_KEY=$(python3 -c "import json, sys; d=json.loads(sys.argv[1]); print(d.get('SERVICE_ROLE_KEY') or d.get('service_role_key') or '')" "$STATUS_JSON" 2>/dev/null || true)
            
            if [ -n "$PARSED_URL" ]; then
                export SUPABASE_URL="$PARSED_URL"
            fi
            if [ -n "$PARSED_KEY" ]; then
                export SUPABASE_ANON_KEY="$PARSED_KEY"
            fi
            if [ -n "$PARSED_SERVICE_KEY" ] && [ -z "${SUPABASE_SERVICE_ROLE_KEY:-}" ]; then
                export SUPABASE_SERVICE_ROLE_KEY="$PARSED_SERVICE_KEY"
            fi
        fi
    fi
fi

# Fallback default local URL if still unset
if [ -z "${SUPABASE_URL:-}" ]; then
    export SUPABASE_URL="http://127.0.0.1:54321"
fi

# Check if edge functions are already responsive
DEL_URL="${SUPABASE_URL}/functions/v1/delete-account"
PUSH_URL="${SUPABASE_URL}/functions/v1/dispatch-push"
DEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$DEL_URL" 2>/dev/null || true)
PUSH_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$PUSH_URL" 2>/dev/null || true)

FUNC_PID=""
if ([ "$DEL_CODE" != "200" ] || [ "$PUSH_CODE" != "200" ]) && command -v supabase >/dev/null 2>&1; then
    echo "Starting local Edge Functions in background..."
    if [ -n "${SUPABASE_SERVICE_ROLE_KEY:-}" ]; then
        printf "SUPABASE_SERVICE_ROLE_KEY=%s\nPUSH_PROVIDER_MODE=mock\nMOCK_PUSH_URL=http://127.0.0.1:54325/mock-push\n" "$SUPABASE_SERVICE_ROLE_KEY" > supabase/functions/.env
    fi
    supabase functions serve --no-verify-jwt > /tmp/supabase_functions_serve.log 2>&1 &
    FUNC_PID=$!

    cleanup_func() {
        rm -f supabase/functions/.env
        if [ -n "$FUNC_PID" ]; then
            kill "$FUNC_PID" 2>/dev/null || true
            wait "$FUNC_PID" 2>/dev/null || true
        fi
    }
    trap cleanup_func EXIT INT TERM

    # Deterministic HTTP probe (up to 30s)
    READY=0
    for i in $(seq 1 30); do
        PROBE_DEL=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$DEL_URL" 2>/dev/null || true)
        PROBE_PUSH=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$PUSH_URL" 2>/dev/null || true)
        if ([ "$PROBE_DEL" = "200" ] || [ "$PROBE_DEL" = "401" ] || [ "$PROBE_DEL" = "405" ]) && \
           ([ "$PROBE_PUSH" = "200" ] || [ "$PROBE_PUSH" = "401" ] || [ "$PROBE_PUSH" = "405" ]); then
            READY=1
            break
        fi
        sleep 1
    done

    if [ "$READY" -ne 1 ]; then
        echo "[ERROR] Edge functions failed to become ready within 30s"
        cat /tmp/supabase_functions_serve.log || true
        exit 1
    fi
    echo "Edge functions (delete-account & dispatch-push) are ready."
fi

# Execute Python black-box E2E contract runner
python3 supabase/tests/e2e_contract_tests.py
