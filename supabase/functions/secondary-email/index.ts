// Supabase Edge Function: manage secondary email addresses.
// Secrets required:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//   MAIL_SERVICE_BASE_URL
//   MAIL_SERVICE_API_KEY

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

type ActionBody = {
  action: "add" | "resend" | "verify" | "remove";
  email?: string;
  id?: string;
  code?: string;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return json({ error: "Unauthorized" }, 401);
  }
  const jwt = authHeader.replace("Bearer ", "").trim();

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
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

      // Check if email equals current user main email or another profile/user email
      if (user.email?.toLowerCase() === email) {
        return json({ error: "That email can't be added." }, 400);
      }

      const { data: existingUser } = await adminClient
        .from("profiles")
        .select("id")
        .ilike("email", email)
        .maybeSingle();

      if (existingUser) {
        return json({ error: "That email can't be added." }, 400);
      }

      const { data: existingSecondary } = await adminClient
        .from("user_emails")
        .select("id, user_id, verified_at")
        .ilike("email", email);

      if (existingSecondary?.some((e) => e.verified_at !== null || e.user_id === userId)) {
        return json({ error: "That email can't be added." }, 400);
      }

      // Rate limit check: max 5 sends per hour per user
      const oneHourAgo = new Date(Date.now() - 3600 * 1000).toISOString();
      const { count } = await adminClient
        .from("user_emails")
        .select("id", { count: "exact", head: true })
        .eq("user_id", userId)
        .gte("created_at", oneHourAgo);

      if ((count ?? 0) >= 10) {
        return json({ error: "Too many email requests. Try again later." }, 429);
      }

      const code = generate6DigitCode();
      const codeHash = await hashCode(code);
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      const { data: inserted, error: insertError } = await adminClient
        .from("user_emails")
        .insert({
          user_id: userId,
          email,
          code_hash: codeHash,
          code_expires_at: expiresAt,
          attempts: 0,
        })
        .select("id")
        .single();

      if (insertError) {
        return json({ error: "Failed to add email address." }, 500);
      }

      await sendVerificationEmail(email, code);
      return json({ ok: true, id: inserted.id });
    }

    case "resend": {
      const emailId = body.id;
      if (!emailId) {
        return json({ error: "Missing email id." }, 400);
      }

      const { data: record } = await adminClient
        .from("user_emails")
        .select("id, user_id, email, verified_at")
        .eq("id", emailId)
        .eq("user_id", userId)
        .maybeSingle();

      if (!record || record.verified_at !== null) {
        return json({ error: "Invalid email request." }, 400);
      }

      const code = generate6DigitCode();
      const codeHash = await hashCode(code);
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      await adminClient
        .from("user_emails")
        .update({
          code_hash: codeHash,
          code_expires_at: expiresAt,
          attempts: 0,
        })
        .eq("id", emailId);

      await sendVerificationEmail(record.email, code);
      return json({ ok: true });
    }

    case "verify": {
      const emailId = body.id;
      const code = body.code?.trim();
      if (!emailId || !code || code.length !== 6) {
        return json({ error: "Enter a valid 6-digit verification code." }, 400);
      }

      const { data: record } = await adminClient
        .from("user_emails")
        .select("id, user_id, code_hash, code_expires_at, attempts, verified_at")
        .eq("id", emailId)
        .eq("user_id", userId)
        .maybeSingle();

      if (!record || record.verified_at !== null) {
        return json({ error: "Invalid verification request." }, 400);
      }

      if (record.attempts >= 5) {
        return json({ error: "Too many failed attempts. Please request a new code." }, 400);
      }

      if (!record.code_expires_at || new Date(record.code_expires_at) < new Date()) {
        return json({ error: "Verification code has expired. Request a new code." }, 400);
      }

      const inputHash = await hashCode(code);
      if (inputHash !== record.code_hash) {
        await adminClient
          .from("user_emails")
          .update({ attempts: record.attempts + 1 })
          .eq("id", emailId);
        return json({ error: "Invalid verification code." }, 400);
      }

      await adminClient
        .from("user_emails")
        .update({
          verified_at: new Date().toISOString(),
          code_hash: null,
          code_expires_at: null,
        })
        .eq("id", emailId);

      return json({ ok: true });
    }

    case "remove": {
      const emailId = body.id;
      if (!emailId) {
        return json({ error: "Missing email id." }, 400);
      }

      await adminClient
        .from("user_emails")
        .delete()
        .eq("id", emailId)
        .eq("user_id", userId);

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

function isValidEmail(email: String): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email));
}

function generate6DigitCode(): string {
  const array = new Uint32Array(1);
  crypto.getRandomValues(array);
  return String(array[0] % 1000000).padStart(6, "0");
}

async function hashCode(code: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(code);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function sendVerificationEmail(toEmail: string, code: string) {
  const baseUrl = Deno.env.get("MAIL_SERVICE_BASE_URL")?.replace(/\/+$/, "");
  const apiKey = Deno.env.get("MAIL_SERVICE_API_KEY") ?? "";
  if (!baseUrl) return;

  await fetch(`${baseUrl}/send-mail`, {
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
  }).catch(() => {});
}
