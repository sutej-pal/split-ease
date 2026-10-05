// Supabase Edge Function: manage secondary email addresses.
// Secrets required:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//   MAIL_SERVICE_BASE_URL
//   MAIL_SERVICE_API_KEY
// Optional:
//   EMAIL_OTP_PEPPER  (HMAC pepper; service role key is used when this is unset)

import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

type ActionBody = {
  action: "add" | "resend" | "verify" | "remove";
  email?: string;
  id?: string;
  code?: string;
};

const SEND_LIMIT_PER_HOUR = 5;
const RESEND_COOLDOWN_MS = 60_000;
const MAX_ATTEMPTS = 5;

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return json({ error: "Unauthorized" }, 401);
  }
  const jwt = authHeader.replace("Bearer ", "").trim();

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    return json({ error: "Secondary email is not configured." }, 500);
  }
  const adminClient = createClient(supabaseUrl, serviceKey);

  const { data: { user }, error: authError } = await adminClient.auth.getUser(jwt);
  if (authError || !user) {
    return json({ error: "Unauthorized" }, 401);
  }

  const userId = user.id;
  const body = (await req.json().catch(() => ({}))) as ActionBody;
  const { action } = body;

  switch (action) {
    case "add": {
      const email = body.email?.trim().toLowerCase();
      if (!email || !isValidEmail(email)) {
        return json({ error: "Please enter a valid email address." }, 400);
      }
      if (user.email?.toLowerCase() === email) {
        return json({ error: "That email can't be added." }, 400);
      }

      const taken = await emailTaken(adminClient, email);
      if (taken === "error") {
        return json({ error: "Failed to add email address." }, 500);
      }
      if (taken === "taken") {
        return json({ error: "That email can't be added." }, 400);
      }

      if (await sendsInLastHour(adminClient, userId) >= SEND_LIMIT_PER_HOUR) {
        return json({ error: "Too many email requests. Try again later." }, 429);
      }

      const code = generate6DigitCode();
      const codeHash = await hashCode(code, serviceKey);
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      try {
        await sendVerificationEmail(email, code);
      } catch {
        return json({ error: "Could not send the verification email." }, 502);
      }

      const { data: inserted, error: insertError } = await adminClient
        .from("user_emails")
        .insert({
          user_id: userId,
          email,
          code_hash: codeHash,
          code_expires_at: expiresAt,
          attempts: 0,
          last_sent_at: new Date().toISOString(),
        })
        .select("id")
        .single();

      if (insertError || !inserted) {
        const duplicate = (insertError as { code?: string } | null)?.code === "23505";
        return json(
          { error: duplicate ? "That email can't be added." : "Failed to add email address." },
          duplicate ? 400 : 500,
        );
      }
      await recordSend(adminClient, userId);
      return json({ ok: true, id: inserted.id });
    }

    case "resend": {
      const emailId = body.id;
      if (!emailId) {
        return json({ error: "Missing email id." }, 400);
      }

      const { data: record, error: loadError } = await adminClient
        .from("user_emails")
        .select("id, user_id, email, verified_at, last_sent_at")
        .eq("id", emailId)
        .eq("user_id", userId)
        .maybeSingle();

      if (loadError || !record || record.verified_at !== null) {
        return json({ error: "Invalid email request." }, 400);
      }

      const lastSent = record.last_sent_at ? new Date(record.last_sent_at).getTime() : 0;
      if (Date.now() - lastSent < RESEND_COOLDOWN_MS) {
        return json({ error: "Please wait a moment before requesting another code." }, 429);
      }
      if (await sendsInLastHour(adminClient, userId) >= SEND_LIMIT_PER_HOUR) {
        return json({ error: "Too many email requests. Try again later." }, 429);
      }

      const code = generate6DigitCode();
      const codeHash = await hashCode(code, serviceKey);
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      try {
        await sendVerificationEmail(record.email, code);
      } catch {
        return json({ error: "Could not send the verification email." }, 502);
      }

      const { error: updateError } = await adminClient
        .from("user_emails")
        .update({
          code_hash: codeHash,
          code_expires_at: expiresAt,
          attempts: 0,
          last_sent_at: new Date().toISOString(),
        })
        .eq("id", emailId)
        .eq("user_id", userId);

      if (updateError) {
        return json({ error: "Could not send the verification email." }, 500);
      }
      await recordSend(adminClient, userId);
      return json({ ok: true });
    }

    case "verify": {
      const emailId = body.id;
      const code = body.code?.trim();
      if (!emailId || !code || code.length !== 6) {
        return json({ error: "Enter a valid 6-digit verification code." }, 400);
      }

      const { data: record, error: loadError } = await adminClient
        .from("user_emails")
        .select("id, user_id, email, code_hash, code_expires_at, attempts, verified_at")
        .eq("id", emailId)
        .eq("user_id", userId)
        .maybeSingle();

      if (loadError || !record || record.verified_at !== null) {
        return json({ error: "Invalid verification request." }, 400);
      }
      if (record.attempts >= MAX_ATTEMPTS) {
        return json({ error: "Too many failed attempts. Please request a new code." }, 400);
      }
      if (!record.code_expires_at || new Date(record.code_expires_at) < new Date()) {
        return json({ error: "Verification code has expired. Request a new code." }, 400);
      }

      const inputHash = await hashCode(code, serviceKey);
      if (!safeEqual(inputHash, record.code_hash ?? "")) {
        await adminClient
          .from("user_emails")
          .update({ attempts: record.attempts + 1 })
          .eq("id", emailId)
          .eq("user_id", userId)
          .eq("attempts", record.attempts);
        return json({ error: "Invalid verification code." }, 400);
      }

      const taken = await emailTaken(adminClient, record.email, emailId);
      if (taken !== "free") {
        return json({ error: "That email can't be added." }, 400);
      }

      const { data: updated, error: updateError } = await adminClient
        .from("user_emails")
        .update({
          verified_at: new Date().toISOString(),
          code_hash: null,
          code_expires_at: null,
        })
        .eq("id", emailId)
        .eq("user_id", userId)
        .is("verified_at", null)
        .select("id");

      if (updateError || !updated || updated.length === 0) {
        return json({ error: "That email can't be added." }, 400);
      }
      return json({ ok: true });
    }

    case "remove": {
      const emailId = body.id;
      if (!emailId) {
        return json({ error: "Missing email id." }, 400);
      }

      const { error: deleteError } = await adminClient
        .from("user_emails")
        .delete()
        .eq("id", emailId)
        .eq("user_id", userId);

      if (deleteError) {
        return json({ error: "Could not remove that email." }, 500);
      }
      return json({ ok: true });
    }

    default:
      return json({ error: "Invalid action" }, 400);
  }
});

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function isValidEmail(email: string): boolean {
  return /^[^\s@%]+@[^\s@%]+\.[^\s@%]+$/.test(email);
}

/** Escapes LIKE wildcards so an address matches itself only. */
function ilikeExact(value: string): string {
  return value.replaceAll("\\", "\\\\").replaceAll("%", "\\%").replaceAll("_", "\\_");
}

type Taken = "free" | "taken" | "error";

async function emailTaken(
  adminClient: SupabaseClient,
  email: string,
  ignoreRowId?: string,
): Promise<Taken> {
  const { data: registered, error: registeredError } = await adminClient.rpc(
    "auth_email_registered",
    { p_email: email },
  );
  if (registeredError) return "error";
  if (registered === true) return "taken";

  const { data: profiles, error: profileError } = await adminClient
    .from("profiles")
    .select("id")
    .ilike("email", ilikeExact(email))
    .limit(1);
  if (profileError) return "error";
  if (profiles && profiles.length > 0) return "taken";

  let secondaryQuery = adminClient
    .from("user_emails")
    .select("id")
    .ilike("email", ilikeExact(email))
    .limit(1);
  if (ignoreRowId) {
    secondaryQuery = secondaryQuery.neq("id", ignoreRowId);
  }
  const { data: secondary, error: secondaryError } = await secondaryQuery;
  if (secondaryError) return "error";
  if (secondary && secondary.length > 0) return "taken";
  return "free";
}

async function sendsInLastHour(adminClient: SupabaseClient, userId: string): Promise<number> {
  const oneHourAgo = new Date(Date.now() - 3600 * 1000).toISOString();
  const { count, error } = await adminClient
    .from("user_email_send_log")
    .select("id", { count: "exact", head: true })
    .eq("user_id", userId)
    .gte("sent_at", oneHourAgo);
  if (error) return SEND_LIMIT_PER_HOUR;
  return count ?? 0;
}

async function recordSend(adminClient: SupabaseClient, userId: string) {
  await adminClient.from("user_email_send_log").insert({ user_id: userId });
}

function generate6DigitCode(): string {
  const array = new Uint32Array(1);
  crypto.getRandomValues(array);
  return String(array[0] % 1000000).padStart(6, "0");
}

async function hashCode(code: string, serviceKey: string): Promise<string> {
  const pepper = Deno.env.get("EMAIL_OTP_PEPPER") ?? serviceKey;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(pepper),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(code));
  return Array.from(new Uint8Array(sig)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

async function sendVerificationEmail(toEmail: string, code: string): Promise<void> {
  const baseUrl = Deno.env.get("MAIL_SERVICE_BASE_URL")?.replace(/\/+$/, "");
  const apiKey = Deno.env.get("MAIL_SERVICE_API_KEY") ?? "";
  if (!baseUrl) {
    throw new Error("Mail service is not configured.");
  }

  const response = await fetch(`${baseUrl}/send-mail`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(apiKey ? { "x-api-key": apiKey } : {}),
    },
    body: JSON.stringify({
      to: toEmail,
      subject: "Verify your email - SplitEase",
      text: `Your SplitEase email verification code is: ${code}. It expires in 10 minutes.`,
      fromName: "SplitEase",
    }),
  });
  if (!response.ok) {
    throw new Error("Mail send failed.");
  }
}
