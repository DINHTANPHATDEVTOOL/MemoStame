// Supabase Edge Function: accept-trade
// Authoritative, server-side stamp trade acceptance with recipient-owned media copy.
// Recipient identity is strictly derived from the caller's JWT.

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

  // 2. Parse request body
  let tradeId = "";
  try {
    const text = await req.text();
    if (!text || text.trim() === "") {
      return jsonResponse(400, {
        error: "INVALID_REQUEST",
        message: "Missing request body",
      });
    }
    const body = JSON.parse(text);
    tradeId = typeof body.trade_id === "string" ? body.trade_id.trim() : (typeof body.tradeId === "string" ? body.tradeId.trim() : "");
    if (!tradeId || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(tradeId)) {
      return jsonResponse(400, {
        error: "INVALID_TRADE_ID",
        message: "trade_id must be a valid UUID",
      });
    }
  } catch (_e) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Malformed JSON request body",
    });
  }

  // Read environment config
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
  let recipientUid = "";
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
    recipientUid = userData?.id;
    if (!recipientUid || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(recipientUid)) {
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

  const restHeaders = {
    "apikey": serviceRoleKey,
    "Authorization": `Bearer ${serviceRoleKey}`,
    "Content-Type": "application/json",
  };

  // 4. Load authoritative trade request
  let trade: {
    id: string;
    sender_id: string;
    recipient_id: string;
    source_object_name: string;
    stamp_title: string;
    stamp_shape: string;
    location: string | null;
    note: string | null;
    status: string;
  };

  try {
    const tradeResp = await fetch(
      `${supabaseUrl}/rest/v1/stamp_trade_requests?id=eq.${tradeId}&select=*`,
      { headers: restHeaders }
    );
    if (!tradeResp.ok) {
      return jsonResponse(500, { error: "DB_ERROR", message: "Failed to query trade request" });
    }
    const trades = await tradeResp.json();
    if (!Array.isArray(trades) || trades.length === 0) {
      return jsonResponse(404, { error: "NOT_FOUND", message: "Trade request not found" });
    }
    trade = trades[0];
  } catch (_e) {
    return jsonResponse(500, { error: "SERVER_ERROR", message: "Error querying trade request" });
  }

  // Idempotency: if already ACCEPTED, check if received stamp exists
  if (trade.status === "ACCEPTED") {
    try {
      const recResp = await fetch(
        `${supabaseUrl}/rest/v1/received_trade_stamps?source_trade_id=eq.${tradeId}&select=*`,
        { headers: restHeaders }
      );
      if (recResp.ok) {
        const recList = await recResp.json();
        if (Array.isArray(recList) && recList.length > 0) {
          const recObj = recList[0];
          const destKey = recObj.recipient_media_path || recObj.media_path || `${recipientUid}/received/${trade.id}.png`;
          return jsonResponse(200, {
            success: true,
            trade_id: trade.id,
            status: "ACCEPTED",
            destination_key: destKey,
            recipient_media_path: destKey,
            destination_media_path: destKey,
            received_stamp: recObj,
            received_stamp_id: recObj.id,
            idempotent: true,
          });
        }
      }
    } catch (_e) {
      // Continue normal flow
    }
  }

  if (trade.status !== "PENDING") {
    return jsonResponse(400, {
      error: "INVALID_STATE",
      message: `Trade request is not pending (current status: ${trade.status})`,
    });
  }

  // Verify caller is the designated recipient
  if (trade.recipient_id !== recipientUid) {
    return jsonResponse(403, {
      error: "FORBIDDEN",
      message: "Only the recipient can accept this trade request",
    });
  }

  // 5. Verify friendship is still active
  try {
    const friendResp = await fetch(
      `${supabaseUrl}/rest/v1/friends?or=(and(user_id_1.eq.${trade.sender_id},user_id_2.eq.${recipientUid}),and(user_id_1.eq.${recipientUid},user_id_2.eq.${trade.sender_id}))&select=id`,
      { headers: restHeaders }
    );
    if (!friendResp.ok) {
      return jsonResponse(500, { error: "DB_ERROR", message: "Failed to verify friendship status" });
    }
    const friendData = await friendResp.json();
    if (!Array.isArray(friendData) || friendData.length === 0) {
      return jsonResponse(400, {
        error: "FRIENDSHIP_REQUIRED",
        message: "Cannot accept trade: sender and recipient are no longer friends",
      });
    }
  } catch (_e) {
    return jsonResponse(500, { error: "SERVER_ERROR", message: "Error checking friendship" });
  }

  // 6. Verify relationship is not blocked in either direction
  try {
    const blockResp = await fetch(
      `${supabaseUrl}/rest/v1/user_blocks?or=(and(blocker_id.eq.${trade.sender_id},blocked_id.eq.${recipientUid}),and(blocker_id.eq.${recipientUid},blocked_id.eq.${trade.sender_id}))&select=id`,
      { headers: restHeaders }
    );
    if (!blockResp.ok) {
      return jsonResponse(500, { error: "DB_ERROR", message: "Failed to verify block status" });
    }
    const blockData = await blockResp.json();
    if (Array.isArray(blockData) && blockData.length > 0) {
      return jsonResponse(400, {
        error: "RELATIONSHIP_BLOCKED",
        message: "Cannot accept trade: relationship is blocked",
      });
    }
  } catch (_e) {
    return jsonResponse(500, { error: "SERVER_ERROR", message: "Error checking block status" });
  }

  // 7. Verify source object exists and copy media into recipient folder
  const destinationKey = `${recipientUid}/received/${trade.id}.png`;

  // Fetch source media bytes
  let sourceBytes: ArrayBuffer;
  let contentType = "image/png";
  try {
    const sourceResp = await fetch(
      `${supabaseUrl}/storage/v1/object/authenticated/stamp-media/${trade.source_object_name}`,
      {
        headers: {
          "apikey": serviceRoleKey,
          "Authorization": `Bearer ${serviceRoleKey}`,
        },
      }
    );
    if (!sourceResp.ok) {
      // Try public endpoint fallback
      const pubResp = await fetch(
        `${supabaseUrl}/storage/v1/object/public/stamp-media/${trade.source_object_name}`
      );
      if (!pubResp.ok) {
        return jsonResponse(400, {
          error: "SOURCE_MEDIA_NOT_FOUND",
          message: "Source stamp media object not found in storage",
        });
      }
      sourceBytes = await pubResp.arrayBuffer();
      contentType = pubResp.headers.get("Content-Type") || "image/png";
    } else {
      sourceBytes = await sourceResp.arrayBuffer();
      contentType = sourceResp.headers.get("Content-Type") || "image/png";
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "STORAGE_READ_ERROR",
      message: "Failed to read source media object",
    });
  }

  // Upload recipient copy
  try {
    const uploadResp = await fetch(
      `${supabaseUrl}/storage/v1/object/stamp-media/${destinationKey}`,
      {
        method: "POST",
        headers: {
          "apikey": serviceRoleKey,
          "Authorization": `Bearer ${serviceRoleKey}`,
          "Content-Type": contentType,
          "x-upsert": "true",
        },
        body: sourceBytes,
      }
    );

    if (!uploadResp.ok && uploadResp.status !== 409) {
      const uploadErr = await uploadResp.text();
      return jsonResponse(500, {
        error: "MEDIA_COPY_FAILED",
        message: `Failed to create recipient-owned media copy: ${uploadErr}`,
      });
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "MEDIA_COPY_FAILED",
      message: "Network error copying stamp media",
    });
  }

  // 8. Authoritative Database Acceptance Transition
  try {
    const rpcResp = await fetch(`${supabaseUrl}/rest/v1/rpc/accept_stamp_trade`, {
      method: "POST",
      headers: {
        "apikey": anonKey || serviceRoleKey,
        "Authorization": `Bearer ${callerJwt}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        p_trade_id: trade.id,
        p_recipient_media_path: destinationKey,
      }),
    });

    if (!rpcResp.ok) {
      const errText = await rpcResp.text();
      return jsonResponse(rpcResp.status, {
        error: "ACCEPT_RPC_FAILED",
        message: `Failed to finalize trade acceptance: ${errText}`,
      });
    }

    const rpcResult = await rpcResp.json();

    // 9. Dispatch push notification to sender (best-effort)
    try {
      await fetch(`${supabaseUrl}/functions/v1/dispatch-push`, {
        method: "POST",
        headers: {
          "apikey": anonKey || serviceRoleKey,
          "Authorization": `Bearer ${callerJwt}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          event_type: "trade_accepted",
          entity_id: trade.id,
        }),
      });
    } catch (_pushErr) {
      // Non-blocking for trade acceptance
    }

    return jsonResponse(200, {
      success: true,
      trade_id: trade.id,
      status: "ACCEPTED",
      destination_key: destinationKey,
      recipient_media_path: destinationKey,
      destination_media_path: destinationKey,
      received_stamp_id: rpcResult.received_stamp_id,
    });
  } catch (err: any) {
    return jsonResponse(500, {
      error: "SERVER_ERROR",
      message: err?.message || "Error finalizing trade acceptance",
    });
  }
});
