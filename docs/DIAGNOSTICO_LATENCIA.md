# Diagnóstico de latencia — versión 0.1.3

## Cómo realizar la prueba

1. Cierra la aplicación anterior e instala la versión 0.1.3. Comprueba el número en la cabecera.
2. Mantén activada **Guardar diagnóstico de latencia** y pulsa **Iniciar procesamiento**.
3. Comprueba que el panel indica **Registrando**. Usa Discord normalmente hasta que notes el retraso.
4. Mientras se oye el retraso, pulsa **Marcar retraso** y después **Reiniciar solo la salida**. Oirás un corte mientras la aplicación reabre el mismo dispositivo de reproducción. La línea de captura sigue abierta.
5. Escucha si el retraso desaparece tras el corte. Si continúa, puedes pulsar **Detener** e **Iniciar procesamiento** para comparar con un reinicio completo.
6. En **Abrir registros** encontrarás el CSV de la sesión marcada. Comparte ese archivo e indica qué oíste después de reabrir la salida. La marca y el resultado de la reapertura están en el mismo archivo.

Los archivos se guardan en `%USERPROFILE%\.discord-audio-guard\diagnostics`. El nombre empieza por `audio-`, contiene la fecha/hora **UTC** del inicio y un identificador único. Cada inicio crea un archivo; no sobrescribe sesiones anteriores. El registro también se cierra al salir normalmente de la aplicación. Ante un cierre forzado se conserva lo que se haya volcado, normalmente hasta el último segundo; puede faltar `END`.

La casilla puede cambiarse con el procesamiento detenido. Se activa por defecto al abrir esta versión. Desactivarla permite comparar el comportamiento sin consultas periódicas ni cronometraje adicional. El diagnóstico **no cambia el tamaño de los búferes, la sincronización, el DSP ni reinicia el audio automáticamente**.

## Qué se mide

El monitor consulta cada 100 ms las colas que Java Sound permite ver y la cola interna. Cada segundo escribe medias y máximos observados. El periodo puede alargarse si Windows o el controlador retardan el monitor; `sample_gap_max_ms` permite detectarlo.

- **Entrada:** bytes disponibles para leer en `TargetDataLine`, convertidos a ms.
- **Interno:** frames almacenados en el búfer adaptativo, convertidos a ms.
- **Salida:** capacidad de `SourceDataLine` menos espacio disponible, convertidos a ms.
- **Pendiente visible + lookahead:** suma aproximada de esas colas y el retardo DSP aplicado en ese momento.
- **Sincronización:** corrección de velocidad en ppm y porcentaje de muestras en el límite de ±5000 ppm.
- **Operaciones:** duración de cada lectura de bloque, DSP, inserción en el búfer, extracción del búfer y escritura de bloque. Se guardan medias del intervalo, máximos acumulados de la sesión y duración de la operación que siga en curso.
- **Contadores:** bloques procesados, escrituras incompletas, errores al consultar dispositivos y posiciones de captura/reproducción reportadas por Java Sound.

Con 48 kHz, PCM16 y estéreo, 192 bytes equivalen a 1 ms; 48 frames equivalen a 1 ms.

El tiempo en `read` o `write` incluye las esperas normales del dispositivo y la planificación del sistema: **no se suma a las colas para calcular latencia**. El tiempo de inserción/extracción incluye la operación completa y cualquier espera. Sus máximos son acumulados; las medias corresponden a las operaciones terminadas desde el resumen anterior.

Las tres colas se consultan sucesivamente, no de forma atómica. La estimación excluye audio en tránsito en los bloques, colas ocultas de Windows/VB-CABLE y la latencia física del dispositivo. Los máximos de las colas son máximos **muestreados**, no una garantía de detectar todos los picos. Una cola desconocida se muestra como **no disponible**, y la estimación total queda sin valor. La antigua cifra fija se identifica ahora como **referencia inicial teórica**.

## Formato del archivo

CSV UTF-8, separador coma, campos entre comillas y punto decimal independiente del idioma de Windows. La primera fila contiene las columnas. No se guardan muestras PCM ni conversaciones; sí nombres de dispositivos, versión de Java/sistema y métricas numéricas.

| Campo o grupo | Significado |
|---|---|
| `event` | `START`, `SAMPLE`, `MARK`, `END`, `LIMIT` o un evento de reinicio de salida |
| Eventos de salida | `OUTPUT_RESTART_BEGIN` indica el comienzo de la reapertura. `OUTPUT_RESTARTED` indica que la salida arrancó. `OUTPUT_RESTART_FAILED` y `OUTPUT_RESTART_CANCELLED` indican fallo o cancelación. |
| `output_position_generation` | Identifica cada apertura de la salida. Compara posiciones de reproducción solo dentro de la misma apertura. Las medias de la ventana que contiene el reinicio pueden incluir muestras de ambos lados. |
| `utc`, `elapsed_ms` | Hora UTC y tiempo monotónico desde el inicio del diagnóstico |
| `marker` | Número de marca; su hora es la del clic, aunque la escritura sea posterior |
| `engine_state` | Estado al escribir la fila; el evento `END` y su nota determinan el final de la sesión |
| `samples`, `sample_gap_max_ms` | Muestras en el resumen y mayor separación observada entre consultas |
| `*_valid_samples` | Cantidad de observaciones válidas de cada magnitud |
| `*_mean_ms`, `*_max_ms` | Media y máximo observado de cada cola/retardo en ese intervalo |
| `rate_ppm_mean`, `rate_ppm_max`, `rate_limit_percent` | Corrección media, máximo con signo y porcentaje de muestras en el límite |
| `*_frame_position` | Posición acumulada del backend, con contador de 64 bits; no es un timestamp físico |
| `blocks_total`, `write_errors_total`, `sampling_errors_total` | Contadores desde el inicio de esa sesión |
| `{read,dsp,put,take,write}_operations_total` | Operaciones terminadas, incluidas las que terminan durante el cierre |
| `*_units_total` | Bytes para `read/write`; frames para `dsp/put/take` |
| `*_mean_ms`, `*_max_session_ms`, `*_inflight_ms` | Media de operaciones terminadas en el intervalo, máximo acumulado y tiempo actual de una operación aún en curso |
| `note` | Configuración inicial, descripción de marca o motivo del final |

Un campo vacío significa desconocido o no aplicable, nunca cero. Las marcas son filas separadas y no reinician las medias del intervalo. La última fila incluye el resumen parcial pendiente. Si hay saturación de consultas o una operación de dispositivo no devuelve, la UI señala **Lectura sin actualizar** tras dos segundos sin muestras.

## Impacto y conservación

Los hilos de audio solo actualizan contadores y tiempos en memoria. Las consultas periódicas, el formateo y las escrituras pertenecen a un hilo de prioridad normal. Un error al crear/escribir el archivo se muestra en el panel y no detiene el procesamiento. El monitor no captura ni reproduce audio adicional.

Cada archivo tiene un límite aproximado de 100 MiB; al alcanzarlo se registra `LIMIT` y se detiene el diagnóstico, manteniendo el audio. Las sesiones anteriores se conservan para no perder evidencias. Pueden eliminarse manualmente desde **Abrir registros** cuando ya no sean necesarias. El registro general de errores sigue en `%USERPROFILE%\.discord-audio-guard\logs`.

La prueba permite localizar acumulación en colas visibles. Si permanecen estables pese al retraso audible, el siguiente paso es una medición con señal de referencia y loopback del recorrido afectado.
