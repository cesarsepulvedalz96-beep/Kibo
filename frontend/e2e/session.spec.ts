import { test, expect, type Page } from '@playwright/test';

const accounts = [
  {
    email: process.env.BOOTSTRAP_ADMIN_EMAIL!,
    password: process.env.BOOTSTRAP_ADMIN_PASSWORD!,
    path: '/admin/businesses',
  },
  { email: process.env.DEMO_USER_EMAIL!, password: process.env.DEMO_USER_PASSWORD!, path: '/' },
];

async function signIn(page: Page, account: (typeof accounts)[number]) {
  await page.getByLabel('Correo electrónico').fill(account.email);
  await page.getByLabel('Contraseña', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page).toHaveURL(`http://localhost:5173${account.path}`);
  await expect(page.locator('.account-name')).not.toBeEmpty();
}

test('admin and merchant can switch sessions without reloading; account menu dismisses outside and with Escape', async ({
  page,
}) => {
  const errors: string[] = [];
  let documents = 0;
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame()) documents++;
  });
  await page.goto('/login');
  for (const account of accounts) {
    await signIn(page, account);
    const menu = page.locator('.account-menu');
    await menu.locator('summary').click();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
    await page.locator('h1').click();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeHidden();
    await menu.locator('summary').click();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeHidden();
    await menu.locator('summary').click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL('http://localhost:5173/login');
    await expect(page.locator('.app-shell')).toHaveCount(0);
    expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401);
  }
  expect(documents).toBe(1);
  expect(errors).toEqual([]);
});

test('logout renews obsolete CSRF and invalidates the server session without reloading', async ({
  page,
}) => {
  await page.goto('/login');
  await signIn(page, accounts[1]);
  // Rotate the server session as another tab would, leaving the page token obsolete.
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const rotated = await page.request.post('/api/v1/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email: accounts[1].email, password: accounts[1].password },
  });
  expect(rotated.status()).toBe(200);
  await page.locator('.account-menu summary').click();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL('http://localhost:5173/login');
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('login switches background, contrasting logo, theme and language', async ({ page }) => {
  await page.goto('/login');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.locator('img[src="/logos/logo-completo-oscuro.webp"]')).toBeVisible();
  expect(await page.evaluate(() => getComputedStyle(document.body).backgroundImage)).toContain(
    'fondo-claro.webp',
  );

  await page.getByRole('button', { name: 'Tema de la interfaz' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.locator('img[src="/logos/logo-completo-claro.webp"]')).toBeVisible();
  expect(await page.evaluate(() => getComputedStyle(document.body).backgroundImage)).toContain(
    'fondo-oscuro.webp',
  );

  await page.getByLabel('Idioma', { exact: true }).selectOption('en');
  await expect(page.locator('#login-language')).toHaveValue('en');
  await page.setViewportSize({ width: 320, height: 720 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
});

test('admin navigation stays in the sidebar and exposes appearance preferences', async ({
  page,
}) => {
  await page.goto('/login');
  await signIn(page, accounts[0]);

  const adminLinks = ['Empresas', 'Usuarios', 'Planes', 'Suscripciones', 'Pagos', 'Preferencias'];
  const sidebar = page.locator('.sidebar nav');
  await expect(page.locator('.sidebar img[src="/logos/logo-oscuro.webp"]')).toBeVisible();
  for (const name of adminLinks)
    await expect(sidebar.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.locator('.admin-navigation')).toHaveCount(0);

  await sidebar.getByRole('link', { name: 'Preferencias', exact: true }).click();
  await expect(page).toHaveURL('http://localhost:5173/admin/preferences');
  await expect(page.getByRole('heading', { name: 'Apariencia' })).toBeVisible();
  await expect(
    page.getByRole('group', { name: 'Tema de la interfaz' }).getByRole('button'),
  ).toHaveCount(2);
  await expect(
    page.getByRole('group', { name: 'Color principal' }).getByRole('button'),
  ).toHaveCount(7);

  await page.getByRole('button', { name: 'Oscuro', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.locator('.sidebar img[src="/logos/logo-claro.webp"]')).toBeVisible();
  await page.getByLabel('Idioma', { exact: true }).selectOption('en');
  await expect(page.getByRole('heading', { name: 'Appearance' })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Open menu' }).click();
  await expect(page.locator('.sidebar')).toHaveClass(/sidebar--open/);
  await expect(sidebar.getByRole('link', { name: 'Businesses', exact: true })).toBeVisible();
  await expect(sidebar.getByRole('link', { name: 'Preferences', exact: true })).toBeVisible();
});
