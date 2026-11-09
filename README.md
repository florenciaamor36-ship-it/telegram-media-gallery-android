# Telegram Media Gallery for Android

Aplicación Android privada para explorar fotos y videos de la cuenta propia de Telegram mediante TDLib oficial.

## Alcance

- Inicio de sesión de Telegram dentro de la app, con código de verificación y contraseña de dos pasos si Telegram la solicita.
- Lista de conversaciones/canales accesibles a la cuenta; selección de la fuente y navegación por sus fotos y videos.
- Miniaturas, fechas y reproducción/visualización en pantalla completa; descarga iniciada por el usuario cuando haga falta.
- Sesión local aislada de otras apps; no se copian credenciales de sesión desde TeleCloud.
- Secretos `TELEGRAM_API_ID` y `TELEGRAM_API_HASH` solo por variables seguras de CI o `local.properties` ignorado. Nunca en el código fuente.

## Estado

Repositorio recién creado. TDLib de TeleCloud es una referencia técnica propia, no una integración ya terminada. Faltan portar/configurar el cliente nativo, implementar listado/filtrado de medios, UI de galería y reproductor, y probar build e inicio de sesión. No distribuir como APK lista hasta superar esas verificaciones.

## Privacidad y uso

La app debe solicitar el inicio de sesión explícito de la persona usuaria, mantener los datos de sesión en almacenamiento privado del dispositivo, permitir cerrar sesión y no enviar archivos a terceros. Solo se deben reproducir o guardar medios a los que la cuenta tenga acceso y que la persona esté autorizada a usar.

## Catálogos abiertos (prototipo)
- Internet Archive: búsqueda pública, metadatos, selección de archivos audiovisuales y reproducción con Media3.
- Wikimedia Commons: búsqueda de archivos de video, lectura de metadatos de licencia/autor y reproducción con Media3.
- Cada resultado conserva el enlace a la ficha fuente y muestra los derechos disponibles. Los metadatos no sustituyen la comprobación legal: confirmar la licencia y condiciones por archivo antes de redistribuir o incluir títulos en un catálogo propio.
- Pluto TV no está integrado: requiere documentación y autorización técnica oficial verificable.

Estado de este prototipo: no compilado ni probado en dispositivo. Los adaptadores de búsqueda pueden requerir ajuste por políticas de API, formatos o disponibilidad de archivos.
