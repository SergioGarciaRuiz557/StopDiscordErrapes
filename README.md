# Discord Audio Guard

**Español** · [English](#english-version)

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

---

<a id="english-version"></a>

# Discord Audio Guard — English

[Español](#discord-audio-guard) · **English**

A Windows desktop application that attenuates shouting, microphone impacts, clipping, and other sudden peaks in Discord's complete audio output. It captures PCM from a virtual device, applies dynamic compression and lookahead limiting, and plays the protected result through the selected headphones.

> This MVP does not capture `Discord.exe` directly or separate individual users. It processes the entire mix that Discord sends to the virtual cable. It is not a replacement for a system volume limit and is not a certified hearing-protection device.

## Audio flow

```text
Discord
  → CABLE Input (output selected in Discord)
  → CABLE Output (input selected in Discord Audio Guard)
  → 48 kHz / 16-bit / stereo PCM
  → L/R-linked compressor
  → L/R-linked lookahead limiter
  → maximum gain + safety clamp
  → headphones or physical output
```

The device backend (`AudioBackend`) is separated from the processor (`AudioProcessor`), allowing a future version to use WASAPI/JNI without rewriting the DSP or user interface.

## Requirements

- Windows 10 or 11.
- A 64-bit JDK 21 (Temurin, Microsoft Build of OpenJDK, or another compatible distribution).
- A virtual audio device, such as [VB-CABLE](https://vb-audio.com/Cable/).
- Gradle does not need to be installed because the repository includes the Gradle Wrapper.
- To generate an `.exe` installer with `jpackage`, JDK 21 and the WiX Toolset must be available. The application and tests do not require WiX.

JavaFX is downloaded automatically from Maven Central by Gradle. You do not need to install the JavaFX SDK manually. Working without Gradle would require downloading the appropriate SDK and configuring `--module-path`, which is not a recommended workflow for this project.

## Build and run

From PowerShell in the project root:

```powershell
./gradlew clean test
./gradlew run
./gradlew build
```

Create and run a distribution containing scripts and all dependencies:

```powershell
./gradlew installDist
./build/install/discord-audio-guard/bin/discord-audio-guard.bat
```

The regular JAR under `build/libs` is not a fat JAR and requires its dependency classpath. The supported standalone options are `installDist`, the ZIP produced by `distZip`, or the `jlink` runtime image:

```powershell
./gradlew distZip
./gradlew jlink
./build/image/bin/DiscordAudioGuard.bat
```

To create an application image and, when WiX is installed, a Windows installer:

```powershell
./gradlew jpackageImage
./gradlew jpackage
```

Artifacts are generated under `build/jpackage` and `build/distributions`. An MSI can be prepared by changing `installerType` to `msi` in `build.gradle.kts`; the MVP is configured to produce an `exe`.

## Configure VB-CABLE and Discord

1. Install VB-CABLE by following the vendor's instructions and restart Windows if requested by the installer.
2. Open Discord → User Settings → Voice & Video.
3. Select **CABLE Input** as Discord's output device.
4. Open Discord Audio Guard.
5. Select **CABLE Output** as the input. This is the recording endpoint corresponding to the cable.
6. Select your headphones or physical DAC as the output.
7. Press **Iniciar procesamiento** (Start processing) and confirm that the meters move.

Exact names may vary by version, language, or driver. Do not select the same virtual cable as both input and output, because that may create an audio loop. The application blocks the case in which both descriptors are exactly equal, but it cannot detect every loop topology that Windows can create.

## Controls

- **Threshold**: level at which compression begins. A more negative value acts sooner.
- **Ratio**: compression strength above the threshold. `6:1` means that a 6 dB increase at the input produces approximately 1 dB at the stage output.
- **Attack**: how quickly the compressor reduces gain.
- **Release**: how quickly gain recovers after the loud sound ends.
- **Knee**: gradual transition around the threshold; a wider knee usually sounds less abrupt.
- **Makeup**: gain after the compressor. For protection, keep it close to 0 dB.
- **Ceiling**: limiter ceiling. The default -2 dBFS value leaves headroom.
- **Lookahead**: intentional delay that lets the limiter detect a peak before playing it.
- **Maximum gain**: permanent final attenuation between -30 and 0 dB.
- **Full bypass**: smoothly crossfades to the signal without compressor or limiter; only the selected maximum gain and emergency numerical clamp remain active.

Recommended initial values for Discord voice audio:

| Parameter | Value |
|---|---:|
| Threshold | -18 dBFS |
| Ratio | 6:1 |
| Attack | 3 ms |
| Compressor release | 250 ms |
| Knee | 6 dB |
| Makeup | 0 dB |
| Ceiling | -2 dBFS |
| Lookahead | 5 ms |
| Limiter release | 150 ms |

If the result “breathes,” increase release. If normal voices become too quiet, raise the threshold—for example, from -24 to -18 dBFS—before adding makeup gain. For a more conservative overall limit, lower the maximum gain.

## Architecture

```text
application/       lifecycle and coordination
audio/api/         backend, input, output, and DSP interfaces
audio/device/      Java Sound enumeration and lines
audio/format/      allocation-free PCM configuration and conversion
audio/processing/  compressor, envelope, gain, lookahead, limiter, and meters
audio/engine/      pipeline, explicit state, and lock-free metrics
config/            JSON configuration with atomic writes and recovery
ui/                JavaFX views and 25 Hz metric updates
tools/             development signal generator
```

Capture and playback use separate threads and an adaptive PCM buffer to absorb jitter from Windows and Java Sound. Because VB-CABLE and the physical output have independent clocks, the reader gently adjusts its speed by at most ±0.5% through linear interpolation, keeping the reserve stable without dropping blocks or inserting silence. Before starting the headphones, the engine preloads both the adaptive buffer and the device buffer. Both threads reuse byte and `float` buffers, use no streams or dynamic collections, and never access JavaFX, disk, or the network. Parameters are published through an atomic reference and applied at the beginning of a block; the UI creates snapshots outside critical threads.

The displayed latency is a **theoretical estimate** based on buffer sizes reported by Java Sound and the configured lookahead. It is not a physical end-to-end measurement. Actual latency depends on Java Sound, the drivers, Windows, VB-CABLE, and the physical device.

## Configuration and logs

```text
%USERPROFILE%/.discord-audio-guard/config.json
%USERPROFILE%/.discord-audio-guard/logs/discord-audio-guard.log
```

If the JSON file is corrupt, it is renamed to `config.corrupt-DATE.json` and safe values are loaded. Persisted values include devices, DSP settings, bypass, format, buffers, window geometry, and basic preferences.

## Tests

Unit tests do not open physical devices. They cover:

- Little-endian 16-bit PCM, endpoints, and stereo ordering.
- dBFS/gain conversion and clamps.
- Compressor curve, knee, ratio, attack, release, and live updates.
- Limiter ceiling, single-sample peaks, stereo linking, lookahead, and release.
- Silence, sine waves, saturation, peaks, and consecutive DSP-chain blocks.
- Persistence and recovery from corrupt JSON.

`SignalGenerator` produces silence, a 1 kHz sine wave, noise, a progressive rise, and peaks in one or both channels.

## Troubleshooting

**VB-CABLE does not appear**

Confirm that the driver is installed, that Windows lists it among recording/playback devices, and press **Actualizar dispositivos** (Refresh devices). Restart the application after installing a driver.

**Unsupported format**

The MVP requires 48,000 Hz, signed 16-bit stereo PCM. Configure both cable endpoints and the headphones for 48 kHz in the Windows sound control panel. There is no resampler yet.

**A line could not be opened**

Close applications using the device in exclusive mode, check microphone permissions for desktop applications, and select the device again. Java Sound may expose similar names for different endpoints.

**There is no sound, but the input meter moves**

Check the selected physical output, its Windows volume, and whether another application is using it in exclusive mode.

**The input meter does not move**

Confirm that Discord's output is `CABLE Input`, the application's input is `CABLE Output`, and Discord is currently playing audio.

**Dropouts or high latency**

Close intensive workloads, avoid Bluetooth when latency matters, and use current drivers. Java Sound does not provide low-latency WASAPI control; actual buffer sizes may exceed the requested values.

**Loop or feedback**

Stop the engine immediately and inspect the graph. The headphones must be the final output, not an endpoint that feeds the virtual input again.

## Known limitations

- Java Sound does not guarantee low latency or predictable exclusive access across all Windows drivers.
- Only 48 kHz, 16-bit, little-endian stereo is supported; there is no sample-rate conversion.
- The complete Discord mix is processed, not individual users.
- The limiter is a sample-peak limiter, not an oversampled true-peak limiter.
- There is no per-process capture, equalizer, gate, FFT, noise suppression, tray integration, hotkeys, or automatic updates.
- Mixer availability and names can only be validated on the machine running the application.

## Next steps

1. Manual tests with several VB-CABLE versions and USB and Bluetooth devices.
2. Integration tests with simulated inputs/outputs and allocation/GC-pause measurements.
3. Shared and exclusive WASAPI backend behind `AudioBackend`.
4. Physical loopback latency measurement and diagnostic mode.
5. Presets, system tray, and profiles while keeping DSP independent.
6. Optional oversampled true-peak limiter.

## Download the installer

[Download Discord Audio Guard 0.1.1 for Windows (.exe)](https://github.com/SergioGarciaRuiz557/StopDiscordErrapes/releases/download/v0.1.1/DiscordAudioGuard-0.1.1.exe)
