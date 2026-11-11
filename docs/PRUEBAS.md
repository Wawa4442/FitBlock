# Verificación de FitBlock

La versión 0.2.0 se verifica en el emulador **FitBlock_Test_API35**, con Android 15, imagen Google APIs x86_64 y JBR 21. Queda pendiente la calibración en el teléfono del usuario.

## Comprobaciones automatizadas

La suite contiene 20 pruebas unitarias y 11 pruebas instrumentadas. Los reportes se generan en `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/androidTests/connected/debug` y `app/build/reports/lint-results-debug.html`.

Las pruebas unitarias de PolicyTest cubren horarios normales y nocturnos, días elegidos, vencimiento, cuotas, emergencia, dificultad progresiva, rechazo de ciclos demasiado rápidos, seguimiento perdido, escenas estáticas, cadencia de pasos y geometría de poses. Incluyen ejemplos sintéticos de flexiones, sentadillas, saltos y estiramiento para comprobar tanto aceptación como rechazo. Los ejemplos sintéticos no sustituyen validación con una persona.

Las pruebas instrumentadas ejecutan código en Android.

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

El ensayo de video de esta entrega también registró dos sentadillas completas y tres flexiones completas. Los clips se analizaron con ML Kit y las mismas reglas de conteo a 10 cuadros por segundo. El resultado está en `docs/qa/pose-video-results.xml`. La fuente de los saltos es [Jumping jacks and burpees, de Taco Fleur](https://commons.wikimedia.org/wiki/File:Jumping_jacks_and_burpees.webm). Los clips de sentadillas y flexiones proceden de [exercise_counter](https://github.com/Shalbulov/exercise_counter/tree/main/videos).

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
11. Exporta el historial y comprueba que no contiene fotos ni coordenadas. Borra los datos y verifica que se desactiva la protección y reaparece el consentimiento.

## Aspectos aún sin validar

Queda por medir la precisión con distintos teléfonos, posiciones del dispositivo, condiciones de luz y ritmos de caminata. La prueba con pantalla apagada usa sensores simulados. El consumo de batería, la temperatura y las restricciones de cada fabricante requieren pruebas físicas.
