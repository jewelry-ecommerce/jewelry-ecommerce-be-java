import { generateKeyPairSync } from 'node:crypto';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const directory = resolve(repository, 'deploy/secrets');
const privatePath = resolve(directory, 'jwt-private.pem');
const publicPath = resolve(directory, 'jwt-public.pem');
if (existsSync(privatePath) || existsSync(publicPath)) {
  throw new Error('JWT keys already exist. Do not overwrite keys used by an existing deployment.');
}
const keys = generateKeyPairSync('rsa', { modulusLength: 2048,
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' } });
mkdirSync(directory, { recursive: true, mode: 0o700 });
writeFileSync(privatePath, keys.privateKey, { encoding: 'utf8', mode: 0o600, flag: 'wx' });
writeFileSync(publicPath, keys.publicKey, { encoding: 'utf8', mode: 0o600, flag: 'wx' });
console.log('Created JWT keys locally (never printed; do not commit):');
console.log(privatePath);
console.log(publicPath);
