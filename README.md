# Mis Millas Flex — app en español para registrar tus millas de Amazon Flex

App instalada de verdad (APK). Registra las millas por GPS con la pantalla
apagada, guarda los viajes en el teléfono y calcula el total y la deducción
estimada. Nada sale del teléfono: no hay cuentas, no hay nube, no hay internet.

## Qué hace

- **Empezar viaje / Terminar viaje**: cuenta las millas por GPS mientras manejas.
- Funciona con la pantalla apagada si le das el permiso **"Permitir todo el tiempo"**
  a la ubicación (Android lo pide aparte, después del permiso normal).
- **Agregar a mano**: por si se te olvida abrirla un día.
- **Exportar CSV**: copia todos los viajes en formato de tabla (fecha, millas,
  tipo) para pegarlos en una nota o un correo.
- **Totales**: millas totales, de trabajo, personales y deducción estimada
  (con la tarifa del IRS 2025: $0.70 por milla; la de 2026 aún no se anuncia).
- Mantén presionado un viaje en la lista para borrarlo.

## Archivos del proyecto

```
MisMillasFlex/
  android/
    build.gradle
    settings.gradle
    app/
      build.gradle
      src/main/AndroidManifest.xml
      src/main/java/com/arley/millasflex/
        MainActivity.java      (pantalla principal, todo en español)
        TrackingService.java   (servicio GPS en segundo plano)
        Trip.java              (un viaje)
        TripStore.java         (guarda los viajes en el teléfono)
      src/main/res/layout/activity_main.xml
  .github/workflows/build-apk.yml   (GitHub construye el APK gratis)
```

## Cómo construir el APK desde el teléfono (como se hizo con Luna)

1. En GitHub, crea un repositorio nuevo llamado **MisMillasFlex** (público).
2. Sube las carpetas **android** y **.github** con todos los archivos de este ZIP,
   tal como están (mismas carpetas, mismos nombres).
3. Ve a la pestaña **Actions** del repositorio. Cuando el trabajo **Build APK**
   termine en verde, entra y descarga el archivo **millas-flex-apk**.
   Dentro del ZIP descargado está **app-debug.apk**.
4. Instala el APK en tu teléfono. Si te pide permiso para instalar apps
   desconocidas, acéptalo para Chrome o para la app de Archivos.
5. Abre **Mis Millas Flex** y acepta los permisos:
   - **Ubicación**: primero "Permitir solo mientras se usa la app",
     y después, cuando la app lo pida, **"Permitir todo el tiempo"**.
     Esto es lo que hace que cuente millas con la pantalla apagada.
   - **Notificaciones**: permitir (así ves el aviso "Registrando millas...").

## Primera prueba (5 minutos)

1. Abre la app, elige **Trabajo** y toca **Empezar viaje**.
2. Maneja una o dos cuadras.
3. Toca **Terminar viaje**.
4. El viaje tiene que aparecer en la lista con sus millas.
5. Apaga la pantalla y maneja otra cuadra con el viaje empezado: al
   terminar, las millas tienen que seguir contando. Si no contaron,
   revisa que la ubicación esté en **"Permitir todo el tiempo"**
   (Ajustes → Apps → Mis Millas Flex → Permisos → Ubicación).

## Si algo no funciona

- Si GitHub Actions falla (trabajo en rojo), mándame una captura del error
  y lo arreglo.
- Si el APK no instala, revisa el permiso de "instalar apps desconocidas".
- Si no cuenta millas con la pantalla apagada, el permiso de ubicación
  no está en "todo el tiempo": es el paso 5 de arriba.

## Honestidad sobre la prueba

Este código **no se probó compilado en una computadora**: se escribió con
la misma receta que ya funcionó para el APK de Luna (Gradle 8.2, Java 17,
Android API 34). GitHub lo compila al subirlo; si la compilación da un
error, mándame la captura y lo corrijo.
