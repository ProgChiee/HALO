import { readFile, realpath, lstat, appendFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
export const BUCKET = 'halo-learning-materials';
const MAX = 3 * 1024 * 1024;
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export function objectKey(reference, roots = '') {
  if (typeof reference !== 'string' || !reference.trim()) throw Error('INVALID_REFERENCE');
  let key = reference.replaceAll('\\', '/');
  if (key.startsWith('uploads/lessons/')) key = key.slice(16);
  else for (let root of roots.split(';').map(s => s.trim()).filter(Boolean)) {
    root = root.replaceAll('\\', '/').replace(/\/+$/, '') + '/';
    if (key.startsWith(root)) { key = key.slice(root.length); break; }
  }
  if (key.length > 512 || /[:%\u0000-\u001f\u007f]/.test(key) || key.split('/').some(s => !s.trim() || s === '.' || s === '..')) throw Error('INVALID_REFERENCE');
  return key;
}
function references(inventory) {
  if (!Array.isArray(inventory.attachments) || !Array.isArray(inventory.revisions) || !Array.isArray(inventory.history)) throw Error('COMPLETE_INVENTORY_REQUIRED');
  const refs = [...inventory.attachments];
  for (const row of [...inventory.revisions, ...inventory.history]) {
    const payload = typeof row.payload === 'string' ? JSON.parse(row.payload) : row.payload;
    if (!Array.isArray(payload?.files)) throw Error('INVALID_REVISION_INVENTORY');
    refs.push(...payload.files);
  }
  return refs;
}
async function bytesAt(root, key) {
  let source = root;
  for (const part of key.split('/')) {
    source = path.join(source, part);
    if ((await lstat(source)).isSymbolicLink()) throw Error('SYMLINK_SOURCE_REJECTED');
  }
  const actual = await realpath(source), relative = path.relative(root, actual);
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw Error('SOURCE_OUTSIDE_BACKUP');
  const info = await lstat(actual);
  if (!info.isFile() || info.size < 1 || info.size > MAX) throw Error('INVALID_SOURCE_SIZE');
  return readFile(actual);
}
export async function planMigration(backup, inventory, legacyRoots = '') {
  if ((await lstat(backup)).isSymbolicLink()) throw Error('SYMLINK_ROOT_REJECTED');
  const root = await realpath(backup), entries = new Map();
  const refs = references(inventory);
  for (const ref of refs) {
    const key = objectKey(ref.filePath, legacyRoots), bytes = await bytesAt(root, key);
    const detected = bytes.subarray(0, 5).equals(Buffer.from('%PDF-')) ? 'application/pdf'
      : bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])) ? 'image/png'
        : bytes.subarray(0, 3).equals(Buffer.from([255,216,255])) ? 'image/jpeg' : null;
    if (!detected || detected !== ref.fileType) throw Error('SOURCE_TYPE_MISMATCH');
    const entry = { key, sha256: hash(bytes), size: bytes.length, type: detected };
    if (entries.has(key) && entries.get(key).sha256 !== entry.sha256) throw Error('KEY_COLLISION');
    entries.set(key, entry);
  }
  return { root, referenceCount: refs.length, revisionCount: inventory.revisions.length, historyCount: inventory.history.length, entries: [...entries.values()] };
}
export async function applyMigration(plan, { url, secret, confirmBucket, journal }) {
  if (confirmBucket !== BUCKET) throw Error('EXPLICIT_BUCKET_CONFIRMATION_REQUIRED');
  const base = new URL(url);
  if ((base.protocol !== 'https:' && !(base.protocol === 'http:' && ['localhost','127.0.0.1','[::1]'].includes(base.hostname))) || base.username || base.password || base.search || base.hash || base.pathname !== '/' || !secret) throw Error('INVALID_STORAGE_CONFIGURATION');
  const headers = { apikey: secret };
  if (!secret.startsWith('sb_secret_')) headers.Authorization = `Bearer ${secret}`;
  async function request(route, options = {}) {
    return fetch(new URL('/storage/v1/' + route, base), { ...options, headers: { ...headers, ...options.headers }, redirect: 'manual', signal: AbortSignal.timeout(30000) });
  }
  async function bounded(response, maximum) {
    const reader = response.body.getReader(); const chunks = []; let total = 0;
    try { while (true) { const { done, value } = await reader.read(); if (done) break; total += value.length; if (total > maximum) throw Error('REMOTE_RESPONSE_TOO_LARGE'); chunks.push(Buffer.from(value)); } }
    finally { await reader.cancel(); }
    return Buffer.concat(chunks);
  }
  async function privateBucket() {
    const response = await request('bucket/' + BUCKET);
    if (!response.ok) throw Error('BUCKET_CHECK_FAILED');
    if (JSON.parse((await bounded(response, 65536)).toString()).public !== false) throw Error('BUCKET_MUST_BE_PRIVATE');
  }
  await privateBucket();
  let uploaded = 0, verified = 0;
  for (const entry of plan.entries) {
    // Re-read the backup on every run; checkpoints never substitute for checksum verification.
    const bytes = await bytesAt(plan.root, objectKey(entry.key));
    if (hash(bytes) !== entry.sha256) throw Error('BACKUP_CHANGED');
    const object = BUCKET + '/' + entry.key.split('/').map(encodeURIComponent).join('/');
    let remote = await request('object/authenticated/' + object);
    if (remote.status === 404) {
      await remote.body?.cancel();
      await privateBucket();
      const response = await request('object/' + object, { method: 'POST', headers: { 'Content-Type': entry.type, 'x-upsert': 'false' }, body: bytes });
      // A concurrent create is safe only if the subsequent read has identical bytes.
      if (!response.ok && response.status !== 409) throw Error('UPLOAD_FAILED');
      await response.body?.cancel();
      remote = await request('object/authenticated/' + object); uploaded++;
    }
    if (!remote.ok || hash(await bounded(remote, MAX)) !== entry.sha256) throw Error('REMOTE_CHECKSUM_MISMATCH');
    verified++;
    if (journal) await appendFile(journal, JSON.stringify({ key: entry.key, sha256: entry.sha256, verifiedAt: new Date().toISOString() }) + '\n', { mode: 0o600 });
  }
  return { uploaded, verified };
}
async function main() {
  const args = process.argv.slice(2), options = {};
  for (let i = 0; i < args.length; i++) { const flag = args[i]; if (flag === '--apply') options.apply = true; else if (flag.startsWith('--') && args[i+1] && !args[i+1].startsWith('--')) options[flag.slice(2)] = args[++i]; else throw Error('INVALID_ARGUMENT'); }
  if (!options.backup || !options.inventory) throw Error('BACKUP_AND_INVENTORY_REQUIRED');
  const inventory = JSON.parse(await readFile(options.inventory, 'utf8'));
  const plan = await planMigration(options.backup, inventory, options['legacy-roots'] || '');
  if (options.plan) await writeFile(options.plan, JSON.stringify(plan, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
  console.log(JSON.stringify({ mode: options.apply ? 'apply' : 'dry-run', objects: plan.entries.length, references: plan.referenceCount, revisions: plan.revisionCount, historicalSnapshots: plan.historyCount }));
  if (options.apply) console.log(JSON.stringify(await applyMigration(plan, { url: process.env.SUPABASE_URL, secret: process.env.SUPABASE_SECRET_KEY, confirmBucket: options['confirm-bucket'], journal: options.journal })));
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) main().catch(() => { console.error('Migration stopped. Check the inventory, backup, private bucket, configuration and checksums. No remote objects are overwritten or deleted.'); process.exitCode = 1; });
