# Verificación de FitBlock

La versión 1.0.0-demo se verifica en el emulador **FitBlock_Test_API35**, con Android 15, imagen Google APIs x86_64 y JBR 21. Queda pendiente la calibración en el teléfono del usuario.

## Comprobaciones automatizadas

La suite contiene 29 pruebas unitarias y 22 pruebas instrumentadas. Los reportes se generan en `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/androidTests/connected/debug` y `app/build/reports/lint-results-debug.html`.

Las pruebas unitarias de PolicyTest cubren horarios normales y nocturnos, días elegidos, vencimiento, cuotas, emergencia, dificultad progresiva, rechazo de ciclos demasiado rápidos, seguimiento perdido, escenas estáticas, cadencia de pasos y geometría de poses. Incluyen ejemplos sintéticos de flexiones, sentadillas, saltos y estiramiento para comprobar tanto aceptación como rechazo. Los ejemplos sintéticos no sustituyen validación con una persona.

Las pruebas instrumentadas ejecutan código en Android.

SportCatalogTest comprueba identificadores únicos, el repertorio de más de cien actividades, los cinco ejercicios verificados originales, búsqueda sin distinguir acentos, categorías y el reloj de sesión al pausar/reanudar.

AccountsAndHistoryTest cubre creación y autenticación, rechazo de duplicados y contraseñas incorrectas, almacenamiento derivado de contraseñas, separación entre perfiles, asociación única del historial de invitado, metas y favoritos, guardado idempotente y ausencia de crédito por cronómetro. Comprueba que las sesiones parciales no aumentan la racha, que las actividades pendientes impiden cambiar de cuenta y que la mejor racha puede reconstruirse con registros anteriores a los 90 días de agregados. También comprueba que las insignias desbloqueadas se conservan al interrumpirse la racha.

NextVersionFlowTest recorre la interfaz real para crear cuenta, buscar Yoga, marcar favorito, configurar una meta de un minuto, iniciar/pausar/reanudar y completar el cronómetro con la pantalla apagada. Guarda la actividad, comprueba la racha, prepara un equipo como vista previa, añade una insignia a la colección, consulta el historial y vuelve a iniciar sesión. Usa los nodos de accesibilidad directamente: el comando de consola `uiautomator dump` puede fallar al esperar inactividad en una pantalla cuyo cronómetro cambia continuamente. Las capturas `v03-*.png` se generan con MediaStore en `Pictures/FitBlockQA03` del emulador y se copian a `docs/qa` para la revisión visual.

| Prueba | Comportamiento comprobado |
| --- | --- |
| Concesión anticipada o identificador falso | No se otorga saldo |
| Sesión completa | Otorga una sola recompensa, incluso si se intenta repetir |
| Sesión cancelada o reemplazada | No puede otorgar recompensa |
| Emergencia | Una por día y aumento de la siguiente meta |
| Consumo y cuota | Una app no elegida no consume; una elegida se bloquea al llegar a su límite |
| Saldo compartido | El límite individual de una app no recorta la recompensa ganada para la bolsa compartida |
| Reinicio | Invalida el pase sin borrar selección |
| Bloqueo integrado | Chrome sin saldo muestra barrera; el botón abre actividades; emergencia permite entrar; el vencimiento vuelve a bloquear |
| Cámara sin Internet | Se abre cámara frontal del emulador y el análisis de pose; una escena sin persona no genera recompensa; salir no genera saldo |
| Metas | Actualiza los valores antiguos por debajo del mínimo y conserva la meta de la sesión iniciada |
| Video de saltos | El modelo de FitBlock registra los tres ciclos completos del clip |

El ensayo de video archivado de la versión 0.3 también registró dos sentadillas completas y tres flexiones completas. Los clips se analizaron con ML Kit y las mismas reglas de conteo a 10 cuadros por segundo. El resultado está en `docs/qa/pose-video-results.xml`. La fuente de los saltos es [Jumping jacks and burpees, de Taco Fleur](https://commons.wikimedia.org/wiki/File:Jumping_jacks_and_burpees.webm). Los clips de sentadillas y flexiones proceden de [exercise_counter](https://github.com/Shalbulov/exercise_counter/tree/main/videos).

`scripts/smoke_walk.py` prueba la pantalla de permisos, el mínimo de la meta y una sesión completa con eventos simulados del acelerómetro del emulador. Registra el progreso tras apagar la pantalla, pausa, reanuda y comprueba el saldo al alcanzar 100 pasos.

La prueba integrada acorta desde el propio arnés de pruebas el plazo de emergencia para verificar el vencimiento sin esperar dos minutos. Las pruebas de Store crean sesiones sintéticas con duración mínima. Ese código vive en la APK de pruebas y no añade accesos de desbloqueo al producto.

Lint se ejecuta con errores bloqueantes. Sus advertencias incluyen internacionalización pendiente de textos en español, versiones de dependencias y uso de algunas APIs heredadas compatibles. No se ha creado una línea base que oculte errores.

La revisión visual incluye bienvenida, selección de apps, inicio, actividades, progreso y barrera. La jerarquía tipográfica se corrigió para usar el peso adecuado de Montserrat. Las capturas se guardan en `docs/qa` y se regeneran al ejecutar las pruebas correspondientes. La captura más relevante del bloqueo viene de BlockingFlowTest, que mantiene los servicios de accesibilidad activos: el comando de consola uiautomator dump puede suspenderlos temporalmente y no sirve para verificar esa barrera.

## Prueba recomendada en tu teléfono

1. Instala la APK, acepta el consentimiento, elige una app prescindible y activa accesibilidad.
2. Abre esa app sin saldo: debe mostrarse la pantalla de bloqueo. Comprueba que teléfono, SMS y Ajustes continúan disponibles.
3. Selecciona el mínimo de 10 sentadillas y, si quieres probar el bloqueo pronto, configura 1 minuto ganado. Completa las indicaciones de brazos y haz las sentadillas de perfil.
4. Abre la app elegida, úsala un minuto y comprueba que se vuelve a bloquear. El tiempo fuera de ella no debe reducir la bolsa.
5. Repite con flexiones, saltos y estiramiento. Prueba movimientos parciales, salir del encuadre, tapar la cámara y mover el teléfono: no deben sumar repeticiones inválidas.
6. Inicia una caminata de 100 pasos, apaga la pantalla y lleva el teléfono en el bolsillo. Compara los pasos de FitBlock con tu conteo. Algunos podómetros reportan pasos en grupos.
7. Comprueba que la cámara se pausa al salir y que la caminata sigue en segundo plano hasta pausarla, terminarla o alcanzar la meta.
8. Configura un horario corto y comprueba sus extremos. Prueba también uno que cruce medianoche y el límite individual de la app.
9. Solicita emergencia: la app elegida debe abrir durante dos minutos. Al vencer se debe reactivar la barrera y no debe concederse otra emergencia ese día.
10. Reinicia el teléfono, verifica el servicio y habilita recuperación por uso de apps. Comprueba con y sin las restricciones de batería que permita configurar tu fabricante.
11. Exporta el historial y comprueba que no contiene fotos ni credenciales; incluye coordenadas si guardaste rutas GPS. Borra los datos y verifica que se desactiva la protección y reaparece el consentimiento.
12. Crea tu cuenta desde **Mi cuenta**. Verifica que el historial de invitado se asocia y que al cerrar sesión no aparece en el perfil de invitado ni en una segunda cuenta.
13. Busca un deporte, márcalo favorito y registra una sesión con duración elegida. Comprueba pausa, pantalla apagada y reabrir la app. Solo las metas completadas suman a la racha; el cronómetro no da saldo para apps.
14. Abre **Rachas y premios**, prepara un reto conjunto y revisa **Muy pronto**. Añade una insignia alcanzada a la colección, cierra y vuelve a iniciar sesión para comprobar que persiste.

## Aspectos aún sin validar

Queda por medir la precisión con distintos teléfonos, posiciones del dispositivo, condiciones de luz y ritmos de caminata. La prueba con pantalla apagada usa sensores simulados. El consumo de batería, la temperatura y las restricciones de cada fabricante requieren pruebas físicas.


## Verificación añadida para Demo Comercial

- RouteTrackerTest: ruido estacionario, distancia de caminata, precisión, coordenadas no finitas, puntos antiguos o fuera de orden, velocidades imposibles, segmentos al pausar/perder señal y cruce del meridiano 180°.
- ActivityTrackingTest: filtros a 20/40/65 ms por muestra, independencia de los ejes, entradas no finitas y cambio abrupto de encuadre. Se mantiene el caso de podómetro por lotes, cadencia, sacudidas, retos de brazos y estiramiento observado.
- CommercialDataTest (Android): recuperación del punto de control, guardado parcial sin recompensa, concesión única al completar, persistencia y eliminación de ruta, datos por fecha al cruzar medianoche, total histórico sin duplicación y rechazo de datos inconsistentes.
- `python scripts/smoke_commercial.py`: banner, presentación de solo lectura, mapa de ejemplo, consentimiento GPS opcional, caminata con pantalla apagada, pausa, cierre forzado, recuperación del mismo conteo, guardado parcial y vistas Día/Semana/Mes.
- `python scripts/smoke_walk.py`: mantiene la prueba de sensores reales del emulador hasta completar la meta y acreditar saldo con pantalla apagada.

Los puntos GPS de las pruebas unitarias y el recorrido de presentación son ejemplos sintéticos. No prueban la precisión GPS en exteriores ni representan recorridos realizados por una persona. La ruta pública de muestra está etiquetada y no se registra en Progreso.

Para validar en el teléfono: activa Ruta GPS, acepta ubicación precisa, camina al aire libre, pausa/reanuda y verifica que no se unan esos segmentos. Prueba también denegar ubicación, retirar Internet, señal débil, guardado parcial y borrado de ruta. Estos ensayos físicos no pueden ejecutarse con el emulador.
