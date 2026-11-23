// Supabase Edge Function: ctecs-sharing-code
// Rate-limited validation for PMC Project Codes & Register Sharing Codes.
// Deploy via: supabase functions deploy ctecs-sharing-code

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const RATE_LIMIT_WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 20;
const ipHits = new Map<string, { count: number; resetAt: number }>();

function checkRateLimit(clientIp: string): boolean {
  const now = Date.now();
  const entry = ipHits.get(clientIp);
  if (!entry || now >= entry.resetAt) {
    ipHits.set(clientIp, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
    return true;
  }
  if (entry.count >= MAX_REQUESTS_PER_WINDOW) {
    return false;
  }
  entry.count += 1;
  return true;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  const clientIp = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  if (!checkRateLimit(clientIp)) {
    return new Response(JSON.stringify({ error: "Rate limit exceeded. Try again shortly." }), {
      status: 429,
      headers: { "Content-Type": "application/json" },
    });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const authHeader = req.headers.get("Authorization") ?? "";

  const client = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: authHeader ? { Authorization: authHeader } : {} },
  });

  try {
    const body = await req.json();
    const kind = String(body?.kind ?? "project_code");
    const code = String(body?.code ?? "").trim().toUpperCase();

    if (!code || code.length < 6) {
      return new Response(JSON.stringify({ valid: false, error: "Code is required." }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (kind === "project_code") {
      const { data, error } = await client.rpc("validate_project_code", { p_code: code });
      if (error) {
        return new Response(JSON.stringify({ valid: false, error: error.message }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify(data), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Register sharing code lookup (strictly read-only; requires authenticated approved project member)
    const { data, error } = await client
      .from("site_sharing_codes")
      .select("register_id, project_id, sharing_code, expires_at, is_revoked")
      .eq("sharing_code", code)
      .maybeSingle();

    if (error || !data || data.is_revoked || (data.expires_at && new Date(data.expires_at) <= new Date())) {
      return new Response(JSON.stringify({ valid: false, error: "Sharing code is invalid, revoked, or expired." }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ valid: true, ...data, access_mode: "READ_ONLY" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ valid: false, error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
