#!/usr/bin/env node
/**
 * Apply docs/sql/migration_db.sql via the Supabase Management API.
 * Called by apply-supabase-schema.ps1. See docs/supabase-reset.md.
 */
const fs = require('fs');
const https = require('https');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const propsPath = path.join(repoRoot, 'local.properties');
const sqlPath = path.join(repoRoot, 'docs', 'sql', 'migration_db.sql');

function readProp(name) {
  if (!fs.existsSync(propsPath)) return '';
  const line = fs
    .readFileSync(propsPath, 'utf8')
    .split(/\r?\n/)
    .find((row) => new RegExp('^\\s*' + name + '\\s*=').test(row));
  if (!line) return '';
  return line.split('=').slice(1).join('=').trim();
}

const token = (process.env.SUPABASE_ACCESS_TOKEN || readProp('SUPABASE_ACCESS_TOKEN')).trim();
const supabaseUrl = (process.env.SUPABASE_URL || readProp('SUPABASE_URL')).trim().replace(/\/$/, '');
const projectRef =
  process.env.SUPABASE_PROJECT_REF ||
  (supabaseUrl.match(/https?:\/\/([a-z0-9]+)\.supabase\.co/i) || [])[1];

if (!token) {
  console.error('SUPABASE_ACCESS_TOKEN missing (env or local.properties).');
  process.exit(1);
}
if (!projectRef) {
  console.error('Could not resolve project ref from SUPABASE_URL / SUPABASE_PROJECT_REF.');
  process.exit(1);
}
if (!fs.existsSync(sqlPath)) {
  console.error('Missing canonical migration:', sqlPath);
  process.exit(1);
}

let sql = fs.readFileSync(sqlPath, 'utf8');
if (sql.charCodeAt(0) === 0xfeff) sql = sql.slice(1);
sql = sql.replace(/^\uFEFF/, '');
sql = sql.replace(
  /delete from storage\.objects[\s\S]*?name like '%\/cover\.jpg';/m,
  '-- skipped storage.objects delete (use Storage API / clear-supabase.ps1)',
);

function postQuery(query) {
  const body = JSON.stringify({ query });
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: 'api.supabase.com',
        path: `/v1/projects/${projectRef}/database/query`,
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => {
          data += chunk;
        });
        res.on('end', () => {
          if (res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 2000)}`));
            return;
          }
          resolve({ status: res.statusCode, data });
        });
      },
    );
    req.on('error', reject);
    req.setTimeout(180000, () => req.destroy(new Error('timeout')));
    req.write(body);
    req.end();
  });
}

(async () => {
  console.log(`Applying migration_db.sql to project ${projectRef} (${sql.length} chars) ...`);
  const applied = await postQuery(sql);
  console.log(`Apply HTTP ${applied.status}`);
  try {
    await postQuery("notify pgrst, 'reload schema';");
    console.log('PostgREST schema cache reload notified.');
  } catch (err) {
    console.warn('Schema reload warning:', err.message);
  }
  const verify = await postQuery(`
select json_build_object(
  'activity_events_exists', to_regclass('public.activity_events') is not null,
  'auth_users', (select count(*)::int from auth.users),
  'profiles', (select count(*)::int from public.profiles),
  'groups', (select count(*)::int from public.groups),
  'expenses', (select count(*)::int from public.expenses),
  'activity_events', (select count(*)::int from public.activity_events)
) as status;
`);
  console.log(verify.data);
  console.log('Done.');
})().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
