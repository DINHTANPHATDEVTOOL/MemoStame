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

# Check if delete-account function is already responsive
FUNCTION_URL="${SUPABASE_URL}/functions/v1/delete-account"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$FUNCTION_URL" 2>/dev/null || true)

FUNC_PID=""
if [ "$HTTP_CODE" != "200" ] && command -v supabase >/dev/null 2>&1; then
    echo "Starting local delete-account function in background..."
    supabase functions serve delete-account --no-verify-jwt > /tmp/supabase_functions_serve.log 2>&1 &
    FUNC_PID=$!

    cleanup_func() {
        if [ -n "$FUNC_PID" ]; then
            kill "$FUNC_PID" 2>/dev/null || true
            wait "$FUNC_PID" 2>/dev/null || true
        fi
    }
    trap cleanup_func EXIT INT TERM

    # Deterministic HTTP probe (up to 30s)
    READY=0
    for i in $(seq 1 30); do
        PROBE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$FUNCTION_URL" 2>/dev/null || true)
        if [ "$PROBE_CODE" = "200" ] || [ "$PROBE_CODE" = "401" ] || [ "$PROBE_CODE" = "405" ]; then
            READY=1
            break
        fi
        sleep 1
    done

    if [ "$READY" -ne 1 ]; then
        echo "[ERROR] delete-account function failed to become ready within 30s"
        cat /tmp/supabase_functions_serve.log || true
        exit 1
    fi
    echo "delete-account function is ready."
fi

# Execute Python black-box E2E contract runner
python3 supabase/tests/e2e_contract_tests.py
