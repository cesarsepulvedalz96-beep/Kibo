# Plan y decisiones iniciales

Kivo se implementa como monorepo: `frontend/` (React), `backend/` (Java),
`infrastructure/` (contenedores y despliegue) y `docs/` (guías de mantenimiento).

## Modelo

Business tiene usuarios, categorías, productos, configuración, ventas, movimientos,
notificaciones y auditoría. Product pertenece a Category de la misma empresa. Sale
contiene SaleItem con snapshots de producto, categoría y precio. InventoryMovement
registra el saldo anterior, el nuevo saldo y el autor. Las claves foráneas compuestas
refuerzan la pertenencia a la empresa. Los precios son netos y el IVA se añade al
confirmar; la moneda inicial es CLP, con cálculo decimal explícito de dos posiciones.

## Arquitectura

Monolito modular con dominio independiente de Spring/JPA, puertos de repositorio,
servicios de aplicación transaccionales, adaptadores de infraestructura y controllers
con DTOs. Los módulos pequeños incorporan únicamente las capas que necesitan.

Se usa una sesión opaca de servidor, persistida en PostgreSQL, con cookie HttpOnly,
CSRF y expiración por inactividad de 30 minutos. Es una decisión deliberada para una
aplicación web del mismo origen: simplifica revocación y no expone tokens a JavaScript.
El rol y la empresa se comprueban contra datos vigentes en cada petición.

Las ventas bloquean productos en orden estable para evitar sobreventa y deadlocks.
Un evento síncrono dentro de la transacción aplica los movimientos y alertas. Una
clave de idempotencia permite reintentar una confirmación sin duplicar ventas.

## Secuencia

1. Persistencia, migraciones, seguridad y aislamiento.
2. Catálogo, inventario, ventas, eventos, comprobantes y reportes.
3. Administración, configuración y adaptador S3.
4. Interfaz responsiva, accesible y traducida.
5. Pruebas de integridad, aislamiento, autorización y flujos de interfaz.
6. Docker, guías de ejecución y documentación de AWS y extensiones futuras.
