// Supabase Edge Function: delete-account
// Secure, server-authoritative permanent account deletion
// Deletion authority is STRICTLY derived from caller JWT.

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const JSON_HEADERS = {
  ...CORS_HEADERS,
  "Content-Type": "application/json",
};

function jsonResponse(status: number, data: Record<string, unknown>): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: JSON_HEADERS,
  });
}

// Recursively list all object keys under a prefix in the given storage bucket
async function listAllObjects(
  supabaseUrl: string,
  serviceKey: string,
  bucketId: string,
  prefix: string
): Promise<string[]> {
  const resultKeys: string[] = [];
  const limit = 100;
  let offset = 0;

  while (true) {
    const resp = await fetch(`${supabaseUrl}/storage/v1/object/list/${bucketId}`, {
      method: "POST",
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        prefix,
        limit,
        offset,
        sortBy: { column: "name", order: "asc" },
      }),
    });

    if (!resp.ok) {
      throw new Error(`STORAGE_LIST_ERROR_${resp.status}`);
    }

    const items = await resp.json();
    if (!Array.isArray(items) || items.length === 0) {
      break;
    }

    for (const item of items) {
      if (!item || !item.name) continue;
      const fullPath = prefix ? `${prefix}/${item.name}` : item.name;
      // If item is a folder (id is null or metadata is null)
      if (item.id === null || !item.metadata) {
        const subKeys = await listAllObjects(supabaseUrl, serviceKey, bucketId, fullPath);
        resultKeys.push(...subKeys);
      } else {
        resultKeys.push(fullPath);
      }
    }

    if (items.length < limit) {
      break;
    }
    offset += limit;
  }

  return resultKeys;
}

// Delete objects in batches
async function deleteStorageObjects(
  supabaseUrl: string,
  serviceKey: string,
  bucketId: string,
  keys: string[]
): Promise<void> {
  if (keys.length === 0) return;

  const batchSize = 100;
  for (let i = 0; i < keys.length; i += batchSize) {
    const batch = keys.slice(i, i + batchSize);
    const resp = await fetch(`${supabaseUrl}/storage/v1/object/${bucketId}`, {
      method: "DELETE",
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ prefixes: batch }),
    });

    if (!resp.ok) {
      throw new Error(`STORAGE_DELETE_ERROR_${resp.status}`);
    }
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  if (req.method !== "POST") {
    return jsonResponse(405, {
      error: "METHOD_NOT_ALLOWED",
      message: "Only POST method is supported",
    });
  }

  // 1. Authenticate caller JWT
  const authHeader = req.headers.get("Authorization") || req.headers.get("authorization") || "";
  const match = authHeader.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return jsonResponse(401, {
      error: "AUTH_REQUIRED",
      message: "Missing or malformed Authorization Bearer header",
    });
  }
  const callerJwt = match[1].trim();

  // 2. Prohibit client-provided target selectors in body
  try {
    const text = await req.text();
    if (text.trim().length > 0) {
      const body = JSON.parse(text);
      if (
        body.user_id ||
        body.uid ||
        body.email ||
        body.targetUser ||
        body.accountId ||
        body.target_uid ||
        body.target_user_id
      ) {
        return jsonResponse(400, {
          error: "INVALID_REQUEST",
          message: "Explicit user target selectors are forbidden; deletion authority is derived strictly from caller JWT",
        });
      }
    }
  } catch (_e) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Invalid JSON request body",
    });
  }

  // Read environment configuration
  const supabaseUrl = (Deno.env.get("SUPABASE_URL") || Deno.env.get("API_URL") || "http://127.0.0.1:54321").replace(/\/+$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("ANON_KEY") || "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SERVICE_ROLE_KEY") || "";

  if (!serviceRoleKey) {
    return jsonResponse(500, {
      error: "SERVER_CONFIG_ERROR",
      message: "Server environment missing required credentials",
    });
  }

  // 3. Verify caller JWT via Supabase Auth
  let deleteUid: string;
  try {
    const userResp = await fetch(`${supabaseUrl}/auth/v1/user`, {
      method: "GET",
      headers: {
        "apikey": anonKey || serviceRoleKey,
        "Authorization": `Bearer ${callerJwt}`,
      },
    });

    if (!userResp.ok) {
      return jsonResponse(401, {
        error: "AUTH_REQUIRED",
        message: "Invalid or expired session credentials",
      });
    }

    const userData = await userResp.json();
    deleteUid = userData?.id;
    if (
      !deleteUid ||
      typeof deleteUid !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(deleteUid)
    ) {
      return jsonResponse(401, {
        error: "AUTH_REQUIRED",
        message: "Unable to verify caller identity",
      });
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "AUTH_VERIFICATION_FAILED",
      message: "Network error during caller verification",
    });
  }

  // 4. STORAGE PURGE FIRST: Purge all objects under stamp-media/<uid>/...
  try {
    const userStorageKeys = await listAllObjects(
      supabaseUrl,
      serviceRoleKey,
      "stamp-media",
      deleteUid
    );

    if (userStorageKeys.length > 0) {
      await deleteStorageObjects(supabaseUrl, serviceRoleKey, "stamp-media", userStorageKeys);
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "STORAGE_CLEANUP_FAILED",
      message: "Failed to purge user storage media",
    });
  }

  // 5. AUTH DELETE SECOND: Delete auth user via Admin Auth API (triggers DB CASCADE)
  try {
    const deleteResp = await fetch(`${supabaseUrl}/auth/v1/admin/users/${deleteUid}`, {
      method: "DELETE",
      headers: {
        "apikey": serviceRoleKey,
        "Authorization": `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
      },
    });

    if (!deleteResp.ok) {
      return jsonResponse(500, {
        error: "ACCOUNT_DELETE_FAILED",
        message: "Failed to delete user account",
      });
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "ACCOUNT_DELETE_FAILED",
      message: "Network error during account deletion",
    });
  }

  // 6. Return sanitized success response
  return jsonResponse(200, {
    success: true,
  });
});
