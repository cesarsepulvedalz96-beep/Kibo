import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const target = new URL('.env', root);
if (existsSync(target)) {
  console.log('Ya existe .env. Se conservó su configuración.');
} else {
  const password = () => randomBytes(24).toString('base64url');
  const databasePassword = password();
  const values = {
    POSTGRES_PASSWORD: databasePassword,
    DB_PASSWORD: databasePassword,
    BOOTSTRAP_ADMIN_PASSWORD: password(),
    DEMO_USER_PASSWORD: password(),
  };
  const template = readFileSync(new URL('.env.example', root), 'utf8');
  const content = template.split('\n').map((line) => {
    const key = line.split('=')[0];
    return key in values ? `${key}=${values[key]}` : line;
  }).join('\n');
  writeFileSync(target, content, { mode: 0o600, flag: 'wx' });
  console.log(`Configuración local creada en ${fileURLToPath(target)}. Las contraseñas no se imprimen ni se versionan.`);
}
