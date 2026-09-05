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
            
            if [ -n "$PARSED_URL" ]; then
                export SUPABASE_URL="$PARSED_URL"
            fi
            if [ -n "$PARSED_KEY" ]; then
                export SUPABASE_ANON_KEY="$PARSED_KEY"
            fi
        fi
    fi
fi

# Fallback default local URL if still unset
if [ -z "${SUPABASE_URL:-}" ]; then
    export SUPABASE_URL="http://127.0.0.1:54321"
fi

# Execute Python black-box E2E contract runner
python3 supabase/tests/e2e_contract_tests.py
