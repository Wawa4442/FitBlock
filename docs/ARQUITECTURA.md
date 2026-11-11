# Arquitectura del MVP

La aplicación es local y de un solo módulo. No contiene claves de API, cuentas, servidor ni dependencia de una suscripción. El manifiesto elimina Internet y acceso al estado de red, incluso cuando aparecen en manifiestos transitivos de ML Kit.

| Archivo | Responsabilidad |
| --- | --- |
| MainActivity.kt | Consentimiento, inicio, catálogo de apps, horarios/metas, progreso, permisos, privacidad y exportación |
| WorkoutActivity.kt | Captura por cámara, retos aleatorios, conteo, pausa y recompensa |
| PoseSession.kt | Retos, fases de repetición y tiempo observado de estiramiento |
| WalkingActivity.kt y WalkService.kt | Control de la sesión, notificación, captura de sensores y recompensa en segundo plano |
| WalkingTracker.kt | Diferencias del podómetro, plausibilidad de pasos y cadencia con acelerómetro |
| PoseValidator.kt | Geometría y confianza de articulaciones, fases y postura válida por actividad |
| Policy.kt | Horarios, límites, metas, expiración y fases de una repetición |
| Store.kt | Configuración, sesión autorizada, saldo, consumo, cuotas, deuda de emergencia, rachas y datos locales |
| BlockService.kt | Eventos de paquete, ventana de bloqueo, conteo de uso y reconexión |
| UsageAccess.kt | Consulta opcional del último paquete en primer plano, sin contenido de pantalla |
| AppCatalog.kt | Apps seleccionables y exclusión de aplicaciones esenciales |
| Ui.kt | Componentes nativos, tipografía, colores, tarjetas, gráfica y anillo de progreso |

## Recorrido de desbloqueo

1. Store crea un identificador aleatorio de sesión con ejercicio, meta y recompensa fijados al comenzar. Solo hay una sesión activa en el proceso.
2. WorkoutActivity solicita los permisos correspondientes e inicia captura únicamente mientras permanece visible.
3. La cámara valida las indicaciones aleatorias y luego registra un ciclo inicial, movimiento y regreso. Tolera pérdidas breves de puntos y usa el tiempo de captura para descartar ciclos imposibles. En estiramiento se integra el tiempo observado en la postura.
4. WalkService inicia desde la pantalla de caminata como servicio en primer plano de tipo salud. Combina pasos nuevos del podómetro con evidencia de movimiento en el intervalo de la lectura. Con acelerómetro solo, exige una secuencia de zancadas con cadencia plausible. Mantiene el procesador disponible durante la sesión y libera sensores y bloqueo de suspensión al pausar o terminar.
5. Al llegar a la meta, Store comprueba el identificador, el conteo y un mínimo temporal. Consume el identificador antes de otorgar saldo. Cancelar, iniciar otra sesión o reiniciar el proceso invalida la anterior.
6. El servicio consulta saldo, horario y cuota. Descuenta uso con el reloj monotónico mientras una app protegida está al frente y la pantalla está activa. La barrera vuelve al agotarse la condición de acceso; la comprobación temporal se realiza aproximadamente cada segundo y los cambios de ventana se atienden al recibir el evento.

## Persistencia y reloj

SharedPreferences privado guarda las reglas y registros acotados. Las actualizaciones frecuentes de consumo/estadísticas se aplican en segundo plano; las concesiones de tiempo se guardan de forma síncrona. No hay base de datos remota. No se guardan coordenadas ni fotogramas.

Saldo y emergencia usan elapsedRealtime para que atrasar el reloj de pared no extienda un pase. BOOT_COUNT y el desfase entre relojes invalidan los pases si hay reinicio o un salto significativo de hora. Las cuotas usan una fecha máxima persistida para no reiniciarse al atrasar el día. No hay fuente externa de tiempo confiable ni resistencia total a manipulación del sistema, root o almacenamiento privado.

Accesibilidad recibe solamente TYPE_WINDOW_STATE_CHANGED. No se permite recuperar contenido de ventanas, leer nodos de otras apps, enviar gestos, registrar teclas ni impedir desinstalación. UsageEvents, si está autorizado, se consulta en reconexiones y cuando hace falta recuperar el paquete en primer plano. La app mantiene accesibles Ajustes y sus propias pantallas para que la persona conserve el control.

## Seguridad y privacidad de la entrega

La APK usa firma de depuración para pruebas personales. No es una publicación comercial firmada. Solo MainActivity se exporta como interfaz de actividad; WorkoutActivity no puede abrirse desde otras apps. El servicio exportado está protegido por BIND_ACCESSIBILITY_SERVICE, reservado al sistema. Los tests viven en una APK de instrumentación separada y no se incluyen como controles de desbloqueo dentro de la app.

Las imágenes de referencia pertenecen al documento del usuario. Las fuentes Montserrat e Inter incluyen sus licencias OFL en assets. El modelo de ML Kit y las dependencias conservan las condiciones de sus proveedores; la API de poses es beta. No se implementa cifrado adicional de los datos de aplicación: se utiliza el aislamiento y almacenamiento protegido de Android, con copias de seguridad deshabilitadas.

Referencias técnicas consultadas: [ML Kit Pose Detection en Android](https://developers.google.com/ml-kit/vision/pose-detection/android), [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [declaración de permisos](https://developer.android.com/training/permissions/declaring).
