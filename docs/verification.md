# Verificación de Kivo

## Preparación del repositorio público — 7 de octubre de 2026

Se actualizó la documentación principal con la marca Kivo, instrucciones de inicio,
variables, puertos locales y requisitos de producción. Se añadieron licencia MIT,
política de seguridad, exclusiones de archivos privados y una comprobación de CI que
rechaza entornos locales, claves privadas y artefactos generados versionados.

En esta revisión pasaron Prettier y ESLint del frontend, además de las 23 pruebas
unitarias del backend. Windows Application Control bloqueó el binario nativo local de
Rollup al iniciar Vitest; Docker Desktop estaba detenido, por lo que no se repitieron
en esta fecha el build en contenedor, las integraciones Testcontainers ni Playwright.
Los resultados completos de la aplicación documentados a continuación corresponden
a las rondas funcionales previas.

Fecha de actualización: 20 de septiembre de 2026. La entrega se verifica en Windows con
Docker Desktop, PostgreSQL real, Java 25, Node 24 y Chromium.

## Usuarios, suscripciones y tiempo real

La etapa actual pasó **86 pruebas: 23 unitarias backend, 19 integraciones backend,
32 unitarias frontend y 12 flujos E2E**. `mvn verify` aplicó desde cero las cinco
migraciones Flyway en PostgreSQL 17 mediante Testcontainers. Spotless, ESLint,
Prettier, TypeScript/Vite y los builds Docker terminaron correctamente.

Las integraciones nuevas verifican alta atómica de empresa/ajustes/suscripción,
rol único comercial, contraseña temporal, último acceso, reset y suspensión,
revocación de sesiones, estados y fechas de billing, scheduler idempotente,
aislamiento empresarial, pagos pendientes, confirmación, reactivación e
idempotencia. Se conservaron las pruebas de stock unitario simultáneo, rollback,
precio/IVA definitivo y reintentos sin duplicación.

Los dos flujos E2E añadidos comprueban:

1. El usuario temporal puede consultar su cuenta y cambiar la contraseña, pero un
   endpoint comercial responde `PASSWORD_CHANGE_REQUIRED`; la versión anterior de
   sesión queda revocada después del cambio.
2. Una suscripción suspendida bloquea productos, permite registrar un pago pendiente
   y vuelve a habilitar el dashboard sin recargar cuando `SUPER_ADMIN` confirma el
   pago y llega `SUBSCRIPTION_CHANGED` por SSE.

El build de producción se ejecutó dentro de Docker y los 12 flujos Chromium pasaron
contra Nginx, Spring Boot y la base persistente local en 1.1 minutos.

## Actualización de interfaz y sesión

La ronda más reciente pasó **66 pruebas: 26 backend, 30 frontend y 10 flujos E2E**.
Backend verify, Spotless, frontend ESLint/Prettier, TypeScript/Vite y build Docker
correctos. Los diez flujos Chromium pasaron sobre Docker/Nginx en 44.8 segundos.
También se verificaron dimensiones iguales y acciones centradas a 320, 390 y
1440 px, filtros/select de inventario, Enter sin modificar stock, X/Escape durante
guardado, logout de administrador/comercio sin recarga, CSRF desactualizado,
dos temas explícitos, siete colores persistentes y ausencia de desborde interno
en los diálogos móviles.
Consulta [cambios, archivos y revisiones de esta ronda](ui-and-session.md).

Los resultados siguientes documentan la primera entrega del 13 de septiembre.

## Revisión especializada

Se ejecutaron en paralelo `security_reviewer`, `blind_reviewer` y `qa_reviewer`,
sin cambios de código por parte de los revisores. El coordinador contrastó los
hallazgos con la implementación y los derivó a workers con archivos separados.

| Severidad | Hallazgo confirmado                                                      | Corrección aplicada / estado                                                                       |
| --------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| HIGH      | El campo del scanner perdía foco durante la consulta                     | Conservar foco, encolar códigos y probar teclado consecutivo con latencia                          |
| HIGH      | Modificar una venta tras perder la respuesta generaba otra operación     | Persistir intento original, recuperar con el mismo UUID y bloquear edición hasta resolver          |
| HIGH      | Reintentar un movimiento manual podía volver a aplicar stock             | Idempotencia empresarial y resultado atómico; prueba de concurrencia y respuesta perdida           |
| HIGH      | Un cambio de precio/IVA podía registrar un importe distinto del revisado | Precondición `expectedTotal` contra cálculo definitivo y reconfirmación de pago                    |
| HIGH      | Nginx hacía que todos los visitantes compartieran el límite de login     | Proxy exacto autorizado, cabecera sobrescrita e IP separada por visitante; pruebas de suplantación |
| MEDIUM    | Rankings agrupaban productos/categorías por nombre mutable               | Agrupar por identidad y conservar categoría histórica; pruebas de renombrado/homónimos             |
| MEDIUM    | Cambiar a Historial durante un scan dejaba Confirmar bloqueado           | Restablecer estado al desmontar búsqueda; regresión con respuesta retenida y regreso a Venta       |
| LOW       | Se fuerza flush de JPA por entidad                                       | Deuda de rendimiento documentada; medir antes de agrupar escrituras                                |

La segunda revisión de seguridad y QA no encontró CRITICAL ni HIGH residuales
confirmados. QA también confirmó por inspección el cierre del bloqueo al cambiar
a Historial; posteriormente su regresión pasó en navegador.

## Resultados finales

**49 pruebas aprobadas, sin fallos ni pruebas omitidas.**

| Verificación                           | Resultado                                                |
| -------------------------------------- | -------------------------------------------------------- |
| Backend `./scripts/backend.ps1 verify` | BUILD SUCCESS; 9 unitarias y 17 integraciones PostgreSQL |
| Backend Spotless y empaquetado         | Correctos; JAR generado                                  |
| Frontend `npm test`                    | 16 pruebas, 4 archivos                                   |
| Frontend ESLint y Prettier             | Correctos                                                |
| Frontend `npm run build`               | TypeScript y Vite correctos, sin sourcemaps              |
| Chromium `npm run test:e2e`            | 7 flujos aprobados contra build Docker/Nginx, 37.5 s     |
| Imágenes Docker backend/frontend       | Construidas y ejecutadas                                 |

Las integraciones backend cubren aislamiento empresarial, roles/CSRF/sesión JDBC,
desactivación de acceso, cálculos monetarios, IVA, concurrencia de stock,
rollback por fallo de evento, cancelación única, idempotencia, precio/impuesto
cambiado, rankings y separación de visitantes detrás del proxy.

Los siete flujos de navegador verifican:

1. Categoría → producto → entrada → scanner continuo con latencia y Enter vacío →
   navegación durante escaneo → venta → comprobante imprimible → cancelación.
2. Pantallas a 1440, 768, 390 y 320 px, navegación móvil por teclado, tema guardado
   e idiomas español/inglés/chino.
3. Salida INVENTORY_ONLY sin datos de pago.
4. Venta confirmada cuya respuesta se pierde: recarga y recuperación, una sola venta.
5. Salida confirmada cuya respuesta se pierde: recuperación sin descontar dos veces.
6. Entrada manual con respuesta perdida: formulario bloqueado/restaurado, un movimiento.
7. Cambio de precio tras revisar pago externo: rechazo sin efectos y nueva aprobación.

En las pruebas de respuesta perdida, Playwright deja que el servidor confirme la
operación y luego aborta únicamente la respuesta al navegador. Se verifican stock,
cantidad de registros y conservación del identificador al reintentar.

## Comprobaciones adicionales

- Contenedores backend/frontend construidos y arranque conjunto probado.
- PostgreSQL saludable; `/api/v1/health` accesible mediante Nginx.
- Ruta React `/products` resuelta por el fallback SPA; cabecera CSP presente.
- `nginx -t` correcto.
- `npm audit --omit=dev`: cero vulnerabilidades conocidas en la consulta realizada.
- Comprobación de fuentes y build público: sin coincidencias con contraseñas locales
  y sin archivos `.map` publicados. Esto no equivale a una auditoría de todo historial Git.

Avisos no bloqueantes de herramientas: Rollup retira dos anotaciones de comentarios
de Zod; Mockito informa que una futura versión de Java requerirá configuración
explícita del agente de pruebas. Las versiones actuales compilan y ejecutan las suites.

## Límites de la validación

**MEDIUM — Recuperación en la misma pestaña.** Los intentos pendientes se guardan
en `sessionStorage`. Navegar o recargar conserva el intento; cerrar la pestaña
puede eliminarlo. Ante resultado incierto hay que mantenerla abierta y recuperar
la operación antes de registrar otra. Recuperar después de cerrar el navegador
requiere persistencia durable y conciliación; queda como ampliación del MVP.

Las pruebas de navegador cubren Chromium con teclado HID simulado y resoluciones
320, 390, 768 y 1440 píxeles. No sustituyen una prueba con el scanner, impresora,
navegadores o dispositivos concretos de cada comercio.

No se han desplegado recursos AWS ni probado credenciales de una cuenta real.
El adaptador S3 necesita validación de staging, IAM, bucket y gestión de objetos
huérfanos antes de habilitar imágenes en producción. SII y autorización/reembolso
automático de terminales son integraciones futuras explícitas.

Queda pendiente medir el rendimiento de carritos grandes y concurrencia sostenida.
`JpaTenantRepository.save` realiza flush para mantener visibles las escrituras entre
JPA y consultas JDBC de la misma transacción. Reducirlos exige conservar ese orden
y volver a probar rollback, eventos, lecturas e inventario; no se declara un problema
de capacidad medido ni se promete un volumen máximo de cajas.

Los workflows de GitHub Actions están preparados; no se ha ejecutado una corrida
remota porque este workspace no se ha publicado en GitHub.
