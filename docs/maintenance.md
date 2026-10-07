# Guía para modificar Kivo

## Dónde cambiar cada cosa

| Cambio                              | Punto de entrada                                             |
| ----------------------------------- | ------------------------------------------------------------ |
| Colores, tema, bordes               | `frontend/src/styles/tokens.css`                             |
| Tamaños, distribución móvil, tablas | `frontend/src/styles/global.css`                             |
| Textos                              | `frontend/src/locales/{es,en,zh}/translation.json`           |
| Menú y cabecera                     | `frontend/src/app/AppShell.tsx`                              |
| Rutas y carga diferida              | `frontend/src/app/App.tsx`                                   |
| Envío de peticiones y CSRF          | `frontend/src/shared/api/client.ts`                          |
| Formulario de productos             | `frontend/src/features/products/ProductForm.tsx`             |
| Carrito y estimación                | `frontend/src/features/sales/cart.ts`                        |
| Recuperar operaciones pendientes    | `frontend/src/shared/hooks/usePendingOperation.ts`           |
| Scanner y cola de códigos           | `frontend/src/features/products/ProductSearch.tsx`           |
| Formas de pago en la pantalla       | `frontend/src/features/sales/CheckoutPanel.tsx`              |
| Reglas monetarias definitivas       | `backend/.../sales/domain/SaleCalculator.java`               |
| Confirmar/cancelar venta            | `backend/.../sales/application/SaleService.java`             |
| Descontar o restituir stock         | `backend/.../inventory/application/InventoryService.java`    |
| Autorización HTTP                   | `backend/.../auth/infrastructure/SecurityConfiguration.java` |
| Comprobar acceso vigente            | `backend/.../auth/infrastructure/ActiveIdentityFilter.java`  |
| Ciclo de una suscripción            | `backend/.../billing/domain/SubscriptionStateMachine.java`   |
| Planes, pagos y acciones admin      | `backend/.../billing/application/`                           |
| Conexiones y eventos SSE            | `backend/.../realtime/application/`                          |
| Pantallas de suscripción            | `frontend/src/features/subscription/`                        |
| Administración de plataforma        | `frontend/src/features/admin/`                               |
| Esquema de datos                    | `backend/src/main/resources/db/migration/`                   |

`backend/.../` representa `backend/src/main/java/com/stockflow/`.

## Ejemplo: añadir un atributo al producto

1. Añade una migración nueva, sin editar las ya aplicadas.
2. Amplía `Product` y su `ProductEntity`; actualiza la conversión en ambos sentidos.
3. Añade el campo al request permitido de `ProductController` y valida su tamaño/tipo.
4. Haz que `ProductService` determine el valor. Evita aceptar campos administrados
   como `businessId`, `stock`, fechas o `imageKey` desde el formulario general.
5. Amplía `shared/types.ts`, Zod, formulario y traducciones.
6. Ejecuta pruebas relevantes, build y formateadores. Si es una regla de negocio,
   añade una prueba que pueda detectar un comportamiento incorrecto.

## Ejemplo: añadir una forma de pago

Implementa `PaymentStrategy`, registra el componente e incorpora el método al
contrato y a la validación de base. Las reglas de autorización de un terminal van
detrás de `PaymentTerminalAdapter`. No mantengas una transacción de stock bloqueada
mientras esperas minutos a un proveedor externo: diseña antes el flujo pendiente,
la idempotencia del proveedor, la respuesta diferida y la conciliación.

## Reglas que conviene conservar

- Obtén el tenant desde `CurrentIdentity`, nunca desde un parámetro del cliente.
- Los IDs UUID no sustituyen a los permisos.
- Nunca actualices stock directamente desde un controller o formulario de producto.
- Las cantidades son unidades enteras. Productos vendidos por peso requieren una
  evolución explícita del modelo, constraints y redondeos.
- Precios de venta netos; IVA centralizado en backend. No cambies la convención
  neto/bruto en un solo componente.
- Una venta histórica conserva snapshots. Modificar un producto no reescribe ventas.
- No vuelvas asíncronos los listeners que mantienen stock e integridad transaccional.
- No uses `localStorage` para credenciales. Solo contiene preferencias visuales.
- Usa traducciones para textos nuevos y mantén las tres lenguas completas.
- Mantén separados el pago de una suscripción y el pago de una venta. Sus estados,
  tablas, servicios y adaptadores resuelven problemas distintos.
- No autorices una operación comercial desde React. La puerta de suscripción vive
  en backend y debe cubrir también endpoints nuevos.
- Publica SSE después del commit. Un evento antes del commit puede hacer que otro
  navegador consulte datos que todavía no existen o que finalmente se revierten.
- Al agregar un evento, incluye `businessId`, revalida la conexión y agrega la clave
  de React Query que debe invalidarse. El evento avisa; la API entrega el dato final.

## Formato y pruebas

Desde `frontend/`, `npm run format` aplica Prettier y `npm run format:check` lo comprueba.
En backend, `mvn spotless:apply` aplica el estilo Java; `mvn verify` también comprueba
el formato. El formato automático evita debates de sangría, pero no reemplaza
nombres descriptivos ni una responsabilidad clara por función.

Consulta los resultados y limitaciones actuales en [verification.md](verification.md).
