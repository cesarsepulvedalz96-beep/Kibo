import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import { randomBytes } from 'node:crypto';

let admin: APIRequestContext;
let merchant: APIRequestContext;
let adminCsrf: string;
let merchantCsrf: string;
let businessId: string;
let planId: string;
const suffix = randomBytes(5).toString('hex');
const email = `e2e-${suffix}@stockflow.local`;
const password = randomBytes(24).toString('base64url');
const temporaryPassword = randomBytes(24).toString('base64url');
let productId: string;

async function loginApi(context: APIRequestContext, username: string, pass: string) {
  let csrf = await (await context.get('/api/v1/auth/csrf')).json();
  const response = await context.post('/api/v1/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email: username, password: pass },
  });
  expect(response.status()).toBe(200);
  csrf = await (await context.get('/api/v1/auth/csrf')).json();
  return csrf.token as string;
}

async function loginPage(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Tu negocio, de un vistazo.' })).toBeVisible();
}

async function setMode(operatingMode: 'POS' | 'INVENTORY_ONLY') {
  const response = await merchant.put('/api/v1/settings', {
    headers: { 'X-CSRF-TOKEN': merchantCsrf },
    data: {
      operatingMode,
      scannerEnabled: true,
      stockAlerts: true,
      taxEnabled: true,
      taxRate: 0.19,
      generateReceipt: true,
      timezone: 'America/Santiago',
    },
  });
  expect(response.status()).toBe(200);
}

async function stock() {
  return (await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock as number;
}

async function scanOne(page: Page) {
  await page.goto('/sale');
  const scanner = page.getByRole('textbox', { name: 'Buscar o escanear producto' });
  await scanner.focus();
  await page.keyboard.type(`E2E-${suffix}`);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('spinbutton', { name: 'Cantidad Producto E2E' })).toHaveValue('1');
}

/** The server commits, but the browser never receives the response. */
async function loseFirstResponse(page: Page, path: string) {
  let dropped = false;
  await page.route(`**/api/v1${path}`, async (route) => {
    if (route.request().method() !== 'POST' || dropped) return route.continue();
    dropped = true;
    const response = await route.fetch();
    expect(response.ok()).toBe(true);
    await route.abort('failed');
  });
}

async function recoverAfterReload(page: Page) {
  await expect(page.getByText(/No pudimos verificar el resultado/)).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Recuperar resultado' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

async function expectDialogToFitViewport(page: Page) {
  const layout = await page.getByRole('dialog').evaluate((dialog) => {
    const dialogRect = dialog.getBoundingClientRect();
    const elements = [
      ...dialog.querySelectorAll<HTMLElement>(
        'input, select, textarea, .dialog-actions, .dialog-actions .button',
      ),
    ].filter((element) => element.offsetParent !== null);
    const outside = elements
      .map((element) => ({
        element: element.id || element.className || element.tagName,
        left: element.getBoundingClientRect().left,
        right: element.getBoundingClientRect().right,
      }))
      .filter(({ left, right }) => left < dialogRect.left - 1 || right > dialogRect.right + 1);
    return {
      clientWidth: dialog.clientWidth,
      scrollWidth: dialog.scrollWidth,
      outside,
    };
  });
  expect(layout.scrollWidth, JSON.stringify(layout.outside)).toBeLessThanOrEqual(
    layout.clientWidth + 1,
  );
  expect(layout.outside).toEqual([]);
}

test.describe.serial('StockFlow real end-to-end', () => {
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
        name: `Plan E2E ${suffix}`,
        description: 'Plan aislado para pruebas end-to-end',
        monthlyPrice: 19990,
        billingPeriodDays: 30,
        gracePeriodDays: 5,
        active: true,
      },
    });
    expect(plan.status()).toBe(201);
    planId = (await plan.json()).id;

    const response = await admin.post('/api/v1/admin/businesses', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: { name: `E2E ${suffix}`, taxId: '', active: true, planId, trialDays: 30 },
    });
    expect(response.status()).toBe(201);
    businessId = (await response.json()).id;

    const user = await admin.post('/api/v1/admin/users', {
      headers: { 'X-CSRF-TOKEN': adminCsrf },
      data: {
        businessId,
        email,
        displayName: 'Prueba de tienda',
        temporaryPassword,
      },
    });
    expect(user.status()).toBe(201);
    merchant = await request.newContext({ baseURL: 'http://localhost:8080' });
    merchantCsrf = await loginApi(merchant, email, temporaryPassword);
    const changed = await merchant.post('/api/v1/auth/change-password', {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: { newPassword: password },
    });
    expect(changed.status()).toBe(200);
    merchantCsrf = (await (await merchant.get('/api/v1/auth/csrf')).json()).token;
  });

  test.afterAll(async () => {
    if (businessId && admin)
      await admin.put(`/api/v1/admin/businesses/${businessId}`, {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: { name: `E2E ${suffix}`, taxId: '', active: false },
      });
    if (planId && admin)
      await admin.put(`/api/v1/admin/plans/${planId}`, {
        headers: { 'X-CSRF-TOKEN': adminCsrf },
        data: {
          name: `Plan E2E ${suffix}`,
          description: 'Plan aislado para pruebas end-to-end',
          monthlyPrice: 19990,
          billingPeriodDays: 30,
          gracePeriodDays: 5,
          active: false,
        },
      });
    await merchant?.dispose();
    await admin?.dispose();
  });

  test('category → product → entry → scanner → sale → receipt → cancellation', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await loginPage(page);
    await page.getByRole('link', { name: 'Productos', exact: true }).click();
    await page.getByRole('button', { name: 'Categorías', exact: true }).click();
    await expect(page).toHaveURL('http://localhost:5173/products');
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('button', { name: 'Nueva categoría', exact: true }).click();
    await page.getByLabel('Nombre', { exact: true }).fill('Categoría E2E');
    let releaseCategory!: () => void;
    const categoryGate = new Promise<void>((resolve) => {
      releaseCategory = resolve;
    });
    await page.route('**/api/v1/categories', async (route) => {
      if (route.request().method() !== 'POST') return route.continue();
      await categoryGate;
      await route.continue();
    });
    await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Guardar cambios', exact: true })).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('dialog').locator('.dialog-header button').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    releaseCategory();
    await expect(
      page.getByRole('dialog').getByText('Categoría E2E', { exact: true }),
    ).toBeVisible();
    await page.getByRole('dialog').locator('.dialog-header button').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.goto('/products');
    await page.getByRole('button', { name: 'Nuevo producto', exact: true }).click();
    await page.getByLabel('Producto', { exact: true }).fill('Producto E2E');
    await page
      .getByLabel('Categoría', { exact: true })
      .last()
      .selectOption({ label: 'Categoría E2E' });
    await page
      .getByRole('dialog')
      .getByLabel('Código de barras', { exact: true })
      .fill(`E2E-${suffix}`);
    await page.getByLabel('Precio neto · CLP').fill('1000');
    await page.getByRole('dialog').getByRole('button', { name: 'Nuevo producto' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    const products = await (await merchant.get('/api/v1/products')).json();
    productId = products.items[0].id;
    expect(products.items[0].stock).toBe(0);
    await page.goto('/inventory');
    await page.getByRole('button', { name: 'Registrar movimiento' }).click();
    await page.locator('#movement-category').selectOption({ label: 'Categoría E2E' });
    await page.locator('#movement-search').fill('Producto E2E');
    await page.locator('#movement-product').selectOption(productId);
    await page.getByLabel('Cantidad', { exact: true }).fill('10');
    await page.getByRole('button', { name: 'Confirmar movimiento' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.goto('/sale');
    const scanner = page.getByRole('textbox', { name: 'Buscar o escanear producto' });
    await page.route('**/products/barcode/**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 350));
      await route.continue();
    });
    await scanner.focus();
    await page.keyboard.type(`E2E-${suffix}`);
    await page.keyboard.press('Enter');
    await page.keyboard.press('Enter'); // Empty accidental Enter must not add another unit.
    await page.keyboard.type(`E2E-${suffix}`);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('spinbutton', { name: 'Cantidad Producto E2E' })).toHaveValue('2');
    await expect(scanner).toBeFocused();
    expect((await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock).toBe(10);
    await page.getByLabel('Efectivo recibido').fill('5000');
    let releaseScan!: () => void;
    const scanGate = new Promise<void>((resolve) => {
      releaseScan = resolve;
    });
    await page.route(
      '**/products/barcode/**',
      async (route) => {
        await scanGate;
        await route.continue();
      },
      { times: 1 },
    );
    await scanner.focus();
    await page.keyboard.type(`E2E-${suffix}`);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', { name: 'Confirmar venta', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Historial de ventas', exact: true }).click();
    await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Confirmar venta', exact: true })).toBeEnabled();
    await expect(page.getByRole('spinbutton', { name: 'Cantidad Producto E2E' })).toHaveValue('2');
    releaseScan();
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar venta', exact: true })
      .click();
    await expect(page.getByText('Venta confirmada', { exact: true })).toBeVisible();
    expect((await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock).toBe(8);
    await page.getByRole('link', { name: 'Ver comprobante' }).click();
    await expect(
      page.getByText('Este comprobante interno no constituye una boleta tributaria electrónica.'),
    ).toBeVisible();
    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('.sidebar')).toBeHidden();
    await expect(page.locator('.receipt')).toBeVisible();
    await page.emulateMedia({ media: 'screen' });
    await page.goto('/sale?tab=history');
    await page.getByRole('button', { name: /Cancelar venta/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancelar venta' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect((await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock).toBe(10);
    expect(errors).toEqual([]);
  });

  test('responsive navigation, saved themes and three languages', async ({ page }) => {
    await loginPage(page);
    for (const width of [1440, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      for (const route of ['/', '/products', '/inventory', '/sale', '/reports', '/settings']) {
        await page.goto(route);
        await expect(page.locator('h1')).toBeVisible();
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth > window.innerWidth,
        );
        expect(overflow, `${route} overflows at ${width}px`).toBe(false);
      }
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: 'Abrir menú' }).click();
    await expect(page.getByRole('link', { name: 'Productos', exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('.sidebar')).toBeHidden();
    const themeOptions = page
      .getByRole('group', { name: 'Tema de la interfaz' })
      .getByRole('button');
    await expect(themeOptions).toHaveCount(2);
    await expect(page.getByRole('button', { name: 'Sistema', exact: true })).toHaveCount(0);
    const accentOptions = page.getByRole('group', { name: 'Color principal' }).getByRole('button');
    await expect(accentOptions).toHaveCount(7);
    await accentOptions.nth(3).click();
    const selectedAccent = await page.locator('html').getAttribute('data-accent');
    expect(selectedAccent).toBeTruthy();
    await page.getByRole('button', { name: 'Oscuro', exact: true }).click();
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect(page.locator('html')).toHaveAttribute('data-accent', selectedAccent!);
    await page.getByLabel('Idioma', { exact: true }).selectOption('en');
    await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible();
    await page.getByLabel('Language', { exact: true }).selectOption('zh');
    await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/mobile-settings.png', fullPage: true });
  });

  test('inventory-only checkout records exits without a payment', async ({ page }) => {
    const response = await merchant.put('/api/v1/settings', {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: {
        operatingMode: 'INVENTORY_ONLY',
        scannerEnabled: true,
        stockAlerts: true,
        taxEnabled: true,
        taxRate: 0.19,
        generateReceipt: true,
        timezone: 'America/Santiago',
      },
    });
    expect(response.status()).toBe(200);
    await loginPage(page);
    await page.goto('/sale');
    const scanner = page.getByRole('textbox', { name: 'Buscar o escanear producto' });
    await scanner.fill(`E2E-${suffix}`);
    await scanner.press('Enter');
    await expect(page.getByRole('spinbutton', { name: 'Cantidad Producto E2E' })).toHaveValue('1');
    expect((await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock).toBe(10);
    await expect(page.getByLabel('Medio de pago')).toHaveCount(0);
    await page.getByRole('button', { name: 'Confirmar salida de stock' }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar salida de stock' })
      .click();
    await expect(page.getByText('Salida registrada correctamente.')).toBeVisible();
    expect((await (await merchant.get(`/api/v1/products/${productId}`)).json()).stock).toBe(9);
    expect((await (await merchant.get('/api/v1/reports/summary')).json()).inventoryExits).toBe(1);
  });

  test('a committed sale survives a lost response and reload without being sold twice', async ({
    page,
  }) => {
    await setMode('POS');
    const before = await stock();
    const salesBefore = (await (await merchant.get('/api/v1/sales')).json()).total;
    await loginPage(page);
    await scanOne(page);
    await page.getByLabel('Efectivo recibido').fill('10000');
    await loseFirstResponse(page, '/sales');
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar venta', exact: true })
      .click();
    await recoverAfterReload(page);
    await expect(page.getByText('Venta confirmada', { exact: true })).toBeVisible();
    expect(await stock()).toBe(before - 1);
    expect((await (await merchant.get('/api/v1/sales')).json()).total).toBe(salesBefore + 1);
  });

  test('a committed inventory exit survives a lost response and reload without another stock reduction', async ({
    page,
  }) => {
    await setMode('INVENTORY_ONLY');
    const before = await stock();
    const exitsBefore = (await (await merchant.get('/api/v1/reports/summary')).json())
      .inventoryExits;
    await loginPage(page);
    await scanOne(page);
    await loseFirstResponse(page, '/inventory/exits');
    await page.getByRole('button', { name: 'Confirmar salida de stock' }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar salida de stock' })
      .click();
    await recoverAfterReload(page);
    expect(await stock()).toBe(before - 1);
    expect((await (await merchant.get('/api/v1/reports/summary')).json()).inventoryExits).toBe(
      exitsBefore + 1,
    );
  });

  test('a committed manual entry survives a lost response and restores its locked form', async ({
    page,
  }) => {
    const before = await stock();
    const movementsBefore = (await (await merchant.get('/api/v1/inventory')).json()).total;
    await loginPage(page);
    await page.goto('/inventory');
    await page.getByRole('button', { name: 'Registrar movimiento' }).click();
    await page.locator('#movement-search').fill('Producto E2E');
    await page.locator('#movement-product').selectOption(productId);
    await page.getByLabel('Cantidad', { exact: true }).fill('3');
    await loseFirstResponse(page, '/inventory');
    await page.getByRole('button', { name: 'Confirmar movimiento' }).click();
    await expect(page.getByText(/No pudimos verificar el resultado/)).toBeVisible();
    await expect(page.getByLabel('Cantidad', { exact: true })).toBeDisabled();
    await recoverAfterReload(page);
    expect(await stock()).toBe(before + 3);
    expect((await (await merchant.get('/api/v1/inventory')).json()).total).toBe(
      movementsBefore + 1,
    );
  });

  test('movement filters and select stay consistent; action buttons are equal and modal actions centered', async ({
    page,
  }) => {
    const categoryResponse = await merchant.post('/api/v1/categories', {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: { name: 'Otra categoría E2E', active: true },
    });
    expect(categoryResponse.status()).toBe(201);
    const otherCategory = await categoryResponse.json();
    const otherResponse = await merchant.post('/api/v1/products', {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: {
        categoryId: otherCategory.id,
        name: 'Alternativa E2E',
        barcode: `ALT-${suffix}`,
        salePrice: 500,
        minimumStock: 0,
        active: true,
      },
    });
    expect(otherResponse.status()).toBe(201);
    const otherProduct = await otherResponse.json();
    await loginPage(page);
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/products');
      const actions = page.locator('.page-actions .button');
      await expect(actions).toHaveCount(2);
      const sizes = await actions.evaluateAll((buttons) =>
        buttons.map((button) => ({
          width: button.getBoundingClientRect().width,
          height: button.getBoundingClientRect().height,
        })),
      );
      expect(Math.abs(sizes[0].width - sizes[1].width)).toBeLessThanOrEqual(1);
      expect(Math.abs(sizes[0].height - sizes[1].height)).toBeLessThanOrEqual(1);
      await page.getByRole('button', { name: 'Nuevo producto', exact: true }).click();
      await expectDialogToFitViewport(page);
      const dimensions = await page.locator('.dialog-actions').evaluate((element) => {
        const rect = element.getBoundingClientRect();
        const buttons = [...element.querySelectorAll('button')].map((button) =>
          button.getBoundingClientRect(),
        );
        const left = Math.min(...buttons.map((button) => button.left));
        const right = Math.max(...buttons.map((button) => button.right));
        return {
          offset: Math.abs((left + right) / 2 - (rect.left + rect.right) / 2),
          widths: buttons.map((button) => button.width),
          heights: buttons.map((button) => button.height),
        };
      });
      expect(dimensions.offset).toBeLessThanOrEqual(1);
      expect(Math.max(...dimensions.widths) - Math.min(...dimensions.widths)).toBeLessThanOrEqual(
        1,
      );
      expect(Math.max(...dimensions.heights) - Math.min(...dimensions.heights)).toBeLessThanOrEqual(
        1,
      );
      expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(
        false,
      );
      if (width === 390)
        await page.screenshot({ path: 'test-results/product-modal-mobile.png', fullPage: true });
      await page.getByRole('dialog').locator('.dialog-header button').click();
      if (width === 320) {
        await page.getByRole('button', { name: 'Categorías', exact: true }).click();
        await expectDialogToFitViewport(page);
        await page.getByRole('dialog').locator('.dialog-header button').click();
      }
    }
    await page.goto('/inventory');
    await page.getByRole('button', { name: 'Registrar movimiento' }).click();
    const select = page.locator('#movement-product');
    await select.selectOption(productId);
    await page.locator('#movement-category').selectOption(otherCategory.id);
    await expect(select).toHaveValue('');
    await expect(page.getByRole('button', { name: 'Confirmar movimiento' })).toBeDisabled();
    await expect(select.locator(`option[value="${productId}"]`)).toHaveCount(0);
    await select.selectOption(otherProduct.id);
    await page.locator('#movement-search').fill('Sin coincidencia');
    await expect(select).toHaveValue('');
    await expect(select.locator('option')).toHaveCount(1);
    await page.locator('#movement-search').fill('Alternativa');
    await select.selectOption(otherProduct.id);
    await expect(page.getByRole('button', { name: 'Confirmar movimiento' })).toBeEnabled();
    const movementsBeforeSearch = (await (await merchant.get('/api/v1/inventory')).json()).total;
    await page.locator('#movement-search').press('Enter');
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(select).toHaveValue(otherProduct.id);
    await expectDialogToFitViewport(page);
    await page.screenshot({ path: 'test-results/movement-modal-mobile.png', fullPage: true });
    await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
    expect((await (await merchant.get('/api/v1/inventory')).json()).total).toBe(
      movementsBeforeSearch,
    );
  });

  test('a changed price requires a new external payment confirmation before committing', async ({
    page,
  }) => {
    await setMode('POS');
    const before = await stock();
    const salesBefore = (await (await merchant.get('/api/v1/sales')).json()).total;
    await loginPage(page);
    await scanOne(page);
    await page.getByLabel('Medio de pago').selectOption('CARD');
    await page.getByLabel('Referencia del pago').fill(`REF-${suffix}`);
    const approval = page.getByRole('checkbox', { name: /Confirmo que el pago fue aprobado/ });
    await approval.check();
    const previousTotal = await page.locator('.checkout-panel .total-line dd').textContent();
    const product = await (await merchant.get(`/api/v1/products/${productId}`)).json();
    const updated = await merchant.put(`/api/v1/products/${productId}`, {
      headers: { 'X-CSRF-TOKEN': merchantCsrf },
      data: {
        categoryId: product.categoryId,
        name: product.name,
        description: product.description,
        barcode: product.barcode,
        salePrice: product.salePrice + 1000,
        costPrice: product.costPrice,
        minimumStock: product.minimumStock,
        active: product.active,
      },
    });
    expect(updated.status()).toBe(200);
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar venta', exact: true })
      .click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByText(/Cambió el precio o el impuesto/)).toBeVisible();
    await expect(page.locator('.checkout-panel .total-line dd')).not.toHaveText(previousTotal!);
    await expect(approval).not.toBeChecked();
    await expect(page.getByRole('button', { name: 'Confirmar venta', exact: true })).toBeDisabled();
    expect(await stock()).toBe(before);
    expect((await (await merchant.get('/api/v1/sales')).json()).total).toBe(salesBefore);
    await approval.check();
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Confirmar venta', exact: true })
      .click();
    await expect(page.getByText('Venta confirmada', { exact: true })).toBeVisible();
    expect(await stock()).toBe(before - 1);
    const sales = await (await merchant.get('/api/v1/sales')).json();
    expect(sales.total).toBe(salesBefore + 1);
    expect(sales.items[0].total).toBe((product.salePrice + 1000) * 1.19);
  });
});
