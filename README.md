# FitBlock para Android

MVP nativo en Kotlin, desarrollado a partir de **Plan de Negocios.docx**. Abre esta carpeta en Android Studio. La versión **0.3.0** funciona sin conexión, con cuenta local opcional: el modelo de postura va incluido en la APK y el manifiesto elimina el permiso de Internet.

## Novedades de la versión 0.3

- **103 deportes y actividades**, con búsqueda, categorías y favoritos: carrera, ciclismo, natación, yoga, fuerza, deportes de equipo, raqueta, combate, baile y más. Los cinco ejercicios verificados conservan sus sensores, mínimos y recompensas.
- Los otros 98 deportes permiten fijar una duración de **1 a 300 minutos**, iniciar, pausar, reanudar y guardar. El cronómetro continúa con la pantalla apagada y recupera la sesión tras cerrar el proceso en el mismo arranque del teléfono. Son registros por tiempo: no detectan automáticamente la técnica ni conceden minutos para desbloquear apps.
- **Mi cuenta** permite registrarse, iniciar/cerrar sesión y cambiar entre cuentas del teléfono. Cada cuenta conserva su historial, favoritos, saldo, rachas e insignias. Las contraseñas se guardan mediante PBKDF2-HMAC-SHA256 con sal aleatoria y 210 000 iteraciones, nunca en texto plano. La primera cuenta asocia el historial previo de invitado.
- **Rachas y premios** muestra la racha personal, su mejor marca, los últimos siete días y ocho insignias que se pueden añadir a la colección. Una meta completada suma actividad al día; guardar una sesión parcial solo añade el registro al historial.
- **Rachas en equipo** es una vista previa: puedes guardar el nombre del equipo y una meta de 3 a 90 días. Invitaciones, seguimiento de otras personas, sincronización, recuperación por correo y premios canjeables muestran **Muy pronto**. Todavía no hay servidor ni conexión entre cuentas de distintos teléfonos.

El repertorio toma como referencia los tipos de actividad de [Apple HealthKit](https://developer.apple.com/documentation/healthkit/hkworkoutactivitytype) y [Health Connect](https://android.googlesource.com/platform/frameworks/support/+/ffbddbd1295b99a57b395a5dcfb86af31f451492/health/connect/connect-client/src/main/java/androidx/health/connect/client/records/ExerciseSessionRecord.kt). No implica integración con sus datos, relojes o APIs.

## Instalar en tu teléfono

1. Copia **dist/FitBlock-MVP.apk** al teléfono e instálala. Requiere Android 8.0 o superior.
2. Abre FitBlock, lee el funcionamiento y acepta el consentimiento local.
3. En **Apps**, selecciona las aplicaciones que quieres proteger. Teléfono, SMS, inicio, teclado, ajustes y apps esenciales identificadas se excluyen.
4. Activa **Protección de FitBlock**. En **Permisos de FitBlock**, pulsa **Abrir Accesibilidad**, elige **FitBlock** entre las apps instaladas y activa el servicio.
5. Si Android muestra **Ajuste restringido**, usa **Abrir información de FitBlock** en la misma pantalla. En el menú ⋮, pulsa **Permitir ajustes restringidos** y vuelve a Accesibilidad.
6. Puedes revisar todos los permisos desde **Ajustes → Configurar permisos**. El acceso de uso es opcional y ayuda a restaurar el bloqueo tras reconectar el servicio.
7. En **Moverme**, busca y abre una actividad. Para ganar minutos, elige una de las cinco verificadas y pulsa **Cambiar meta**. La app solicita Cámara o Actividad física al iniciar. Puedes activar la notificación de caminata para ver el avance y pausar desde ella. Los demás deportes guardan duración mediante cronómetro.
8. Completa la meta y abre una de tus apps seleccionadas. El saldo disminuye únicamente durante el uso protegido. Al terminar el saldo, vencer el pase o alcanzar el límite diario de esa app, aparece la barrera.

Para probar la app, usa las metas mínimas en **Cambiar meta**. La meta queda fijada al iniciar y se conserva al pausar. El saldo se acredita al completar la meta.

## Reglas iniciales

Estos valores son decisiones configurables del MVP, porque el plan no fija cantidades finales.

| Regla | Valor inicial | Configuración |
| --- | --- | --- |
| Tiempo por actividad completa | 10 minutos | 1–60 min |
| Vigencia del pase | 60 minutos desde la recompensa | 5–240 min |
| Límite diario por app | 60 minutos | 1–240 min; mantener presionada la app para cambiarlo |
| Horario protegido | Todo el día, todos los días | Inicio, fin y días; permite horario nocturno |
| Sentadillas | 10 repeticiones | 10–300 |
| Flexiones | 8 repeticiones | 8–300 |
| Saltos de tijera | 15 repeticiones | 15–300 |
| Estiramiento de brazos | 30 segundos verificados | 30–300 |
| Caminata | 100 pasos verificados | 100–5000 |
| Dificultad progresiva | Desactivada | +10 % por actividad del día, hasta +40 % |
| Emergencia | 2 minutos, una vez al día | La siguiente meta aumenta un 50 % |

El saldo es una bolsa compartida entre las apps protegidas. Una nueva actividad suma sus minutos y renueva la vigencia de la bolsa. Los límites diarios son independientes por app: tener saldo no permite superar el límite de una app. No se restringe fuera del horario elegido. La emergencia se concede por el tiempo transcurrido real y no consume saldo. Los límites y el acceso de emergencia se contabilizan por fecha del dispositivo; atrasar la fecha no reinicia el registro del día más reciente.

## Cómo hacer los ejercicios

- **Preparación de cámara:** deja el teléfono estable, aléjate hasta que se vean pies y cabeza y usa buena iluminación. Antes de contar se solicitan dos movimientos aleatorios de brazos. La vista funciona como espejo: izquierdo significa tu propio brazo izquierdo.
- **Sentadillas:** después de las indicaciones, ponte de perfil. Extiende las piernas, baja hasta flexionar suficientemente las rodillas y vuelve a extenderlas. Se requiere recorrido completo y permanencia breve en cada fase.
- **Flexiones:** de perfil, muestra el brazo y cuerpo completo, con hombros, cadera y tobillos alineados. Baja flexionando el codo y regresa a brazos extendidos.
- **Saltos de tijera:** de frente, separa los pies y levanta ambas manos por encima de los hombros. Cierra los pies y baja las manos para registrar la repetición. Haz el movimiento continuo a tu ritmo.
- **Estiramiento:** de frente, eleva ambos brazos extendidos. Solo se suman segundos de postura válida. Perder la postura pausa el tiempo; los segundos válidos anteriores se conservan.
- **Caminata:** inicia en FitBlock y lleva el teléfono en el bolsillo. El podómetro del sistema, cuando está disponible, se contrasta con el acelerómetro. En otros teléfonos, FitBlock estima pasos con la cadencia del acelerómetro. Algunos podómetros envían varios pasos juntos y el avance puede aparecer con demora.

Salir de un ejercicio con cámara pausa la captura. Pulsa **Reanudar actividad** al volver. Una caminata iniciada continúa al cambiar de app o apagar la pantalla. Puedes pausarla o terminarla desde **Ver caminata**. El conteo y los sensores se detienen al completar la meta.

## Abrir y compilar

En Android Studio: **Open → esta carpeta → esperar Gradle Sync → configuración app → Run**. Selecciona el JDK incluido en Android Studio, versión 17 o superior; se comprobó con el JBR 21 instalado. SDK de compilación y destino 35, Gradle 9.4.1 y Android Gradle Plugin 9.2.1. En otro equipo Android Studio generará su propio `local.properties`.

En PowerShell:

```powershell
.\scripts\compilar.ps1
```

El script usa el JDK de Android Studio sin cambiar permanentemente las variables del sistema. Genera la APK en `dist`, su hash SHA-256 y ejecuta las pruebas unitarias y lint. La primera compilación necesita Internet para descargar dependencias; la aplicación instalada funciona sin él.

Para instalar por USB con depuración habilitada:

```powershell
.\scripts\instalar.ps1
# Si hay varios dispositivos:
.\scripts\instalar.ps1 -Serial NUMERO_DEL_DISPOSITIVO
```

Para las pruebas instrumentadas, usa exclusivamente un emulador de pruebas con Chrome: modifican la configuración y borran los datos locales de FitBlock en ese emulador.

```powershell
.\scripts\compilar.ps1 -PruebasEnEmulador
```

## Datos y límites técnicos

Se guardan configuración, paquetes seleccionados, 90 días de estadísticas agregadas y 50 eventos por perfil en almacenamiento privado de Android. Los registros detallados de actividades de esta versión se conservan hasta borrar los datos y se muestran por páginas de 20. No se guardan imágenes, videos, coordenadas ni recorridos. La exportación JSON contiene estadísticas, registros y eventos del perfil activo, sin credenciales. **Borrar todos los datos** elimina todas las cuentas y datos locales. No se habilitan copias de seguridad Android. Montserrat e Inter se incluyen con sus licencias OFL.

Las reglas de protección, apps seleccionadas y permisos pertenecen al teléfono y son compartidos entre perfiles. El saldo, uso supervisado e historial pertenecen al perfil activo. Debes terminar o descartar una actividad pendiente antes de cambiar de cuenta. Las cuentas locales no pueden recuperar datos tras desinstalar, borrar los datos o cambiar de teléfono; exporta tu historial antes si necesitas conservarlo.

El bloqueo funciona por accesibilidad y depende de que Android mantenga el servicio autorizado. No es una suspensión de paquetes del sistema ni administración empresarial del teléfono. Desactivar permisos, forzar detención, desinstalar o las restricciones del fabricante pueden interrumpirlo. El acceso opcional al uso de apps ayuda a recuperar el bloqueo; no impide esas acciones. Al reiniciar el teléfono o detectar un salto significativo del reloj se invalidan los pases, conservando reglas y estadísticas.

La caminata usa movimiento y cadencia, por lo que funciona en interiores y puede confundir movimiento rítmico con pasos. Android o el fabricante pueden detener el proceso. En ese caso debes iniciar otra caminata y los minutos ya ganados se conservan. La calibración física y el consumo de batería varían por teléfono.

Ligas, desafíos compartidos en línea, cuentas en la nube, pagos, suscripciones y relojes pertenecen a fases posteriores. Las vistas previas indican su estado. Consulta **docs/ALCANCE.md**, **docs/ARQUITECTURA.md** y **docs/PRUEBAS.md**.
