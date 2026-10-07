# Seguridad

## Informar una vulnerabilidad

No publiques vulnerabilidades, credenciales, datos personales ni archivos `.env`
en una incidencia pública. Informa el problema de forma privada al responsable del
repositorio mediante la opción **Security → Report a vulnerability** de GitHub.

Incluye una descripción, los pasos mínimos para reproducirlo, el impacto observado
y la versión afectada. No incluyas datos reales de comercios o clientes.

## Configuración segura

La configuración de Docker Compose incluida es para desarrollo y enlaza los puertos
solamente a `127.0.0.1`. Para producción sigue la sección correspondiente del
[README](README.md#preparación-para-producción) y la
[guía de AWS](infrastructure/aws.md).

Nunca confirmes `.env`, claves privadas, certificados, volcados de base de datos,
tokens ni credenciales de servicios. Si un secreto se expone, revócalo y rótalo;
eliminarlo del último commit no lo elimina del historial.
