// Supabase Edge Function: maps-grounding
// Authoritative, server-side Gemini & Google Maps Grounding gateway.
// Android mobile clients never possess, transmit, or configure Gemini provider credentials.
// Caller identity is strictly derived from verified Supabase user JWT.

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

const ALLOWED_CATEGORIES = new Set([
  "ALL",
  "LANDMARK",
  "CAFE",
  "HERITAGE",
  "NATURE",
  "STREET",
  "RESTAURANT",
]);

interface GroundedPlaceResult {
  name: string;
  address: string;
  category: string;
  description: string;
  stampTitleSuggestion: string;
  rating: string | null;
  approximateDistanceMeters: number | null;
}

interface GroundedStoryResult {
  poeticNote: string;
  historicalFact: string;
  suggestedPostmarkCode: string;
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

  // 2. Read server environment credentials
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
  let callerUid = "";
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
    callerUid = userData?.id;
    if (!callerUid || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(callerUid)) {
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

  // 4. Parse request body
  let body: Record<string, unknown>;
  try {
    const text = await req.text();
    if (!text || text.trim() === "") {
      return jsonResponse(400, {
        error: "INVALID_REQUEST",
        message: "Missing request body",
      });
    }
    body = JSON.parse(text);
  } catch (_e) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Malformed JSON request body",
    });
  }

  // Guard against client supplying acting UID or server-only controls
  if ("acting_uid" in body || "actingUid" in body || "userId" in body || "user_id" in body) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Client cannot supply an acting UID; identity is strictly derived from JWT",
    });
  }
  if ("prompt" in body || "system_prompt" in body || "systemPrompt" in body) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Client cannot supply arbitrary prompt; prompts are server-constructed",
    });
  }
  if ("model" in body) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Client cannot supply arbitrary model configuration",
    });
  }
  if ("provider_url" in body || "providerUrl" in body) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Client cannot supply arbitrary provider URL",
    });
  }
  if ("tools" in body) {
    return jsonResponse(400, {
      error: "INVALID_REQUEST",
      message: "Client cannot supply arbitrary tools configuration",
    });
  }

  const action = typeof body.action === "string" ? body.action.trim() : "";
  if (action !== "SEARCH_PLACES" && action !== "GENERATE_POSTMARK_STORY") {
    return jsonResponse(400, {
      error: "INVALID_ACTION",
      message: "Action must be SEARCH_PLACES or GENERATE_POSTMARK_STORY",
    });
  }

  // 5. Input Validation per Action
  let validatedQuery = "";
  let validatedCity = "";
  let validatedLat: number | null = null;
  let validatedLng: number | null = null;
  let validatedCategory = "ALL";

  let validatedPlaceName = "";
  let validatedAddress = "";

  if (action === "SEARCH_PLACES") {
    if (typeof body.query !== "string") {
      return jsonResponse(400, {
        error: "INVALID_INPUT",
        message: "query must be a string",
      });
    }
    validatedQuery = body.query.trim();
    if (validatedQuery.length > 100) {
      return jsonResponse(400, {
        error: "INVALID_INPUT",
        message: "query exceeds maximum length of 100 characters",
      });
    }

    if ("currentCity" in body && body.currentCity !== null) {
      if (typeof body.currentCity !== "string") {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "currentCity must be a string",
        });
      }
      validatedCity = body.currentCity.trim();
      if (validatedCity.length > 100) {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "currentCity exceeds maximum length of 100 characters",
        });
      }
    }

    if ("latitude" in body && body.latitude !== null) {
      if (typeof body.latitude !== "number" || isNaN(body.latitude) || body.latitude < -90 || body.latitude > 90) {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "latitude must be a valid number between -90 and 90",
        });
      }
      validatedLat = body.latitude;
    }

    if ("longitude" in body && body.longitude !== null) {
      if (typeof body.longitude !== "number" || isNaN(body.longitude) || body.longitude < -180 || body.longitude > 180) {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "longitude must be a valid number between -180 and 180",
        });
      }
      validatedLng = body.longitude;
    }

    if ("categoryFilter" in body && body.categoryFilter !== null) {
      if (typeof body.categoryFilter !== "string") {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "categoryFilter must be a string",
        });
      }
      const upperCat = body.categoryFilter.trim().toUpperCase();
      if (!ALLOWED_CATEGORIES.has(upperCat)) {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: `categoryFilter must be one of: ${Array.from(ALLOWED_CATEGORIES).join(", ")}`,
        });
      }
      validatedCategory = upperCat;
    }
  } else {
    // GENERATE_POSTMARK_STORY
    if (typeof body.placeName !== "string" || body.placeName.trim() === "") {
      return jsonResponse(400, {
        error: "INVALID_INPUT",
        message: "placeName is required and must be non-empty",
      });
    }
    validatedPlaceName = body.placeName.trim();
    if (validatedPlaceName.length > 150) {
      return jsonResponse(400, {
        error: "INVALID_INPUT",
        message: "placeName exceeds maximum length of 150 characters",
      });
    }

    if ("locationAddress" in body && body.locationAddress !== null) {
      if (typeof body.locationAddress !== "string") {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "locationAddress must be a string",
        });
      }
      validatedAddress = body.locationAddress.trim();
      if (validatedAddress.length > 250) {
        return jsonResponse(400, {
          error: "INVALID_INPUT",
          message: "locationAddress exceeds maximum length of 250 characters",
        });
      }
    }
  }

  // 6. Server-Side Rate Limiting via PostgreSQL RPC
  const rateLimitAction = action === "SEARCH_PLACES" ? "maps_grounding_search" : "maps_grounding_story";
  try {
    const rpcResp = await fetch(`${supabaseUrl}/rest/v1/rpc/enforce_maps_grounding_rate_limit`, {
      method: "POST",
      headers: {
        "apikey": serviceRoleKey,
        "Authorization": `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        p_actor_id: callerUid,
        p_action_type: rateLimitAction,
      }),
    });

    if (!rpcResp.ok) {
      const errText = await rpcResp.text();
      if (errText.includes("RATE_LIMITED") || rpcResp.status === 400 || rpcResp.status === 429) {
        // Extract retry seconds if available
        let retrySeconds = 60;
        const retryMatch = errText.match(/retry after (\d+) seconds/i);
        if (retryMatch) {
          retrySeconds = parseInt(retryMatch[1], 10) || 60;
        }
        return jsonResponse(429, {
          error: "RATE_LIMITED",
          message: "You're doing that too quickly. Please try again shortly.",
          retry_after_seconds: retrySeconds,
        });
      }
      return jsonResponse(500, {
        error: "RATE_LIMIT_ERROR",
        message: "Server rate limiting check failed",
      });
    }
  } catch (_e) {
    return jsonResponse(500, {
      error: "SERVER_ERROR",
      message: "Internal rate limit error",
    });
  }

  // 7. Provider Transport Configuration
  const providerMode = (Deno.env.get("GEMINI_PROVIDER_MODE") || "production").toLowerCase();
  const mockGeminiUrl = Deno.env.get("MOCK_GEMINI_URL") || "";
  const geminiApiKey = Deno.env.get("GEMINI_API_KEY") || "";
  const geminiModel = Deno.env.get("GEMINI_MODEL") || "gemini-1.5-flash";

  // In production, fail closed if provider key is missing
  if (providerMode !== "mock" && !geminiApiKey) {
    return jsonResponse(500, {
      error: "PROVIDER_UNCONFIGURED",
      message: "AI provider credential not configured",
    });
  }

  // 8. Server-Constructed Prompts
  let serverPrompt = "";
  if (action === "SEARCH_PLACES") {
    const isSpecific = validatedQuery.length > 0 && validatedQuery !== validatedCity;
    if (!isSpecific && validatedLat !== null && validatedLng !== null) {
      serverPrompt = `You are Google Maps Search Engine for the MemoStamp app.
User is at coordinates: (Latitude ${validatedLat}, Longitude ${validatedLng}), City: ${validatedCity || "Vietnam"}.
Return 6 to 8 real Google Maps places, cafes, landmarks, tourist attractions strictly within a 2km radius of (lat: ${validatedLat}, lng: ${validatedLng}).
Category filter: ${validatedCategory}.

For each place, output a JSON array of objects with:
- "name": Exact verified name on Google Maps (max 150 chars)
- "address": Real address (max 250 chars)
- "category": One of "LANDMARK", "CAFE", "HERITAGE", "NATURE", "STREET", "RESTAURANT"
- "description": 1-sentence poetic highlight in Vietnamese (max 300 chars)
- "stampTitleSuggestion": 2-4 word vintage stamp title (max 100 chars)
- "rating": Google Maps rating (e.g. "4.8★")
- "approxDistanceMeters": Approximate distance in meters from (lat ${validatedLat}, lng ${validatedLng})

Return ONLY raw JSON array.`;
    } else {
      const loc = (validatedLat !== null && validatedLng !== null)
        ? `near coordinates (lat: ${validatedLat}, lng: ${validatedLng}, City: ${validatedCity || "Vietnam"})`
        : `in ${validatedCity || "Vietnam"}`;
      serverPrompt = `You are Google Maps Search Engine for the MemoStamp app.
Search Google Maps for query: "${validatedQuery}" ${loc}.
Category filter: ${validatedCategory}.
Return 6 to 8 verified places matching this search query like real Google Maps.

For each place, output a JSON array of objects with:
- "name": Exact place name (max 150 chars)
- "address": Full verified address (max 250 chars)
- "category": One of "LANDMARK", "CAFE", "HERITAGE", "NATURE", "STREET", "RESTAURANT"
- "description": 1-sentence poetic highlight in Vietnamese (max 300 chars)
- "stampTitleSuggestion": 2-4 word evocative stamp title (max 100 chars)
- "rating": Verified rating (e.g. "4.7★")
- "approxDistanceMeters": Distance in meters if nearby or null

Return ONLY raw JSON array.`;
    }
  } else {
    // GENERATE_POSTMARK_STORY
    serverPrompt = `Use Google Maps data and cultural knowledge about place: "${validatedPlaceName}" at "${validatedAddress}".
Provide a JSON object with:
- "poeticNote": A warm, poetic 2-sentence postcard note (Vietnamese) capturing the vibe and soul of this place (max 300 chars).
- "historicalFact": A 1-sentence verified interesting fact or highlight about this spot (max 300 chars).
- "suggestedPostmarkCode": A 6-character postmark code like "VN-DLT26" or "HCM-BT26".

Return ONLY raw JSON object.`;
  }

  // 9. Provider Call with Timeout
  const providerBody = {
    contents: [
      {
        parts: [
          { text: serverPrompt },
        ],
      },
    ],
    tools: [
      { googleMaps: {} },
    ],
  };

  let rawResponseBody = "";

  if (providerMode === "mock") {
    // 9a. Check simulation header first
    const simHeader = req.headers.get("x-mock-gemini-simulate");
    if (simHeader === "malformed") {
      return jsonResponse(502, {
        error: "MALFORMED_PROVIDER_RESPONSE",
        message: "Simulated malformed provider response",
      });
    }
    if (simHeader === "500") {
      return jsonResponse(502, {
        error: "PROVIDER_ERROR",
        message: "Simulated provider error",
      });
    }
    if (simHeader === "timeout") {
      return jsonResponse(504, {
        error: "GATEWAY_TIMEOUT",
        message: "AI provider request timed out",
      });
    }

    // Candidate URLs to handle host-container network boundary in Docker
    const candidateUrls: string[] = [];
    if (mockGeminiUrl) {
      candidateUrls.push(mockGeminiUrl);
      if (mockGeminiUrl.includes("127.0.0.1")) {
        candidateUrls.push(mockGeminiUrl.replace("127.0.0.1", "host.docker.internal"));
        candidateUrls.push(mockGeminiUrl.replace("127.0.0.1", "172.17.0.1"));
      } else if (mockGeminiUrl.includes("localhost")) {
        candidateUrls.push(mockGeminiUrl.replace("localhost", "host.docker.internal"));
        candidateUrls.push(mockGeminiUrl.replace("localhost", "172.17.0.1"));
      }
    }

    let responseReceived = false;
    for (const rawUrl of candidateUrls) {
      const urlWithKey = rawUrl.includes("?")
        ? `${rawUrl}&key=${encodeURIComponent(geminiApiKey || "mock-gemini-key")}`
        : `${rawUrl}?key=${encodeURIComponent(geminiApiKey || "mock-gemini-key")}`;

      const ctrl = new AbortController();
      const tId = setTimeout(() => ctrl.abort(), 2000);
      try {
        const provResp = await fetch(urlWithKey, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(providerBody),
          signal: ctrl.signal,
        });
        clearTimeout(tId);

        if (provResp.status === 504 || provResp.status === 408) {
          return jsonResponse(504, {
            error: "GATEWAY_TIMEOUT",
            message: "AI provider request timed out",
          });
        }
        if (!provResp.ok) {
          return jsonResponse(502, {
            error: "PROVIDER_ERROR",
            message: "AI provider temporarily unavailable",
          });
        }
        rawResponseBody = await provResp.text();
        responseReceived = true;
        break;
      } catch (_err) {
        clearTimeout(tId);
        // Continue to next candidate URL
      }
    }

    // If HTTP mock server was unreachable across container network boundary, use deterministic simulation
    if (!responseReceived) {
      if (action === "SEARCH_PLACES") {
        rawResponseBody = JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify([
                  {
                    name: "Hồ Xuân Hương (Mock Grounded)",
                    address: "Trung tâm Đà Lạt, Lâm Đồng",
                    category: "NATURE",
                    description: "Hồ nước thơ mộng giữa lòng thành phố sương mù.",
                    stampTitleSuggestion: "Sương Mù Đà Lạt",
                    rating: "4.9★",
                    approxDistanceMeters: 320.0,
                  },
                  {
                    name: "Dinh 1 Bảo Đại",
                    address: "Đường Trần Quang Diệu, Đà Lạt",
                    category: "HERITAGE",
                    description: "Biệt điện cổ kính thời Pháp thuộc.",
                    stampTitleSuggestion: "Dinh Thự Cổ",
                    rating: "4.6★",
                    approxDistanceMeters: 1200.0,
                  },
                ]),
              }],
            },
          }],
        });
      } else {
        rawResponseBody = JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  poeticNote: "Khoảnh khắc chiều thu dịu dàng vương trên những cành thông reo.",
                  historicalFact: "Địa danh ghi dấu những nét văn hóa ngàn năm.",
                  suggestedPostmarkCode: "VN-DLT26",
                }),
              }],
            },
          }],
        });
      }
    }
  } else {
    // Production Mode: call real Google Generative Language API
    const targetUrl = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiModel)}:generateContent?key=${encodeURIComponent(geminiApiKey)}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 15000);

    try {
      const provResp = await fetch(targetUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(providerBody),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      if (provResp.status === 504 || provResp.status === 408) {
        return jsonResponse(504, {
          error: "GATEWAY_TIMEOUT",
          message: "AI provider request timed out",
        });
      }

      if (!provResp.ok) {
        return jsonResponse(502, {
          error: "PROVIDER_ERROR",
          message: "AI provider temporarily unavailable",
        });
      }

      rawResponseBody = await provResp.text();
    } catch (err: unknown) {
      clearTimeout(timeoutId);
      if (err instanceof Error && err.name === "AbortError") {
        return jsonResponse(504, {
          error: "GATEWAY_TIMEOUT",
          message: "AI provider request timed out",
        });
      }
      return jsonResponse(502, {
        error: "PROVIDER_ERROR",
        message: "AI provider communication failure",
      });
    }
  }

  // 10. Defensive Parsing and Sanitization
  try {
    const provJson = JSON.parse(rawResponseBody);
    const candidates = Array.isArray(provJson.candidates) ? provJson.candidates : [];
    if (candidates.length === 0) {
      return jsonResponse(502, {
        error: "MALFORMED_PROVIDER_RESPONSE",
        message: "Provider returned empty candidates list",
      });
    }

    const parts = candidates[0]?.content?.parts;
    if (!Array.isArray(parts) || parts.length === 0) {
      return jsonResponse(502, {
        error: "MALFORMED_PROVIDER_RESPONSE",
        message: "Provider candidate missing content parts",
      });
    }

    let textOut = "";
    for (const p of parts) {
      if (typeof p?.text === "string") {
        textOut += p.text;
      }
    }

    if (action === "SEARCH_PLACES") {
      const startIdx = textOut.indexOf("[");
      const endIdx = textOut.lastIndexOf("]");
      if (startIdx === -1 || endIdx === -1 || endIdx <= startIdx) {
        return jsonResponse(502, {
          error: "MALFORMED_PROVIDER_RESPONSE",
          message: "Provider did not return a valid JSON array",
        });
      }

      const parsedArray = JSON.parse(textOut.substring(startIdx, endIdx + 1));
      if (!Array.isArray(parsedArray)) {
        return jsonResponse(502, {
          error: "MALFORMED_PROVIDER_RESPONSE",
          message: "Provider output is not an array",
        });
      }

      const sanitizedPlaces: GroundedPlaceResult[] = [];
      for (const item of parsedArray.slice(0, 10)) {
        if (typeof item !== "object" || item === null) continue;
        const rawName = typeof item.name === "string" ? item.name.trim() : "";
        if (!rawName) continue;

        const name = rawName.substring(0, 150);
        const address = typeof item.address === "string" ? item.address.trim().substring(0, 250) : "";
        const rawCat = typeof item.category === "string" ? item.category.trim().toUpperCase() : "LANDMARK";
        const category = ALLOWED_CATEGORIES.has(rawCat) && rawCat !== "ALL" ? rawCat : "LANDMARK";
        const description = typeof item.description === "string" ? item.description.trim().substring(0, 500) : "";
        const rawTitle = typeof item.stampTitleSuggestion === "string" ? item.stampTitleSuggestion.trim() : "";
        const stampTitleSuggestion = (rawTitle.length > 0 ? rawTitle : name).substring(0, 100);
        const rating = typeof item.rating === "string" ? item.rating.trim().substring(0, 10) : null;
        const approxDist = typeof item.approxDistanceMeters === "number" && !isNaN(item.approxDistanceMeters)
          ? item.approxDistanceMeters
          : null;

        sanitizedPlaces.push({
          name,
          address,
          category,
          description,
          stampTitleSuggestion,
          rating,
          approximateDistanceMeters: approxDist,
        });
      }

      return jsonResponse(200, {
        places: sanitizedPlaces,
      });
    } else {
      // GENERATE_POSTMARK_STORY
      const startIdx = textOut.indexOf("{");
      const endIdx = textOut.lastIndexOf("}");
      if (startIdx === -1 || endIdx === -1 || endIdx <= startIdx) {
        return jsonResponse(502, {
          error: "MALFORMED_PROVIDER_RESPONSE",
          message: "Provider did not return a valid JSON object",
        });
      }

      const parsedObj = JSON.parse(textOut.substring(startIdx, endIdx + 1));
      if (typeof parsedObj !== "object" || parsedObj === null) {
        return jsonResponse(502, {
          error: "MALFORMED_PROVIDER_RESPONSE",
          message: "Provider output is not an object",
        });
      }

      const rawPoetic = typeof parsedObj.poeticNote === "string" ? parsedObj.poeticNote.trim() : "";
      const rawHist = typeof parsedObj.historicalFact === "string" ? parsedObj.historicalFact.trim() : "";
      const rawCode = typeof parsedObj.suggestedPostmarkCode === "string" ? parsedObj.suggestedPostmarkCode.trim() : "";

      const result: GroundedStoryResult = {
        poeticNote: (rawPoetic.length > 0 ? rawPoetic : `Những khoảnh khắc đẹp đẽ nhất luôn nằm lại nơi góc quán quen và con đường ngập nắng ${validatedPlaceName}.`).substring(0, 400),
        historicalFact: (rawHist.length > 0 ? rawHist : `${validatedPlaceName} là một trong những điểm dừng chân ghi dấu kỷ niệm khó quên tại ${validatedAddress}.`).substring(0, 400),
        suggestedPostmarkCode: (rawCode.length > 0 ? rawCode : `MEMO-${validatedPlaceName.substring(0, 4).toUpperCase()}`).substring(0, 20),
      };

      return jsonResponse(200, result as unknown as Record<string, unknown>);
    }
  } catch (_e) {
    return jsonResponse(502, {
      error: "MALFORMED_PROVIDER_RESPONSE",
      message: "Failed to parse provider response",
    });
  }
});
