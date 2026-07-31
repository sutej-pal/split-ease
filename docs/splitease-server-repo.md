# SplitEase Server (separate repo)

Android app and SplitEase Server are **separate Git repositories** under one parent folder.

| | Android | SplitEase Server |
|---|---|---|
| Local path | `C:\splitease\app` | `C:\splitease\server` |
| GitHub | your SplitEase app repo | https://github.com/sutej-pal/mail-service (rename to `splitease-server` recommended) |
| Role | Compose app + Supabase client | OTP / mail + invite bridge |

## Local run

```bash
cd C:\splitease\server
npm install
npm start
```

Uses Brevo HTTPS when `BREVO_API_KEY` is set; otherwise Nodemailer SMTP.

Android uses `MAIL_SERVICE_BASE_URL` / `MAIL_SERVICE_API_KEY` — point them at this server.
