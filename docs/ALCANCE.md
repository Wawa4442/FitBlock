# Cobertura del Plan de Negocios

El documento original sigue intacto. El MVP cubre restricción de aplicaciones, validación física y panel básico de estadísticas, los tres módulos que el plan identifica como objetivo de la primera versión Android. Los valores numéricos que no especifica el plan se documentan como decisiones configurables en README.md.

| Aspecto del plan | Implementación del MVP | Límite o fase posterior |
| --- | --- | --- |
| Android Studio primero | Proyecto Android nativo Kotlin y Gradle Wrapper | Sin versión iOS/Flutter |
| Identidad visual | #10172A, #38D996, #22B8CF, blanco; Montserrat/Inter; isotipo extraído del documento | Sin cambiar el documento original |
| Eslogan | Gana tu pantalla, muévete primero | Conservado en bienvenida y barrera |
| Elección de apps | Catálogo real de apps con entrada de launcher, búsqueda y selección persistida | No enumera paquetes sin interfaz de lanzamiento |
| Aplicaciones esenciales | Exclusión de teléfono, SMS, teclado, inicio, ajustes, contactos y paquetes conocidos de emergencia/mapas | La identificación de apps esenciales puede variar por fabricante |
| Bloqueo | Ventana de accesibilidad sobre la app seleccionada sin saldo o con cuota agotada | Requiere permiso voluntario; no es Device Owner ni protección contra desinstalación |
| Persistencia | Servicio gestionado por Android y almacenamiento privado de reglas, saldo y cuotas | El fabricante puede detener/restringir el servicio |
| Recuperación | Acceso opcional a UsageEvents para recuperar el paquete en primer plano al reconectar o desbloquear | Sin este permiso depende del siguiente evento de ventana |
| Tiempos de bloqueo | Horario de inicio/fin, días de semana y horarios que cruzan medianoche | Fuera del horario no se restringe |
| Límites diarios | Límite por defecto e individual por paquete | No calcula toda la actividad histórica previa a instalar FitBlock |
| Metas | Cinco actividades, todas configurables | No hay prescripción de rutinas médicas |
| Desbloqueo condicional | Recompensa única por sesión completa y bolsa compartida de minutos | Sesiones parciales no dan minutos |
| Tiempo ganado | Cantidad por actividad y vigencia de la bolsa | No es un bloqueo por tiempo absoluto de toda la app después de ganar saldo |
| Consumo | Descuento según tiempo transcurrido al usar apps seleccionadas, en horario y con pantalla activa | Muestra consumo supervisado, no todo el Screen Time de Android |
| Sentadillas | Ángulo de rodilla, orientación, articulaciones visibles y ciclo completo | Necesita calibración física con distintas personas |
| Flexiones | Ángulo de codo, alineación hombro-cadera-tobillo y ciclo completo | No distingue identidad ni garantiza técnica deportiva perfecta |
| Saltos en el sitio | Saltos de tijera con separación de tobillos y subida de ambos brazos | No estima altura de salto ni calorías |
| Modo discreto | Estiramiento de brazos y caminata en interiores o al aire libre | La caminata continúa con la pantalla apagada |
| Cámara antitrampas | Confianza mínima, estabilidad del teléfono, continuidad, tiempos de fase, rango de movimiento y dos indicaciones aleatorias | No hay detección robusta de pantallas externas o videos preparados |
| Pasos antitrampas | Podómetro, movimiento y cadencia. Acelerómetro como alternativa | La validación usa sensores locales y admite caminar sin GPS |
| Uso de batería | Cámara visible y servicio de caminata durante una sesión activa | Consumo real pendiente de medir en teléfonos físicos |
| Dificultad progresiva | Opción +10 % por actividad del día, máximo +40 % | Se usa número de actividades como indicador simple; el plan no fija fórmula |
| Emergencia regulada | Un pase diario de 2 minutos y +50 % a la siguiente meta | Los esenciales siguen accesibles sin usar el pase |
| Estadísticas | Hoy, gráfica de 7 días, reps/pasos/estiramientos, tiempo supervisado y eventos | Retención limitada a 90 días y 50 eventos |
| Rachas y recompensas | Racha diaria, nivel cada 5 actividades e insignias locales | Sin rankings ni premios de valor monetario |
| Privacidad | Consentimiento, explicación de permisos, cámara local sin guardar, sin permiso de Internet, exportación y borrado | Responsable/contacto y condiciones comerciales por definir antes de publicación |
| Suscripciones/freemium | MVP gratuito sin anuncios ni cobros | Play Billing y monetización pendientes; no hay compras simuladas |
| Social y ligas | Identificadas en el plan como evolución del producto | Requieren backend, cuentas y reglas de competencia; fuera del MVP |
| Wearables/biometría | Conservado como etapa futura del plan | No integra Wear OS, Apple Watch, Health Connect ni pulso |
| iOS | Conservado como etapa futura del plan | No solicita ni usa Screen Time API de Apple |

## Criterio de aceptación en teléfono físico

La entrega se verifica en un emulador API 35. Queda pendiente probar una sesión de cada ejercicio y la caminata con la pantalla apagada en el teléfono físico, con los sensores y los ajustes de batería del fabricante.
