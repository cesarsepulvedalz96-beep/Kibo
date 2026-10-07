import { expect, request, test, type APIRequestContext, type Page } from '@playwright/test';
import { randomBytes, randomUUID } from 'node:crypto';

const suffix = randomBytes(5).toString('hex');
const email = `billing-${suffix}@stockflow.local`;
const temporaryPassword = randomBytes(24).toString('base64url');
const permanentPassword = randomBytes(24).toString('base64url');
const businessName = `Facturación E2E ${suffix}`;
const replacementPlanName = `Plan alternativo E2E ${suffix}`;

let admin: APIRequestContext;
let adminCsrf: string;
let businessId: string;
let planId: string;
let replacementPlanId: string;
let subscriptionId: string;

async function loginApi(context: APIRequestContext, username: string, password: string) {
  let csrf = await (await context.get('/api/v1/auth/csrf')).json();
  const response = await context.post('/api/v1/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email: username, password },
  });
  expect(response.status()).toBe(200);
  csrf = await (await context.get('/api/v1/auth/csrf')).json();
  return csrf.token as string;
}

async function loginPage(page: Page, password: string) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page
    .locator('form')
    .getByRole('button', { name: /Iniciar sesi/ })
    .click();
}

test.describe.serial('Usuarios y suscripciones', () => {
  test.beforeAll(async () => {
    admin = await request.newContext({ baseURL: 'http://localhost:8080' });
    adminCsrf = await loginApi(
      admin,
      process.env.BOOTSTRAP_ADMIN_EMAIL!,
      process.env.BOOTSTRAP_ADMIN_PASSWORD!,
    );

    const plan = await admin.post('/api/v1/admin/plans', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: {
        name: `Mensual E2E ${suffix}`,
        description: 'Plan temporal de prueba',
        monthlyPrice: 29990,
        billingPeriodDays: 30,
        gracePeriodDays: 3,
        active: true,
      },
    });
    expect(plan.status()).toBe(201);
    planId = (await plan.json()).id;

    const replacementPlan = await admin.post('/api/v1/admin/plans', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: {
        name: replacementPlanName,
        description: 'Plan alternativo de prueba',
        monthlyPrice: 49990,
        billingPeriodDays: 30,
        gracePeriodDays: 3,
        active: true,
      },
    });
    expect(replacementPlan.status()).toBe(201);
    replacementPlanId = (await replacementPlan.json()).id;

    // Omitting trialDays exercises the direct ACTIVE assignment path during onboarding.
    const business = await admin.post('/api/v1/admin/businesses', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: { name: businessName, taxId: '', active: true, planId },
    });
    expect(business.status()).toBe(201);
    const businessBody = await business.json();
    businessId = businessBody.id;
    expect(businessBody.subscriptionStatus).toBe('ACTIVE');

    const subscriptions = await admin.get(`/api/v1/admin/subscriptions?businessId=${businessId}`);
    expect(subscriptions.status()).toBe(200);
    const subscriptionBody = (await subscriptions.json()).items[0];
    subscriptionId = subscriptionBody.id;
    expect(subscriptionBody.status).toBe('ACTIVE');

    const user = await admin.post('/api/v1/admin/users', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: {
        businessId,
        email,
        displayName: 'Responsable E2E',
        temporaryPassword,
      },
    });
    expect(user.status()).toBe(201);
    expect((await user.json()).mustChangePassword).toBe(true);
  });

  test.afterAll(async () => {
    if (businessId)
      await admin.put(`/api/v1/admin/businesses/${businessId}`, {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: { name: businessName, taxId: '', active: false },
      });
    if (planId)
      await admin.put(`/api/v1/admin/plans/${planId}`, {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: {
          name: `Mensual E2E ${suffix}`,
          description: 'Plan temporal de prueba',
          monthlyPrice: 29990,
          billingPeriodDays: 30,
          gracePeriodDays: 3,
          active: false,
        },
      });
    if (replacementPlanId)
      await admin.put(`/api/v1/admin/plans/${replacementPlanId}`, {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: {
          name: replacementPlanName,
          description: 'Plan alternativo de prueba',
          monthlyPrice: 49990,
          billingPeriodDays: 30,
          gracePeriodDays: 3,
          active: false,
        },
      });
    await admin?.dispose();
  });

  test('el administrador cambia el plan desde la edición de la empresa', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#email').fill(process.env.BOOTSTRAP_ADMIN_EMAIL!);
    await page.locator('#password').fill(process.env.BOOTSTRAP_ADMIN_PASSWORD!);
    await page
      .locator('form')
      .getByRole('button', { name: /Iniciar sesi/ })
      .click();
    await expect(page).toHaveURL(/\/admin\/businesses$/);

    const row = page.getByRole('row').filter({ hasText: businessName });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: `Editar ${businessName}` }).click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('#business-plan').selectOption(replacementPlanId);
    await dialog.locator('button[type="submit"]').click();

    await expect(dialog).toBeHidden();
    await expect(row).toContainText(replacementPlanName);
    const subscriptions = await admin.get(`/api/v1/admin/subscriptions?businessId=${businessId}`);
    expect((await subscriptions.json()).items[0].planId).toBe(replacementPlanId);
  });

  test('exige cambiar la contraseña temporal antes de usar funciones comerciales', async ({
    page,
  }) => {
    const temporarySession = await request.newContext({ baseURL: 'http://localhost:8080' });
    await loginApi(temporarySession, email, temporaryPassword);
    const blocked = await temporarySession.get('/api/v1/products');
    expect(blocked.status()).toBe(403);
    expect((await blocked.json()).code).toBe('PASSWORD_CHANGE_REQUIRED');

    await loginPage(page, temporaryPassword);
    await expect(page).toHaveURL(/\/change-password$/);
    await page.locator('#new-password').fill(permanentPassword);
    await page.locator('#confirm-password').fill(permanentPassword);
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page).toHaveURL('http://localhost:5173/');
    await expect(page.getByRole('heading', { name: 'Tu negocio, de un vistazo.' })).toBeVisible();

    expect((await temporarySession.get('/api/v1/auth/me')).status()).toBe(401);
    await temporarySession.dispose();
  });

  test('bloquea el negocio suspendido y lo reactiva en vivo al confirmar el pago', async ({
    page,
  }) => {
    const suspended = await admin.post(`/api/v1/admin/subscriptions/${subscriptionId}/suspend`, {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
    });
    expect(suspended.status()).toBe(200);

    const merchant = await request.newContext({ baseURL: 'http://localhost:8080' });
    const merchantCsrf = await loginApi(merchant, email, permanentPassword);
    const blocked = await merchant.get('/api/v1/products');
    expect(blocked.status()).toBe(403);
    expect((await blocked.json()).code).toBe('SUBSCRIPTION_SUSPENDED');

    let eventStreamRequested = false;
    page.on('request', (requestEvent) => {
      if (requestEvent.url().endsWith('/api/v1/events')) eventStreamRequested = true;
    });
    await loginPage(page, permanentPassword);
    await expect(page).toHaveURL(/\/subscription-suspended$/);
    await expect(
      page.getByRole('heading', { name: 'Tu suscripción está suspendida.' }),
    ).toBeVisible();
    await expect.poll(() => eventStreamRequested).toBe(true);

    const payment = await merchant.post('/api/v1/subscription/payments', {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: {
        requestId: randomUUID(),
        paymentMethod: 'MANUAL_TRANSFER',
        externalReference: `E2E-${suffix}`,
      },
    });
    expect(payment.status()).toBe(201);
    const paymentBody = await payment.json();
    expect(paymentBody.status).toBe('PENDING');

    const confirmed = await admin.post(
      `/api/v1/admin/subscription-payments/${paymentBody.id}/confirm`,
      {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: { externalReference: `CONF-${suffix}` },
      },
    );
    expect(confirmed.status()).toBe(200);
    expect((await confirmed.json()).status).toBe('PAID');

    await expect(page).toHaveURL('http://localhost:5173/');
    await expect(page.getByRole('heading', { name: 'Tu negocio, de un vistazo.' })).toBeVisible();
    expect((await merchant.get('/api/v1/products')).status()).toBe(200);
    await merchant.dispose();
  });
});
