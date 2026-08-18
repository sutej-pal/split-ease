// Supabase Edge Function: notify group members via FCM when expenses/payments change.
// Secrets (Dashboard → Edge Functions → Secrets):
//   FIREBASE_SERVICE_ACCOUNT_JSON  — full service account JSON string
// Deploy: supabase functions deploy notify-group-members --no-verify-jwt
// (or keep JWT and pass service role from Database Webhook Authorization header)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

type NotifyBody = {
  type?: string;
  table?: string;
  record?: Record<string, unknown> | null;
  old_record?: Record<string, unknown> | null;
  /** Explicit fields when not using a Database Webhook envelope. */
  group_id?: string;
  actor_user_id?: string;
  event?: string;
  title?: string;
  body?: string;
  expense_id?: string;
  payment_id?: string;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const saJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!saJson) {
    return json({ ok: false, error: "missing_firebase_sa" }, 500);
  }

  const payload = (await req.json()) as NotifyBody;
  const record = payload.record ?? null;
  const oldRecord = payload.old_record ?? null;
  const table = payload.table ?? (payload.expense_id ? "expenses" : "payments");

  const groupId =
    (record?.group_id as string | undefined) ??
    (oldRecord?.group_id as string | undefined) ??
    payload.group_id;
  if (!groupId) {
    return json({ ok: true, skipped: "no_group_id" });
  }

  const actorUserId =
    payload.actor_user_id ??
    (table === "payments"
      ? ((record?.from_user_id as string | undefined) ??
        (oldRecord?.from_user_id as string | undefined))
      : ((record?.paid_by_user_id as string | undefined) ??
        (oldRecord?.paid_by_user_id as string | undefined)));

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: members, error: membersError } = await supabase
    .from("group_members")
    .select("user_id")
    .eq("group_id", groupId);
  if (membersError) {
    return json({ ok: false, error: membersError.message }, 500);
  }

  const recipientIds = (members ?? [])
    .map((m) => m.user_id as string)
    .filter((id) => id && id !== actorUserId);
  if (recipientIds.length === 0) {
    return json({ ok: true, sent: 0 });
  }

  const { data: prefsRows } = await supabase
    .from("notification_prefs")
    .select("user_id, mute_all, muted_group_ids")
    .in("user_id", recipientIds);
  const mutedIds = new Set<string>();
  for (const row of prefsRows ?? []) {
    const userId = row.user_id as string;
    if (row.mute_all) {
      mutedIds.add(userId);
      continue;
    }
    const mutedGroups = (row.muted_group_ids as string[] | null) ?? [];
    if (mutedGroups.includes(groupId)) mutedIds.add(userId);
  }
  const notifyIds = recipientIds.filter((id) => !mutedIds.has(id));
  if (notifyIds.length === 0) {
    return json({ ok: true, sent: 0, reason: "all_muted" });
  }

  const { data: tokens, error: tokensError } = await supabase
    .from("device_tokens")
    .select("token,user_id")
    .in("user_id", notifyIds);
  if (tokensError) {
    return json({ ok: false, error: tokensError.message }, 500);
  }
  if (!tokens?.length) {
    return json({ ok: true, sent: 0, reason: "no_tokens" });
  }

  const { data: group } = await supabase
    .from("groups")
    .select("name")
    .eq("id", groupId)
    .maybeSingle();
  const groupName = (group?.name as string | undefined) ?? "Group";

  let actorName = "Someone";
  if (actorUserId) {
    const { data: profile } = await supabase
      .from("profiles")
      .select("display_name")
      .eq("id", actorUserId)
      .maybeSingle();
    if (profile?.display_name) actorName = profile.display_name as string;
  }

  const event = (payload.event ?? payload.type ?? "UPDATE").toUpperCase();
  const description =
    (record?.description as string | undefined) ??
    (record?.note as string | undefined) ??
    (oldRecord?.description as string | undefined) ??
    "";
  const amount =
    (record?.amount as string | undefined) ??
    (oldRecord?.amount as string | undefined) ??
    "";
  const currency =
    (record?.currency_code as string | undefined) ??
    (oldRecord?.currency_code as string | undefined) ??
    "";

  const isPayment = table === "payments";
  const action =
    event.includes("DELETE")
      ? isPayment
        ? "removed a payment"
        : "deleted an expense"
      : event.includes("INSERT") || event.includes("CREATE")
        ? isPayment
          ? "recorded a payment"
          : "added an expense"
        : isPayment
          ? "updated a payment"
          : "updated an expense";

  const title = payload.title ?? groupName;
  const body =
    payload.body ??
    [actorName, action, description ? `“${description}”` : null, amount ? `· ${currency} ${amount}` : null]
      .filter(Boolean)
      .join(" ");

  const accessToken = await getFcmAccessToken(saJson);
  const projectId = JSON.parse(saJson).project_id as string;
  const expenseId = String(payload.expense_id ?? record?.id ?? oldRecord?.id ?? "");
  const paymentId = String(
    payload.payment_id ?? (isPayment ? record?.id ?? oldRecord?.id ?? "" : ""),
  );

  let sent = 0;
  const staleTokens: string[] = [];
  for (const row of tokens) {
    const token = row.token as string;
    const res = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            // Data-only so Android always delivers to SplitEaseMessagingService,
            // which posts the tray item with a PendingIntent that opens the group.
            data: {
              groupId,
              title,
              body,
              type: isPayment ? "payment" : "expense",
              expenseId,
              paymentId,
            },
            android: {
              priority: "HIGH",
            },
          },
        }),
      },
    );
    if (res.ok) {
      sent += 1;
      continue;
    }
    const errJson = await res.json().catch(() => ({})) as {
      error?: { status?: string; details?: Array<{ errorCode?: string }> };
    };
    const unregistered =
      res.status === 404 ||
      errJson.error?.status === "NOT_FOUND" ||
      errJson.error?.details?.some((d) => d.errorCode === "UNREGISTERED");
    if (unregistered) staleTokens.push(token);
  }

  if (staleTokens.length > 0) {
    await supabase.from("device_tokens").delete().in("token", staleTokens);
  }

  return json({ ok: true, sent, pruned: staleTokens.length });
});

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function getFcmAccessToken(saJson: string): Promise<string> {
  const sa = JSON.parse(saJson);
  const now = Math.floor(Date.now() / 1000);
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  const claim = btoa(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const sig = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  const jwt = `${unsigned}.${sig}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const tokenJson = await tokenRes.json();
  if (!tokenJson.access_token) {
    throw new Error(`FCM auth failed: ${JSON.stringify(tokenJson)}`);
  }
  return tokenJson.access_token as string;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = atob(b64);
  const buf = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) buf[i] = raw.charCodeAt(i);
  return buf.buffer;
}
