# API de Kivo

Base: `/api/v1`. JSON UTF-8; la carga de imágenes usa multipart.
Los importes son valores decimales y las fechas se devuelven en formato ISO.
Los DTOs rechazan campos desconocidos. Ningún endpoint comercial acepta
`businessId` como autorización: se obtiene de la sesión.

## Autenticación

1. `GET /auth/csrf`: devuelve `{token, headerName}` y establece sesión anónima.
2. `POST /auth/login`: enviar `{email, password}`, cookies y el token en el header indicado.
3. Obtener nuevamente `/auth/csrf` tras login, porque la sesión/token se rotan.
4. `GET /auth/me`: identidad, rol y empresa autorizada.
5. Si `mustChangePassword` es `true`, solo se permite cuenta/autenticación hasta
   ejecutar `POST /auth/change-password` con `{newPassword}`.
6. `POST /auth/logout`: enviar CSRF; invalida la sesión y devuelve 204.

El navegador debe enviar `credentials: 'include'`. Todo POST/PUT exige CSRF,
incluido login. No se usa un JWT en el navegador. `GET /health` es público.

## Endpoints

| Recurso                | Lecturas                                                                 | Escrituras                                             |
| ---------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------ |
| Empresas de plataforma | `GET /admin/businesses`                                                  | `POST /admin/businesses`, `PUT /admin/businesses/{id}` |
| Usuarios de plataforma | `GET /admin/users`                                                       | `POST /admin/users`, `PUT /admin/users/{id}`           |
| Planes                  | `GET /admin/plans`                                                       | `POST /admin/plans`, `PUT /admin/plans/{id}`           |
| Suscripciones           | `GET /admin/subscriptions`, `GET /subscription`                         | alta y acciones bajo `/admin/subscriptions`            |
| Pagos de suscripción    | `GET /admin/subscription-payments`, `GET /subscription/payments`        | registrar y confirmar bajo `subscription-payments`     |
| Eventos                 | `GET /events` (SSE)                                                      | —                                                       |
| Categorías             | `GET /categories`                                                        | `POST /categories`, `PUT /categories/{id}`             |
| Productos              | `GET /products`, `GET /products/{id}`, `GET /products/barcode/{barcode}` | `POST /products`, `PUT /products/{id}`                 |
| Movimientos            | `GET /inventory`                                                         | `POST /inventory`                                      |
| Salida sin cobro       | —                                                                        | `POST /inventory/exits`                                |
| Ventas                 | `GET /sales`, `GET /sales/{id}`                                          | `POST /sales`, `POST /sales/{id}/cancel`               |
| Comprobante interno    | `GET /documents/sales/{saleId}`                                          | —                                                      |
| Reportes               | `GET /reports/summary?range=TODAY`                                       | —                                                      |
| Ajustes                | `GET /settings`                                                          | `PUT /settings`                                        |
| Alertas                | `GET /notifications`                                                     | `POST /notifications/{id}/read`                        |
| Imagen                 | `GET /storage/products/{id}`                                             | `POST /storage/products/{id}`                          |

Administración exige `SUPER_ADMIN`. Todo acceso comercial usa `BUSINESS_USER` y
la empresa sale exclusivamente de la identidad autenticada. No quedan roles
`BUSINESS_ADMIN` ni `CASHIER` en el modelo vigente.

Suspender una suscripción hace que los endpoints comerciales respondan 403 con
`SUBSCRIPTION_SUSPENDED`. Siguen disponibles autenticación, logout, cuenta,
`GET /subscription`, `/subscription/payments` y `/events`.

## Administración de usuarios y suscripciones

`POST /admin/users` recibe `businessId`, `email`, `displayName` y
`temporaryPassword`. El usuario debe cambiarla en el primer ingreso. La edición
no permite reasignar la empresa. Las acciones son:

- `POST /admin/users/{id}/suspend`
- `POST /admin/users/{id}/reactivate`
- `POST /admin/users/{id}/reset-password` con `{temporaryPassword}`

Crear empresa acepta opcionalmente `planId` y `trialDays`; empresa, ajustes y
suscripción se guardan en una transacción. Sin `trialDays` comienza `ACTIVE`.

Las acciones administrativas de suscripción son `start-trial`, `extend-trial`,
`change-plan`, `activate`, `suspend`, `reactivate` y `cancel`. El pago solicitado
por el comercio no acepta importe: usa el precio actual del plan y queda `PENDING`.
Solo `POST /admin/subscription-payments/{id}/confirm` lo marca `PAID` y activa o
extiende la suscripción. `requestId` es idempotente por empresa.

`GET /events` usa `text/event-stream` y emite `PRODUCT_STOCK_CHANGED`,
`INVENTORY_CHANGED`, `SALE_CONFIRMED` y `SUBSCRIPTION_CHANGED`. Los eventos se
entregan únicamente a sesiones vigentes de la empresa correspondiente.

Las colecciones usan `page=0`, `size=20`, con máximo 100 por página, y devuelven:

```json
{ "items": [], "total": 0, "page": 0, "size": 20 }
```

Productos admite `search`, `categoryId`, `status=all|active|inactive|low`.
Inventario admite `productId`. Reportes admite `TODAY|WEEK|MONTH|YEAR` y agrega
los datos en PostgreSQL usando la zona horaria del negocio.

## Producto

POST/PUT comparte este cuerpo; el stock empieza en cero y solo cambia mediante
movimientos o ventas. Los IDs de ejemplo deben reemplazarse por los reales.

```json
{
  "categoryId": "00000000-0000-0000-0000-000000000001",
  "name": "Producto de ejemplo",
  "description": "",
  "barcode": "EJEMPLO001",
  "salePrice": 1000.0,
  "costPrice": null,
  "minimumStock": 5,
  "active": true
}
```

El precio es neto. El código es único por empresa si está informado.
La desactivación usa PUT; no se borran los registros históricos.

## Confirmar una venta

```json
{
  "requestId": "00000000-0000-0000-0000-000000000002",
  "expectedTotal": 2380.0,
  "items": [{ "productId": "00000000-0000-0000-0000-000000000003", "quantity": 2 }],
  "paymentMethod": "CASH",
  "cashReceived": 5000,
  "paymentReference": null,
  "externallyConfirmed": false
}
```

Se devuelve `{sale, items}`. El backend recalcula precios, IVA y total, verifica
stock y efectivo, y confirma todos los movimientos en la misma transacción.
Se admiten hasta 100 líneas con productos distintos.

`expectedTotal` es el importe que el operador revisó. Funciona como precondición:
si difiere del cálculo actual, el servidor rechaza con `PRICE_CHANGED` y no registra
la venta. El cliente debe actualizar los precios/impuestos y pedir una nueva
confirmación; el servidor nunca toma ese campo como fuente del total.

Para `CARD`, `TRANSFER` o `EXTERNAL`, indicar una referencia no vacía y
`externallyConfirmed: true` después de verificar el pago fuera del sistema.
Este endpoint registra el pago; no contacta una terminal.

`requestId` identifica una operación, no una petición HTTP. Reintentar con el
mismo UUID y contenido devuelve el resultado ya confirmado. Cambiar el contenido
con ese UUID produce `IDEMPOTENCY_CONFLICT`. Si se pierde la respuesta, recuperar
el resultado reintentando exactamente la operación original antes de iniciar otra.

Cancelar una venta devuelve su estado y restituye existencias una sola vez.
No procesa un reembolso en un proveedor externo.

## Inventario y ajustes

`POST /inventory/exits` recibe `{requestId, items}` con el mismo formato de líneas
que venta. Solo funciona en `INVENTORY_ONLY`; no recibe datos de pago.

Los movimientos manuales admiten `PURCHASE_ENTRY`, `MANUAL_ENTRY`, `MANUAL_EXIT`
y `ADJUSTMENT`. En ADJUSTMENT, `quantity` es la existencia final contada; en las
otras operaciones es la cantidad que entra o sale. Nunca se permite stock negativo.

`POST /inventory` recibe `{requestId, productId, type, quantity}`. También conserva
el resultado por empresa e identificador de operación: un reintento exacto devuelve
el mismo movimiento. Conserva el UUID mientras el resultado sea desconocido.

`PUT /settings` recibe `operatingMode`, `scannerEnabled`, `stockAlerts`,
`taxEnabled`, `taxRate`, `generateReceipt` y `timezone`. `taxRate` es fracción
entre 0 y 1; por ejemplo 0.19 representa 19 %. La tasa persistida es la fuente
definitiva del backend.

## Imágenes

Enviar campo multipart `file` con JPEG/PNG de hasta 5 MB. El servidor valida y
recodifica la imagen, genera una clave del tenant y la guarda en S3 privado.
GET devuelve una URL firmada temporal; no almacenarla como enlace permanente.
Sin bucket configurado se responde con un error de almacenamiento no disponible.

## Errores

La respuesta pública tiene forma `{"code":"INSUFFICIENT_STOCK"}`; no contiene
trazas, SQL ni credenciales. El frontend traduce el código al idioma elegido.

| HTTP | Uso                                                                    |
| ---- | ---------------------------------------------------------------------- |
| 400  | Validación de entrada                                                  |
| 401  | Sesión inexistente, expirada, acceso revocado o credenciales inválidas |
| 403  | Permisos o CSRF                                                        |
| 404  | Recurso inexistente dentro de la empresa autorizada                    |
| 409  | Conflicto, stock insuficiente, modo o idempotencia                     |
| 429  | Límite de intentos de login                                            |
| 500  | Error interno genérico                                                 |
| 503  | Servicio externo no configurado/disponible                             |

Los contratos completos están en los records `*Request` de cada módulo
`presentation`; se deben actualizar sus validaciones, frontend y esta guía juntos.
