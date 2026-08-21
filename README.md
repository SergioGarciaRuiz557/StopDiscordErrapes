# Discord Audio Guard

Aplicación de escritorio para Windows que atenúa gritos, golpes de micrófono, saturaciones y otros picos repentinos en el audio completo de Discord. Captura PCM desde un dispositivo virtual, aplica compresión dinámica y limitación con *lookahead*, y reproduce el resultado en los auriculares elegidos.

> Este MVP no captura `Discord.exe` directamente ni separa usuarios. Procesa conjuntamente toda la mezcla que Discord envía al cable virtual. No sustituye un límite de volumen del sistema ni constituye un dispositivo de protección auditiva certificado.

## Flujo de audio

```text
Discord
  → CABLE Input (salida elegida en Discord)
  → CABLE Output (entrada elegida en Discord Audio Guard)
  → PCM 48 kHz / 16-bit / estéreo
  → compresor enlazado L/R
  → limitador enlazado L/R con lookahead
  → ganancia máxima + clamp de seguridad
  → auriculares o salida física
```

El backend de dispositivos (`AudioBackend`) está separado del procesador (`AudioProcessor`), por lo que una versión futura puede usar WASAPI/JNI sin reescribir el DSP ni la interfaz.

## Requisitos

- Windows 10 u 11.
- JDK 21 de 64 bits (Temurin, Microsoft Build of OpenJDK u otra distribución compatible).
- Un dispositivo de audio virtual, por ejemplo [VB-CABLE](https://vb-audio.com/Cable/).
- Gradle no necesita estar instalado: el repositorio incluye Gradle Wrapper.
- Para generar un instalador `.exe` con `jpackage`, JDK 21 y WiX Toolset deben estar disponibles. La aplicación y las pruebas no necesitan WiX.

JavaFX se obtiene automáticamente desde Maven Central con Gradle. No hay que instalar manualmente el SDK de JavaFX. Si se trabaja sin Gradle, habría que descargar el SDK apropiado y configurar `--module-path`, una modalidad que este proyecto no recomienda.

## Compilar y ejecutar

Desde PowerShell en la raíz del proyecto:

```powershell
./gradlew clean test
./gradlew run
./gradlew build
```

Crear y ejecutar una distribución con scripts y todas las dependencias:

```powershell
./gradlew installDist
./build/install/discord-audio-guard/bin/discord-audio-guard.bat
```

El JAR normal de `build/libs` no es un *fat JAR*: necesita su classpath de dependencias. La forma soportada de ejecución independiente es `installDist`, el ZIP de `distZip` o la imagen de runtime de `jlink`:

```powershell
./gradlew distZip
./gradlew jlink
./build/image/bin/DiscordAudioGuard.bat
```

Para crear una imagen de aplicación y, si WiX está instalado, un instalador Windows:

```powershell
./gradlew jpackageImage
./gradlew jpackage
```

Los artefactos se generan bajo `build/jpackage` y `build/distributions`. Un MSI puede prepararse cambiando `installerType` a `msi` en `build.gradle.kts`; para el MVP se configura `exe`.

## Configurar VB-CABLE y Discord

1. Instala VB-CABLE siguiendo las instrucciones del proveedor y reinicia Windows si el instalador lo solicita.
2. Abre Discord → Ajustes de usuario → Voz y vídeo.
3. Elige **CABLE Input** como dispositivo de salida de Discord.
4. Abre Discord Audio Guard.
5. Elige **CABLE Output** como entrada. Es el extremo de grabación correspondiente al cable.
6. Elige tus auriculares o DAC físico como salida.
7. Pulsa **Iniciar procesamiento** y comprueba que se mueven los medidores.

Los nombres exactos pueden variar por versión, idioma o controlador. No selecciones el mismo cable virtual como entrada y salida: produciría un posible bucle de audio. La aplicación bloquea el caso en que ambos descriptores son exactamente iguales, pero no puede detectar todas las topologías de bucle creadas en Windows.

## Controles

- **Threshold**: nivel a partir del cual comprime. Un valor más negativo actúa antes.
- **Ratio**: intensidad de compresión por encima del umbral. `6:1` significa que 6 dB de subida en la entrada producen aproximadamente 1 dB en la salida de la etapa.
- **Attack**: rapidez con que el compresor reduce la ganancia.
- **Release**: rapidez con que recupera la ganancia al terminar el sonido fuerte.
- **Knee**: transición gradual alrededor del umbral; un knee mayor suele sonar menos abrupto.
- **Makeup**: ganancia posterior al compresor. Para protección conviene mantenerla cerca de 0 dB.
- **Ceiling**: techo del limitador. El valor predeterminado de -2 dBFS deja margen.
- **Lookahead**: retraso intencionado que permite detectar el pico antes de reproducirlo.
- **Ganancia máxima**: atenuación permanente final entre -30 y 0 dB.
- **Bypass completo**: cruza suavemente hacia la señal sin compresor ni limitador; solo conserva la ganancia máxima elegida y el clamp numérico de emergencia.

Valores iniciales recomendados para voz de Discord:

| Parámetro | Valor |
|---|---:|
| Threshold | -18 dBFS |
| Ratio | 6:1 |
| Attack | 3 ms |
| Release del compresor | 250 ms |
| Knee | 6 dB |
| Makeup | 0 dB |
| Ceiling | -2 dBFS |
| Lookahead | 5 ms |
| Release del limitador | 150 ms |

Si el resultado “respira”, aumenta el release. Si voces normales quedan demasiado bajas, sube el threshold (por ejemplo, de -24 a -18 dBFS) antes de añadir makeup. Si se desea un límite general más conservador, baja la ganancia máxima.

## Arquitectura

```text
application/       ciclo de vida y coordinación
audio/api/         interfaces para backend, entrada, salida y DSP
audio/device/      enumeración y líneas Java Sound
audio/format/      configuración y conversión PCM sin asignaciones
audio/processing/  compresor, envolvente, ganancia, lookahead, limitador y medidores
audio/engine/      pipeline, estado explícito y métricas lock-free
config/            configuración JSON con escritura atómica y recuperación
ui/                vistas JavaFX y actualización de métricas a 25 Hz
tools/             generador de señales de desarrollo
```

La captura y la reproducción usan hilos separados y un búfer PCM adaptativo para absorber el *jitter* de Windows y de Java Sound. Como VB-CABLE y la salida física tienen relojes independientes, el lector ajusta suavemente su velocidad en un margen máximo de ±0,5 % mediante interpolación lineal, manteniendo estable la reserva sin descartar bloques ni insertar silencios. Antes de arrancar los auriculares, el motor precarga tanto el búfer adaptativo como el del dispositivo. Ambos hilos reutilizan buffers de bytes y `float`, no usan streams ni colecciones dinámicas y no acceden a JavaFX, disco o red. Los parámetros se publican con una referencia atómica y se aplican al comienzo de un bloque; la interfaz crea snapshots fuera de los hilos críticos.

La latencia mostrada es una **estimación teórica** basada en tamaños de buffer reportados por Java Sound y el lookahead. No es una medición física extremo a extremo. La latencia real depende de Java Sound, los controladores, Windows, VB-CABLE y el dispositivo físico.

## Configuración y logs

```text
%USERPROFILE%/.discord-audio-guard/config.json
%USERPROFILE%/.discord-audio-guard/logs/discord-audio-guard.log
```

Si el JSON está dañado, se renombra a `config.corrupt-FECHA.json` y se cargan valores seguros. Se guardan dispositivos, DSP, bypass, formato, buffers, ventana y preferencias básicas.

## Pruebas

Las pruebas unitarias no abren dispositivos físicos. Cubren:

- PCM 16-bit little endian, extremos y estéreo.
- Conversión dBFS/ganancia y clamps.
- Curva, knee, ratio, ataque, release y cambio en caliente del compresor.
- Techo, pico de una muestra, enlace estéreo, lookahead y release del limitador.
- Silencio, seno, saturación, picos y bloques consecutivos en la cadena DSP.
- Persistencia y recuperación de JSON corrupto.

`SignalGenerator` genera silencio, seno de 1 kHz, ruido, subida progresiva y picos en uno o dos canales.

## Resolución de problemas

**No aparece VB-CABLE**  
Comprueba que el controlador está instalado, que Windows lo muestra en dispositivos de grabación/reproducción y pulsa “Actualizar dispositivos”. Reinicia la aplicación después de instalar un controlador.

**Formato no soportado**  
El MVP exige 48.000 Hz, PCM signed de 16 bits y estéreo. Configura ambos extremos del cable y los auriculares a 48 kHz en el panel de sonido de Windows. Todavía no hay resampler.

**No se pudo abrir una línea**  
Cierra aplicaciones que usen el dispositivo en modo exclusivo, verifica permisos de micrófono para aplicaciones de escritorio y vuelve a elegir el dispositivo. Java Sound puede exponer nombres similares para endpoints diferentes.

**No se oye nada pero el medidor de entrada se mueve**  
Revisa la salida física seleccionada, su volumen de Windows y que no esté usada en modo exclusivo.

**No se mueve la entrada**  
Confirma que la salida de Discord es `CABLE Input`, que la entrada de la aplicación es `CABLE Output` y que Discord está reproduciendo audio.

**Cortes o latencia alta**  
Cierra cargas intensivas, evita Bluetooth si la latencia importa y usa controladores actualizados. Java Sound no ofrece control WASAPI de baja latencia; los tamaños reales pueden superar los solicitados.

**Bucle o realimentación**  
Detén inmediatamente el motor y revisa el grafo. Los auriculares deben ser la salida final, no el extremo que vuelve a alimentar la entrada virtual.

## Limitaciones conocidas

- Java Sound no garantiza latencia baja ni acceso exclusivo predecible en todos los controladores Windows.
- Solo se admite 48 kHz, 16-bit, estéreo, little endian; no hay conversión de frecuencia.
- Se procesa la mezcla completa de Discord, no usuarios individuales.
- El limitador es de pico de muestra, no *true peak* con sobremuestreo.
- No hay captura por proceso, ecualizador, puerta, FFT, supresión de ruido, bandeja, hotkeys ni actualizaciones automáticas.
- La disponibilidad y nombres de mixers solo pueden validarse en la máquina que ejecuta la aplicación.

## Próximos pasos

1. Pruebas manuales con varias versiones de VB-CABLE, dispositivos USB y Bluetooth.
2. Pruebas de integración con entradas/salidas simuladas y medición de asignaciones/pausas GC.
3. Backend WASAPI compartido y exclusivo detrás de `AudioBackend`.
4. Medición de latencia física por loopback y modo de diagnóstico.
5. Presets, bandeja del sistema y perfiles, manteniendo el DSP independiente.
6. Limitador *true peak* opcional con sobremuestreo.

## Descargar el instalador

[Descargar Discord Audio Guard 0.1.1 para Windows (.exe)](https://github.com/SergioGarciaRuiz557/StopDiscordErrapes/releases/download/v0.1.1/DiscordAudioGuard-0.1.1.exe)
