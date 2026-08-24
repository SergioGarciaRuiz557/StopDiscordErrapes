# Discord Audio Guard — documentación técnica

**Versión documentada:** 0.1.1  
**Plataforma objetivo:** Windows 10/11, x64  
**Lenguaje y runtime:** Java 21 modular  
**Interfaz:** JavaFX 21  
**Sistema de construcción:** Gradle 8.12.1 con Kotlin DSL  
**Alcance:** arquitectura, flujo de ejecución, audio digital, concurrencia, persistencia, interfaz, pruebas y distribución.

## 1. Propósito del sistema

Discord Audio Guard es una aplicación de escritorio que se coloca entre la salida de Discord y los auriculares. Discord envía su mezcla a un cable de audio virtual; la aplicación captura esa señal, controla su dinámica y reproduce el resultado en un dispositivo físico.

El objetivo funcional es reducir la exposición a gritos, golpes de micrófono, saturaciones y otros cambios súbitos de volumen. La aplicación procesa la mezcla completa, no usuarios individuales, y no se conecta a la API de Discord ni inspecciona su proceso.

El sistema realiza cinco trabajos principales:

1. Enumera dispositivos de captura y reproducción compatibles con el formato requerido.
2. Abre una entrada y una salida de Java Sound.
3. Convierte PCM de 16 bits a muestras `float`, aplica DSP y vuelve a codificar PCM.
4. Desacopla los relojes de entrada y salida mediante un búfer adaptativo.
5. Presenta controles, medidores, estado, diagnósticos y persistencia local.

No es un dispositivo médico ni una protección auditiva certificada. El techo del limitador controla muestras digitales, pero no conoce el volumen analógico final del sistema, del amplificador o de los auriculares.

## 2. Visión arquitectónica

La aplicación sigue una separación por capas y responsabilidades:

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Arranque y coordinación | `application` | Ciclo de vida de JavaFX, creación de componentes y estado global. |
| Presentación | `ui` | Construcción de vistas, eventos de usuario y actualización periódica de medidores. |
| Contratos de audio | `audio.api` | Interfaces que desacoplan motor, hardware y DSP. |
| Dispositivos | `audio.device` | Integración concreta con `javax.sound.sampled`. |
| Motor | `audio.engine` | Hilos, estados, buffers, sincronización, lectura y reproducción. |
| Formato | `audio.format` | Definición del PCM y conversión `byte[]` ↔ `float[]`. |
| Procesamiento | `audio.processing` | Compresor, limitador, envolventes, bypass y medición de nivel. |
| Configuración | `config` | Modelo inmutable y persistencia JSON segura. |
| Utilidades | `util`, `tools` | Matemática, decibelios, creación de hilos y señales de prueba. |

La dirección general de dependencias es `UI → ApplicationController → AudioEngine → contratos/backend/DSP`. El backend conoce Java Sound; el DSP no conoce dispositivos, ventanas ni persistencia. Esta separación permite sustituir Java Sound por WASAPI sin reescribir el procesamiento dinámico.

## 3. Tecnologías y dependencias

### 3.1 Java 21

Se utilizan records, pattern matching para `instanceof`, módulos JPMS y las APIs estándar de concurrencia y audio. Java 21 es también la base con la que `jlink` crea un runtime reducido y `jpackage` produce una aplicación autocontenida.

### 3.2 JavaFX 21.0.5

`javafx.controls` proporciona `Application`, `Stage`, layouts, controles, `AnimationTimer` y diálogos. La UI se construye íntegramente por código Java; no existe FXML.

### 3.3 Java Sound

`java.desktop` aporta `AudioSystem`, `Mixer`, `TargetDataLine`, `SourceDataLine` y `AudioFormat`. El backend actual depende de estas abstracciones, que en Windows se conectan a los dispositivos expuestos por el sistema.

### 3.4 Jackson 2.17.2

Serializa y deserializa records de configuración. Los paquetes que contienen records persistidos se abren a Jackson en `module-info.java` para permitir reflexión.

### 3.5 SLF4J y Logback

SLF4J es la fachada de logging. Logback escribe en consola y en archivos rotatorios bajo el perfil del usuario.

### 3.6 JUnit 5 y AssertJ

JUnit ejecuta las pruebas y AssertJ proporciona aserciones legibles. Las pruebas de audio usan dispositivos simulados y señales sintéticas; no abren hardware real.

### 3.7 Plugin Badass JLink

El plugin `org.beryx.jlink` genera un runtime modular reducido, una imagen de aplicación y paquetes nativos mediante `jpackage`.

## 4. Estructura del repositorio

| Ruta | Contenido |
|---|---|
| `src/main/java` | Código de producción y descriptor modular. |
| `src/main/resources` | CSS, logging, PNG de JavaFX e ICO de Windows. |
| `src/test/java` | Pruebas unitarias y de integración simulada. |
| `gradle/wrapper` | Wrapper que fija la versión de Gradle. |
| `build.gradle.kts` | Dependencias, compilación, ejecución y empaquetado. |
| `settings.gradle.kts` | Nombre lógico del proyecto. |
| `gradle.properties` | Memoria, codificación y daemon de Gradle. |
| `build` | Artefactos generados; no es fuente mantenida manualmente. |

## 5. Sistema modular: `module-info.java`

El módulo se llama `com.discordaudioguard`.

### Dependencias declaradas

- `java.desktop`: Java Sound y clases de escritorio usadas por el empaquetado y el audio.
- `javafx.controls`: interfaz JavaFX.
- `com.fasterxml.jackson.databind`: persistencia JSON.
- `org.slf4j`: logging desacoplado de su implementación.

### Paquetes exportados

Los paquetes funcionales se exportan para permitir uso desde pruebas, herramientas y posibles consumidores modulares. La exportación no implica reflexión; solo visibilidad pública entre módulos.

### Paquetes abiertos

- `application` se abre a `javafx.graphics`, necesario para que JavaFX cree y gestione la clase `Application`.
- `config`, `audio.format` y `audio.processing` se abren a Jackson, porque sus records forman parte del árbol JSON.

## 6. Paquete `application`

### 6.1 `DiscordAudioGuardApplication`

Es el punto de entrada JavaFX y extiende `Application`.

#### Campos

- `controller`: conserva el coordinador de aplicación durante toda la vida de la ventana.
- `viewController`: conserva el controlador de presentación para detener su temporizador al cerrar.

#### `start(Stage stage)`

JavaFX invoca este método en su hilo de aplicación. El método:

1. Crea `ApplicationController`, que a su vez carga configuración y construye el motor.
2. Crea `MainView` con los parámetros DSP persistidos.
3. Conecta vista y lógica mediante `MainViewController`.
4. Crea una escena de 1080 × 760 píxeles.
5. Carga `application.css` desde el classpath.
6. Carga `app-icon.png` y lo asigna a la ventana.
7. Define título y tamaño mínimo.
8. Restaura posición, tamaño y maximización.
9. Instala el manejador de cierre, que guarda geometría y cierra UI y motor.
10. Muestra la ventana y, en la primera ejecución, el diálogo introductorio.

`Objects.requireNonNull` convierte la ausencia del icono en un error inmediato y explícito durante el arranque, en vez de permitir un fallo ambiguo más tarde.

#### `showIntroduction()`

Construye un diálogo informativo con la topología esperada de VB-CABLE. Tras cerrarlo marca la introducción como mostrada, evitando repetirla en futuras ejecuciones.

#### `restoreWindow(...)`

Restaura dimensiones siempre. Solo aplica coordenadas si son finitas, porque los valores predeterminados usan `NaN` para representar “posición todavía no guardada”.

#### `main(String[] args)`

Llama a `Application.launch`, que inicializa el toolkit de JavaFX y termina delegando en `start`.

### 6.2 `ApplicationController`

Es la fachada de casos de uso entre UI, configuración y motor de audio. Evita que los controles JavaFX conozcan detalles de Java Sound o DSP.

#### Dependencias y estado

- `ConfigurationRepository`: contrato de persistencia.
- `JavaSoundAudioBackend`: implementación de hardware actual.
- `AudioMetrics`: almacén compartido de telemetría.
- `DynamicsProcessor`: cadena DSP única y reutilizable.
- `AudioEngine`: coordinador de streaming.
- `stateListeners`: observadores de cambios de estado.
- `configuration`: snapshot inmutable vigente, publicado como `volatile`.
- `state`: estado de aplicación con dispositivos y mensaje, también `volatile`.

#### Constructores

El constructor público crea las dependencias reales. El constructor de paquete acepta repositorio y backend, una decisión que facilita pruebas o sustituciones dentro del paquete.

Al construir:

1. Carga la configuración.
2. Dimensiona el DSP con frecuencia y tamaño máximo de bloque.
3. Construye el motor con formato y número de bloques.
4. Suscribe un listener al motor para traducir estados técnicos a `ApplicationState`.

#### `refreshDevices()`

Solicita todos los mixers al backend, separa entradas y salidas mediante streams y publica un mensaje con el resultado. Las listas resultantes son no modificables por el uso de `Stream.toList()`.

#### `start(input, output)`

Valida selecciones no nulas, persiste sus identificadores antes de arrancar y delega en `AudioEngine.start`. Guardar primero permite recordar la selección incluso si la apertura posterior falla.

#### `stop()`

Delega la parada asíncrona al motor.

#### `updateParameters(...)`

Publica parámetros al DSP y actualiza el snapshot de configuración en memoria. El DSP los adopta de forma segura al inicio del siguiente bloque. El guardado a disco se realiza al cerrar o cuando otra operación de persistencia guarda el snapshot completo.

#### `resetParameters()`

Restaura `ProcessingParameters.DEFAULT`.

#### Consultas y listeners

- `metricsSnapshot()` obtiene una fotografía consistente para la UI.
- `configuration()` y `state()` exponen snapshots inmutables.
- `addStateListener(...)` registra observadores.
- `notifyState()` recorre la lista segura frente a modificaciones concurrentes.

#### Persistencia de ciclo de vida

- `saveWindow(...)` actualiza y guarda geometría.
- `markIntroductionShown()` actualiza y guarda `firstRun`.
- `close()` solicita la parada del motor y guarda la configuración completa.

### 6.3 `ApplicationState`

Record inmutable que agrupa:

- estado del motor;
- mensaje legible;
- entradas disponibles;
- salidas disponibles.

`initial()` representa una aplicación detenida, lista y sin dispositivos enumerados. Usar un único record reduce estados parciales difíciles de sincronizar en la UI.

## 7. Paquete `audio.api`

Estas interfaces forman la frontera de inversión de dependencias del subsistema de audio.

### 7.1 `AudioBackend`

Define tres operaciones:

- `scanDevices(format)`: descubre endpoints.
- `openInput(device, format, bufferBlocks)`: abre captura.
- `openOutput(device, format, bufferBlocks)`: abre reproducción.

Su finalidad es permitir un backend futuro WASAPI, ASIO o simulado sin alterar `AudioEngine`.

### 7.2 `AudioInput`

Abstracción mínima de captura:

- `start()` inicia el flujo.
- `read(...)` entrega bytes PCM y puede bloquear.
- `stop()` detiene la línea.
- `bufferSizeBytes()` informa del búfer real.
- `close()` libera el recurso.

Extiende `AutoCloseable` para usarse con `try-with-resources`.

### 7.3 `AudioOutput`

Es el contrato simétrico de reproducción:

- `write(...)` introduce PCM en el dispositivo y puede bloquear.
- El resto de métodos controla ciclo de vida y consulta capacidad.

### 7.4 `AudioProcessor`

Contrato DSP independiente del formato de bytes:

- `process(...)` transforma arrays intercalados de `float`.
- `updateParameters(...)` permite cambios en caliente.
- `latencyFrames()` declara retraso algorítmico.
- `reset()` limpia memoria temporal antes de una nueva sesión.

## 8. Paquete `audio.device`

### 8.1 `AudioDeviceDescriptor`

Record que transporta identidad y capacidades de un mixer:

- `id`: UUID determinista utilizado para persistencia.
- `name`, `description`, `vendor`, `version`: metadatos del proveedor.
- `inputSupported`, `outputSupported`: direcciones expuestas.
- `requestedFormatSupported`: compatibilidad declarada con el formato solicitado.

`toString()` produce la etiqueta mostrada por los `ComboBox`.

### 8.2 `AudioDeviceScanner`

`scan(configuration)` convierte la configuración a `AudioFormat`, crea descriptores de línea para captura y reproducción y recorre `AudioSystem.getMixerInfo()`.

Para cada mixer:

1. Consulta si expone líneas target o source.
2. Omite mixers que no sirven para audio de entrada ni de salida.
3. Comprueba compatibilidad con el formato exacto.
4. Construye una clave estable con nombre, proveedor y descripción separados por NUL.
5. Deriva un UUID determinista con `UUID.nameUUIDFromBytes`.
6. Añade el descriptor.

Un fallo al inspeccionar un mixer no aborta todo el escaneo: se registra como advertencia y se continúa. El resultado se copia como lista inmutable.

### 8.3 `JavaSoundAudioBackend`

Implementa `AudioBackend` con Java Sound.

#### `MINIMUM_STABLE_BUFFER_BLOCKS`

Fuerza al menos 12 bloques en las líneas físicas aunque la configuración solicite menos. Su finalidad es proteger contra jitter del planificador y drivers de Windows. Este mínimo aumenta estabilidad a cambio de memoria y latencia potencial.

#### `openInput(...)`

Valida capacidad de entrada, localiza el mixer por ID, comprueba `TargetDataLine`, abre la línea con el formato y tamaño de búfer calculado y la envuelve en `JavaSoundAudioInput`.

#### `openOutput(...)`

Realiza el proceso equivalente para `SourceDataLine` y devuelve `JavaSoundAudioOutput`.

#### `findMixer(...)`

Regenera para cada mixer el UUID usado durante el escaneo y devuelve el coincidente. Si el dispositivo desapareció entre enumeración y apertura lanza `AudioDeviceException`.

#### `unsupported(...)`

Centraliza un mensaje de formato no compatible con frecuencia, profundidad, canales y endianness esperados.

#### `AudioDeviceException`

Excepción runtime específica del backend. Puede contener solo mensaje o encadenar `LineUnavailableException`.

### 8.4 `JavaSoundAudioInput`

Adaptador fino sobre `TargetDataLine`. No añade buffering propio: delega inicio, lectura, tamaño y cierre. `stop()` consulta `isRunning()` para evitar llamadas redundantes.

### 8.5 `JavaSoundAudioOutput`

Adaptador sobre `SourceDataLine`. Al detener una línea activa ejecuta `flush()` antes de `stop()`, descartando audio pendiente para que la parada sea inmediata y no reproduzca cola antigua.

## 9. Paquete `audio.format`

### 9.1 `AudioFormatConfiguration`

Record que describe el contrato PCM de extremo a extremo.

Los valores predeterminados del código son 48.000 Hz, 16 bits, dos canales, signed, little endian y 256 frames por bloque.

El constructor compacto rechaza:

- frecuencia no positiva;
- profundidad distinta de 16 bits;
- número de canales distinto de dos;
- bloques menores de 32 o mayores de 4096 frames.

Métodos derivados:

- `toJavaSoundFormat()`: crea el objeto de Java Sound.
- `bytesPerFrame()`: canales × bytes por muestra; actualmente 4 bytes.
- `blockBytes()`: frames × bytes por frame.
- `blockDurationMillis()`: duración temporal de un bloque.

La aplicación no contiene un conversor general para profundidad, canales o endianness. Por eso estas restricciones son invariantes, no simples preferencias.

### 9.2 `Pcm16Decoder`

Convierte pares little-endian a `short` con signo y normaliza a `float` dividiendo por 32768. El rango resultante es `[-1, 0.9999695...]`.

El método limita el número de muestras tanto por `byteCount` como por la capacidad del destino, evitando escritura fuera del array.

### 9.3 `Pcm16Encoder`

Realiza la operación inversa:

1. Limita cada muestra a `[-1, 1]`.
2. Usa 32768 para la rama negativa y 32767 para la positiva, respetando la asimetría de PCM16.
3. Redondea al entero más cercano.
4. Escribe byte bajo y byte alto.

El clamp final impide wrap-around si una etapa DSP genera accidentalmente valores fuera de rango.

## 10. Paquete `audio.processing`

### 10.1 Modelo de muestras

El DSP utiliza arrays `float` estéreo intercalados:

```text
[L0, R0, L1, R1, L2, R2, ...]
```

Un frame contiene una muestra izquierda y una derecha. Los detectores están enlazados en estéreo para aplicar la misma ganancia a ambos canales y conservar la imagen estéreo.

### 10.2 `ProcessingParameters`

Record raíz e inmutable de parámetros DSP. Agrupa compresor, limitador, bypass y ganancia máxima final.

#### Valores predeterminados

| Parámetro | Valor |
|---|---:|
| Compresor | Activado |
| Threshold | -18 dBFS |
| Ratio | 6:1 |
| Attack | 3 ms |
| Release del compresor | 250 ms |
| Knee | 6 dB |
| Makeup | 0 dB |
| Limitador | Activado |
| Ceiling | -2 dBFS |
| Lookahead | 5 ms |
| Release del limitador | 150 ms |
| Bypass | Desactivado |
| Ganancia máxima | 0 dB |

El constructor exige subrecords no nulos y valida la ganancia final entre -30 y 0 dB. `validated()` devuelve el propio objeto porque la validación ya ocurre al construir.

Los métodos `with...` implementan actualizaciones inmutables: crean un record nuevo conservando el resto de campos.

#### `CompressorSettings`

Valida los rangos de threshold, ratio, attack, release, knee y makeup. `withEnabled` cambia únicamente la activación.

#### `LimiterSettings`

Valida ceiling, lookahead y release. El ceiling nunca puede llegar a 0 dBFS; el máximo admitido es -0,1 dBFS.

#### `range(...)`

Rechaza valores no finitos y valores fuera del intervalo cerrado. Centraliza mensajes consistentes para UI, JSON y pruebas.

### 10.3 `DecibelUtils` como base matemática

Aunque reside en `util`, sus fórmulas sostienen el DSP:

- Conversión de amplitud: `dB = 20 · log10(|x|)`.
- Conversión a ganancia: `x = 10^(dB/20)`.
- Coeficiente temporal: `a = exp(-1 / (tiempoSegundos · frecuenciaMuestreo))`.

Amplitudes menores de `1e-6`, cero, NaN o infinito negativo se representan como -120 dB para mantener métricas finitas y dibujables.

### 10.4 `EnvelopeFollower`

Detector de envolvente de primer orden.

- `configure(...)` calcula coeficientes de ataque y release.
- `process(level)` elige ataque cuando el nivel sube y release cuando baja.
- `reset()` vuelve a silencio.

La ecuación por muestra es `envolvente = a · anterior + (1-a) · entrada`. Ataque corto permite reaccionar rápido; release largo evita bombeo excesivo.

### 10.5 `GainSmoother`

Tiene una estructura matemática similar al seguidor de envolvente, pero suaviza ganancia en dB. Usa ataque cuando la ganancia objetivo es más negativa —se necesita atenuar— y release cuando recupera hacia 0 dB.

Separar detección y suavizado de ganancia permite que la curva estática y el comportamiento temporal evolucionen de forma independiente.

### 10.6 `Compressor`

Compresor feed-forward, enlazado en estéreo y con soft knee.

#### Estado

- `sampleRate`: base temporal.
- `envelope`: nivel detectado suavizado.
- `gainSmoother`: ganancia aplicada suavizada.
- `settings`: parámetros activos.
- `reductionDb`: reducción más reciente para telemetría.

#### `setSettings(...)`

Evita trabajo si el record no cambió. Cuando cambia, actualiza parámetros y recalcula coeficientes temporales sin reconstruir el procesador.

#### `process(...)`

Por cada frame:

1. Toma el máximo absoluto de L/R como detector enlazado.
2. Suaviza el detector.
3. Convierte el nivel a dBFS.
4. Calcula reducción según threshold, ratio y knee.
5. Suaviza la ganancia negativa.
6. Añade makeup si el compresor está activado.
7. Convierte la ganancia a lineal.
8. Multiplica ambos canales por la misma ganancia.

#### `calculateReductionDb(...)`

Implementa la curva estática:

- Por debajo del umbral y fuera de knee: 0 dB de reducción.
- Por encima: `over - over/ratio`.
- Dentro del soft knee: transición cuadrática continua.

Ratio 1:1 produce reducción cero. Ratios altos aproximan limitación, aunque el limitador posterior sigue siendo responsable del techo estricto.

#### `reset()`

Limpia envolvente, suavizador y métrica para evitar que una sesión herede ganancia de la anterior.

### 10.7 `LookaheadBuffer`

Delay circular estéreo de capacidad fija. Reserva memoria una sola vez y nunca redimensiona durante audio.

- `setDelayFrames(...)` limita el retraso a la capacidad.
- `push(...)` escribe el frame actual.
- `delayedLeft/Right()` devuelve el frame situado `delayFrames` detrás.
- `peakInWindow()` busca el pico máximo desde el frame retrasado hasta el actual.
- `advance()` mueve el índice circular.
- `reset()` rellena ambos canales con cero.

El frame actual está disponible para detección antes de que el frame retrasado salga. Esa diferencia temporal es el lookahead.

### 10.8 `PeakLimiter`

Limitador brickwall de pico de muestra con lookahead y detector estéreo enlazado.

#### Construcción

Reserva hasta 20 ms de delay, que coincide con el máximo permitido por configuración.

#### `setSettings(...)`

Recalcula coeficiente de release y convierte milisegundos de lookahead a frames.

#### `processInPlace(...)`

Por cada frame:

1. Inserta L/R en el delay.
2. Obtiene el pico de toda la ventana anticipada.
3. Convierte ceiling de dB a amplitud.
4. Calcula ganancia objetivo `ceiling / peak` si hay exceso.
5. Aplica reducción inmediatamente; la recuperación usa release exponencial.
6. Lee el frame retrasado y aplica la misma ganancia a L/R.
7. Limita numéricamente a `[-1, 1]`.
8. Avanza el buffer.

La reducción publicada es `-amplitudeToDb(gain)` y nunca negativa.

#### Coste computacional

`peakInWindow()` recorre la ventana completa para cada frame. Con 5 ms a 48 kHz son unas 240 posiciones por frame. Es sencillo y determinista, aunque una deque monotónica permitiría reducir este coste de O(frames × lookahead) a O(frames).

### 10.9 `LevelMeter`

Calcula por bloque:

- pico absoluto izquierdo;
- pico absoluto derecho;
- RMS combinado de ambos canales.

El RMS se calcula como raíz de la media de cuadrados de todas las muestras L/R. Los resultados se convierten a dBFS mediante `DecibelUtils`.

### 10.10 `DynamicsProcessor`

Cadena DSP completa e implementación de `AudioProcessor`.

#### Memoria y publicación de parámetros

- `wetBuffer` se reserva al construir según el bloque máximo.
- `pendingParameters` es un `AtomicReference` escrito por la UI y leído por captura.
- `activeParameters` solo se modifica en el hilo de audio.
- `bypassStep` define una transición de 5 ms.

#### `process(...)`

Orden exacto:

1. Adopta parámetros pendientes.
2. Mide entrada.
3. Procesa compresor hacia `wetBuffer`.
4. Limita `wetBuffer` in-place.
5. Calcula ganancia final.
6. Hace crossfade entre señal procesada y seca según bypass.
7. Aplica ganancia máxima y clamp de seguridad.
8. Mide salida.
9. Publica niveles y reducciones.

El crossfade evita clics al activar o desactivar bypass. Incluso en bypass se conserva la ganancia máxima final y el clamp numérico.

#### `applyPendingParameters()`

Compara referencias, no igualdad estructural. Como cada cambio crea un record nuevo, una referencia distinta significa que hay parámetros por aplicar. La adopción ocurre en frontera de bloque y evita mutaciones a mitad de procesamiento.

#### `latencyFrames()` y `reset()`

La latencia algorítmica es la del lookahead. El reset limpia estados dinámicos, pero conserva parámetros.

## 11. Paquete `audio.engine`

### 11.1 `AudioEngineState`

Máquina de estados explícita:

- `STOPPED`: recursos cerrados.
- `STARTING`: apertura y precarga.
- `RUNNING`: captura y reproducción activas.
- `STOPPING`: parada solicitada y recursos en cierre.
- `ERROR`: terminación inesperada.

Cada valor tiene `displayName` en español para la UI.

### 11.2 `AudioPipeline`

Adapta el mundo de bytes del backend al mundo `float` del DSP.

Al construir reserva `inputSamples` y `outputSamples` con tamaño de bloque × canales. `process(...)` calcula frames válidos, decodifica, llama al procesador y codifica únicamente las muestras correspondientes.

La reutilización de arrays evita crear objetos en cada bloque.

### 11.3 `AudioMetrics`

Almacén de telemetría no bloqueante para la UI. Los campos son `volatile`, por lo que el hilo JavaFX ve valores recientes sin tomar locks del audio.

#### Niveles y reducción

`publishLevels(...)` copia resultados de los medidores y reducciones del compresor y limitador.

#### Rendimiento

`blockCompleted(...)` incrementa bloques, calcula microsegundos, media incremental, máximo y porcentaje del presupuesto temporal del bloque.

La media incremental evita guardar un historial. El porcentaje representa solo DSP y conversión medidos en captura; no incluye espera de dispositivos ni renderizado de UI.

#### Errores y latencia

- `incrementWriteErrors()` cuenta escrituras incompletas.
- `setEstimatedLatencyMillis(...)` publica una estimación, no una medición física.
- `setSynchronization(...)` publica reserva adaptativa y corrección en partes por millón.

#### `snapshot()`

Crea un record inmutable para consumo de la UI. La reducción total se calcula sumando compresor y limitador.

### 11.4 `AdaptivePcmBuffer`

Búfer circular PCM estéreo que desacopla los relojes de VB-CABLE y del dispositivo físico.

#### Estructura

- Arrays `short[] left/right`: almacenamiento separado por canal.
- `readIndex/writeIndex`: cursores circulares.
- `sizeFrames`: ocupación actual.
- `readPhase`: posición fraccional del lector.
- `playbackRate`: velocidad aplicada recientemente.
- `ReentrantLock`: protege estructura.
- `dataAvailable` y `spaceAvailable`: condiciones productor/consumidor.
- `closed`: termina esperas de ambos hilos.

#### Control de deriva

La velocidad se calcula como:

```text
error = (framesDisponibles - objetivo) / objetivo
velocidad = clamp(1 + error × 0,01, 0,995, 1,005)
```

Si el búfer está por encima del objetivo, el lector consume ligeramente más rápido. Si está por debajo, consume ligeramente más lento. El margen máximo de ±0,5 % evita cambios bruscos de tono.

#### `putPcm16LittleEndian(...)`

Valida frames estéreo completos, espera espacio de forma interrumpible, decodifica cada canal a `short`, avanza el escritor y despierta lectores.

#### `awaitFrames(...)`

Bloquea el arranque hasta reunir una reserva mínima. Devuelve `false` si el buffer se cierra antes de alcanzar el objetivo.

#### `readPcm16LittleEndian(...)`

Espera un bloque más dos frames de margen, calcula velocidad, limita la velocidad máxima a la cantidad de datos segura y genera cada frame por interpolación lineal entre dos posiciones adyacentes.

Tras producir el bloque actualiza fase, cursor, ocupación y despierta al productor. Devuelve `-1` si el buffer se cerró sin un bloque completo, evitando reproducir muestras antiguas.

#### Métodos auxiliares

- `bufferedFrames()` consulta ocupación bajo lock.
- `playbackRate()` expone el último ratio.
- `close()` marca cierre y despierta todas las esperas.
- `decodeShort/encodeShort` convierten PCM little-endian localmente.
- `interpolate` realiza interpolación lineal.

### 11.5 `AudioEngine`

Es el núcleo de tiempo real. Coordina backend, procesador, estados, hilos y cierre.

#### Constantes

- `MAX_ZERO_READS = 200`: límite de lecturas vacías consecutivas.
- `MINIMUM_ADAPTIVE_BUFFER_BLOCKS = 8`: reserva adaptativa mínima.
- `MAXIMUM_DEVICE_PREFILL_BLOCKS = 11`: máximo precargado antes de iniciar salida.

#### Estado concurrente

- `AtomicReference<AudioEngineState>` protege transiciones iniciales.
- `CopyOnWriteArrayList` mantiene listeners con iteración segura.
- `stopRequested` comunica parada.
- `worker` ejecuta reproducción/coordinación.
- `captureWorker` captura y procesa.
- `activeInput/activeOutput` permiten que `stop()` desbloquee I/O cerrando líneas.

Los campos compartidos relevantes son `volatile`.

#### `start(...)`

Es `synchronized` para serializar órdenes externas. Solo permite iniciar desde `STOPPED` o `ERROR`. Rechaza IDs iguales para reducir el riesgo de realimentación. Publica `STARTING`, crea un hilo daemon de prioridad máxima y retorna sin bloquear la UI.

#### `runAudio(...)`

Secuencia principal:

1. Abre entrada y salida mediante `try-with-resources`.
2. Publica referencias activas para parada.
3. Resetea DSP.
4. Consulta el búfer real de salida.
5. Calcula precarga, objetivo y capacidad adaptativa.
6. Construye buffer, pipeline, referencia de error y bloque de playback.
7. Crea el hilo de captura.
8. Inicia entrada y captura.
9. Espera la reserva inicial.
10. Escribe varios bloques en `SourceDataLine` antes de iniciarla.
11. Inicia reproducción, calcula latencia y publica `RUNNING`.
12. Lee continuamente del buffer adaptativo y escribe bloques completos.
13. En `finally`, cierra buffer, entrada e hilo de captura.

Precargar `SourceDataLine` impide comenzar con un dispositivo vacío. La reserva adaptativa protege frente a jitter y deriva de reloj.

#### `captureAudio(...)`

Es el bucle productor:

1. Reserva una vez buffers de entrada y salida.
2. Lee un bloque completo.
3. Detecta desconexión o lecturas cero repetidas.
4. Mide tiempo alrededor del pipeline.
5. Procesa PCM.
6. Inserta el resultado en el buffer adaptativo.
7. Publica rendimiento.

Las excepciones se guardan en `captureFailure`, porque ocurren en un hilo distinto. El cierre del buffer despierta al consumidor para que propague la causa.

#### `readPlaybackBlock(...)`

Solicita un bloque adaptado, convierte ocupación a milisegundos, velocidad a ppm y publica ambas métricas. Si recibe fin inesperado, reconstruye y lanza la excepción de captura.

#### `throwCaptureFailure(...)`

Conserva excepciones runtime originales; envuelve checked exceptions; distingue entre fallo conocido y terminación inexplicada.

#### `readBlock(...)` y `writeBlock(...)`

Repiten lecturas/escrituras parciales hasta completar la longitud solicitada o hasta parada/error. Esta lógica es necesaria porque las interfaces de streaming no garantizan completar siempre la petición en una sola llamada.

#### `stop()`

Es idempotente para estados detenidos o en parada. Marca `STOPPING`, publica mensaje, detiene y cierra líneas para desbloquear operaciones nativas, e interrumpe ambos hilos.

#### Cierre seguro

`safeStop` ignora excepciones runtime durante limpieza; el objetivo en esa fase es liberar el máximo de recursos posible. `joinCapture` espera hasta un segundo y conserva el estado de interrupción si corresponde.

## 12. Concurrencia y modelo temporal

### 12.1 Hilos

| Hilo | Responsabilidad | Puede bloquear en |
|---|---|---|
| JavaFX Application Thread | Eventos, controles, diálogos y snapshots | Diálogos modales; nunca I/O de audio. |
| `discord-audio-guard-realtime` | Precarga, lectura adaptativa y escritura de salida | Condición de datos y `SourceDataLine.write`. |
| `discord-audio-guard-capture` | Captura, conversión y DSP | `TargetDataLine.read` y condición de espacio. |

Los dos hilos de audio son daemon y usan prioridad máxima mediante `ThreadUtils`. Esto reduce competencia con trabajo ordinario, aunque Java no garantiza prioridad de tiempo real duro.

### 12.2 Comunicación entre hilos

- UI → DSP: `AtomicReference<ProcessingParameters>`.
- motor → UI: listeners y `Platform.runLater`.
- captura → reproducción: `AdaptivePcmBuffer` con lock y condiciones.
- captura → motor: `AtomicReference<Throwable>`.
- audio → UI: campos `volatile` de `AudioMetrics`.
- parada → audio: boolean `volatile`, cierre de líneas e interrupciones.

### 12.3 Ausencia de asignaciones por bloque

Los arrays principales se crean al arrancar. El bucle DSP reutiliza memoria. Sí existen operaciones matemáticas y sincronización, pero no streams, listas ni objetos por muestra.

### 12.4 Cálculo de tiempos

Con 48 kHz:

| Frames por bloque | Duración | Bytes PCM estéreo |
|---:|---:|---:|
| 64 | 1,333 ms | 256 B |
| 128 | 2,667 ms | 512 B |
| 256 | 5,333 ms | 1024 B |

El backend solicita al menos 12 bloques. El motor mantiene al menos 8 bloques adaptativos y puede precargar hasta 11 en salida. La latencia estimada por el programa suma objetivo adaptativo, precarga y lookahead; no suma necesariamente toda la capacidad anunciada por drivers.

## 13. Paquete `config`

### 13.1 `ApplicationConfiguration`

Record raíz persistido. Contiene IDs de dispositivo, parámetros DSP, formato, bloques, geometría, tema y primera ejecución.

`defaults()` crea una configuración segura. El constructor valida audio completo, rango de `bufferBlocks` entre 2 y 32 y sustituye una ventana nula.

Los métodos `withDevices`, `withProcessing`, `withWindow` y `withFirstRun` mantienen inmutabilidad.

#### `WindowConfiguration`

Guarda posición, tamaño y maximización. Los valores predeterminados usan `NaN` en coordenadas. Anchos fuera de 800–5000 y altos fuera de 600–5000 se corrigen a valores seguros.

### 13.2 `ConfigurationRepository`

Contrato con `load()` y `save()`. Separa el modelo de la tecnología JSON y permite repositorios de memoria en pruebas o implementaciones futuras.

### 13.3 `JsonConfigurationRepository`

Persiste por defecto en `%USERPROFILE%/.discord-audio-guard/config.json`.

#### `load()`

Si no existe archivo devuelve defaults. Si la lectura o validación falla, registra el error, preserva el archivo dañado y continúa con defaults.

#### `save(...)`

1. Crea directorios.
2. Escribe a `config.json.tmp`.
3. Intenta mover atómicamente sobre el destino.
4. Si el sistema no soporta movimiento atómico, usa reemplazo normal.

Este patrón reduce el riesgo de dejar un JSON truncado tras un corte o cierre inesperado.

#### `preserveCorruptFile()`

Renombra el archivo a `config.corrupt-AAAAMMDD-HHMMSS.json`. Si tampoco puede hacerlo, emite advertencia pero mantiene la recuperación con defaults.

### 13.4 Esquema conceptual del JSON

| Campo | Tipo | Uso |
|---|---|---|
| `inputDeviceId` | string/null | Entrada recordada. |
| `outputDeviceId` | string/null | Salida recordada. |
| `processing` | objeto | Compresor, limitador, bypass y ganancia. |
| `audioFormat` | objeto | Frecuencia, bits, canales, signo, endian y bloque. |
| `bufferBlocks` | entero | Preferencia de buffering, sujeta a mínimos internos. |
| `window` | objeto | Geometría de ventana. |
| `darkTheme` | boolean | Reservado; actualmente no cambia CSS. |
| `firstRun` | boolean | Control del diálogo introductorio. |

## 14. Paquete `ui`

### 14.1 `MainView`

Raíz visual basada en `BorderPane`.

- Cabecera: título y subtítulo.
- Contenido: dispositivos, acciones, dinámica, medidores y estado.
- `ScrollPane`: mantiene accesibilidad con ventanas pequeñas.
- Botones: iniciar, detener y restablecer.

La vista expone getters de subcomponentes; no ejecuta casos de uso.

### 14.2 `MainViewController`

Conecta eventos y estado sin introducir lógica de audio en JavaFX.

#### Constructor

Registra acciones de botones, enlaza cambios DSP, suscribe estado del controlador e inicia un `AnimationTimer`.

#### Temporizador de medidores

`UI_INTERVAL_NANOS = 40.000.000` limita la actualización a unos 25 Hz. Esto es suficiente para una lectura fluida y evita redibujar a frecuencia de audio.

#### `refreshDevices()` y `start()`

Envuelven operaciones en `try/catch` y convierten excepciones runtime en diálogos comprensibles.

#### `applyState(...)`

Actualiza estado, dispositivos seleccionados y habilitación de controles. Mientras el motor no está `STOPPED` ni `ERROR`, bloquea inicio y selectores, y habilita detener.

#### `showError(...)`

Presenta título, cabecera contextual y mensaje original.

#### `close()`

Detiene el `AnimationTimer`, evitando actualizaciones después de cerrar la ventana.

### 14.3 `DeviceSelectionView`

Contiene dos `ComboBox` y un botón de actualización. `setDevices(...)` conserva IDs actuales, repuebla listas y vuelve a seleccionar coincidencias.

La etiqueta inferior describe el formato. Actualmente contiene el texto fijo “bloque de 128 frames”, mientras `AudioFormatConfiguration.DEFAULT` declara 256. El valor efectivo procede de configuración; esta etiqueta debería enlazarse dinámicamente al formato para evitar divergencia.

### 14.4 `DynamicsControlsView`

Construye switches y sliders para todos los parámetros DSP.

- `installListeners()` comparte un único listener de cambio.
- `publish()` evita notificaciones durante carga programática.
- `parameters()` reconstruye un snapshot validado desde controles.
- `setParameters(...)` carga un snapshot con `loading = true`.
- `slider(...)` y `addControl(...)` centralizan creación y layout.

El valor numérico de cada slider se enlaza con `asString("%.1f")`.

### 14.5 `LevelMeterView`

Presenta pico L/R de entrada y salida, reducción total, indicador `LIMITANDO` y diagnósticos.

`update(snapshot)`:

- convierte dB de -60 a 0 en progreso 0–1;
- limita visualmente reducción a 30 dB;
- muestra el indicador si el limitador reduce más de 0,1 dB;
- presenta bloques, CPU DSP, búfer, sincronía y latencia.

`setDb(...)` recorta solo la presentación a -60 dB; las métricas internas conservan hasta -120 dB.

### 14.6 `StatusView`

Presenta estado y mensaje. Limpia clases CSS anteriores antes de aplicar verde a `RUNNING` o rojo a `ERROR`.

## 15. Utilidades y herramientas

### 15.1 `MathUtils`

Ofrece `clamp` para `double` y `float`. Centraliza la operación usada en validación y seguridad DSP.

### 15.2 `DecibelUtils`

Proporciona conversiones y coeficientes temporales descritos en la sección DSP. Su constructor privado impide instanciación.

### 15.3 `ThreadUtils`

`daemonThread(name, task)` crea un hilo con nombre descriptivo, daemon y prioridad máxima. Nombrar hilos mejora diagnóstico en logs y volcados.

### 15.4 `SignalGenerator`

Fuente sintética para pruebas sin Discord:

- `silence`: rellena cero.
- `sine`: seno estéreo continuo con `startFrame` para preservar fase entre bloques.
- `noise`: ruido pseudoaleatorio reproducible por semilla.
- `sineWithPeak`: seno de fondo con un pico unitario configurable.
- `risingSine`: seno cuya amplitud interpola entre dos extremos.

## 16. Recursos

### 16.1 `application.css`

Define tipografía Segoe UI, fondo, títulos, secciones, estilos de estado e indicador de limitación. Las clases `.meter-track` y `.meter-fill` están preparadas para medidores personalizados, aunque los medidores actuales usan `ProgressBar` estándar y no asignan estas clases directamente.

### 16.2 `logback.xml`

Configura:

- archivo principal en `%USERPROFILE%/.discord-audio-guard/logs/discord-audio-guard.log`;
- rotación diaria y por tamaño;
- máximo de 5 MB por archivo;
- siete días de historial;
- límite total de 50 MB;
- salida adicional a consola;
- nivel raíz `INFO`.

### 16.3 Iconos

- `app-icon.png`: icono RGBA de alta resolución para JavaFX.
- `DiscordAudioGuard.ico`: contenedor multirresolución para launcher, acceso directo y empaquetado Windows.

## 17. Construcción y distribución

### 17.1 `settings.gradle.kts`

Fija `rootProject.name = "discord-audio-guard"`, usado en nombres de tareas y distribuciones.

### 17.2 `gradle.properties`

- Limita Gradle a 1 GiB de heap.
- Fuerza UTF-8.
- Activa daemon para acelerar compilaciones sucesivas.

### 17.3 Wrapper

`gradle-wrapper.properties` fija Gradle 8.12.1, timeout de red y validación de URL. Permite construir con `gradlew.bat` sin una instalación global de Gradle.

### 17.4 `build.gradle.kts`

#### Plugins

- `application`: ejecución y distribuciones convencionales.
- `org.openjfx.javafxplugin`: resolución de módulos JavaFX.
- `org.beryx.jlink`: runtime modular y `jpackage`.

#### Identidad

Grupo `com.discordaudioguard`, versión `0.1.1`.

#### Toolchain

Solicita Java 21 y compila con `--release 21`, evitando usar accidentalmente APIs posteriores.

#### Dependencias

Separa dependencias de producción, runtime y prueba. Logback es `runtimeOnly` porque el código depende de la fachada SLF4J.

#### Aplicación

Declara clase y módulo principal, y fuerza UTF-8 al launcher.

#### Pruebas

Configura JUnit Platform.

#### JLink

Elimina símbolos, comprime, omite headers y páginas man, y enlaza servicios. El launcher se llama `DiscordAudioGuard`.

#### JPackage

Genera un instalador `exe`, nombre e imagen `DiscordAudioGuard`, usa la versión del proyecto, integra el ICO, solicita menú Inicio y acceso directo y declara el proveedor.

### 17.5 Comandos operativos

```powershell
.\gradlew.bat clean test
.\gradlew.bat run
.\gradlew.bat jlink
.\gradlew.bat jpackageImage
.\gradlew.bat jpackage
```

`jpackageImage` produce una carpeta portable autocontenida. `jpackage` necesita WiX en Windows y produce el instalador.

## 18. Estrategia de pruebas

### 18.1 `DecibelUtilsTest`

Comprueba conversión 1,0 ↔ 0 dB, 0,5 ↔ aproximadamente -6,0206 dB, cero, NaN y clamps.

### 18.2 `Pcm16CodecTest`

Valida cero, máximos positivo/negativo, round-trip estéreo y saturación de valores fuera de rango.

### 18.3 `ProcessingParametersTest`

Confirma que rangos inseguros producen `IllegalArgumentException`.

### 18.4 `CompressorTest`

Cubre señal bajo umbral, ratios, soft knee, ataque/release, enlace estéreo y actualización en caliente.

### 18.5 `PeakLimiterTest`

Cubre transparencia bajo ceiling, techo máximo, enlace estéreo, pico de una muestra, lookahead cero y recuperación gradual.

### 18.6 `DynamicsProcessorTest`

Ejercita silencio, seno continuo, saturación, pico artificial, bloques consecutivos y transición a bypass.

### 18.7 `AdaptivePcmBufferTest`

Valida identidad a velocidad 1, consumo acelerado con exceso, ralentización con déficit y cierre sin repetición de muestras obsoletas.

### 18.8 `AudioEngineTest`

Usa `FakeBackend`, entrada, salida y procesador para comprobar streaming, precarga antes de iniciar salida, cierre de recursos, rechazo de doble arranque y prevención de bucle directo.

### 18.9 `JsonConfigurationRepositoryTest`

Comprueba round-trip JSON y recuperación con copia preservada ante corrupción.

### 18.10 Límites de cobertura

No existen pruebas automáticas para:

- dispositivos físicos ni drivers particulares;
- VB-CABLE real;
- latencia extremo a extremo;
- comportamiento bajo suspensión/cambio de dispositivo;
- rendering JavaFX y accesibilidad;
- instaladores en máquina limpia;
- sesiones prolongadas con deriva real.

Estas áreas requieren pruebas manuales o bancos de integración específicos.

## 19. Gestión de errores y observabilidad

### Errores recuperables

- Mixer individual no inspeccionable: warning y continuación.
- JSON dañado: copia, defaults y continuación.
- Movimiento atómico no soportado: fallback normal.
- Error de UI: diálogo al usuario.

### Errores que detienen audio

- dispositivo inexistente o incompatible;
- línea no disponible;
- desconexión;
- 200 lecturas vacías consecutivas;
- terminación inesperada de captura;
- cualquier excepción no controlada en motor.

El motor registra stack trace, cambia a `ERROR` y publica un mensaje de usuario.

### Diagnósticos visibles

- bloques procesados;
- porcentaje del presupuesto DSP;
- reserva adaptativa en milisegundos;
- corrección de reloj en ppm;
- latencia estimada;
- niveles y reducción.

## 20. Seguridad, robustez y decisiones de diseño

### Inmutabilidad

Records de configuración reducen mutaciones compartidas. Cada cambio produce un snapshot nuevo.

### Cierre determinista

Interfaces `AutoCloseable` y `try-with-resources` garantizan cierre aun ante excepciones.

### Protección frente a realimentación

El motor rechaza descriptores con el mismo ID. No puede detectar bucles formados por endpoints distintos conectados externamente.

### Protección numérica

Validación de rangos, dB mínimos, rechazo de no finitos y clamps evitan NaN, infinito y overflow PCM.

### Persistencia tolerante a fallos

Escritura temporal y reemplazo minimizan corrupción. Un JSON inválido nunca impide abrir la aplicación.

## 21. Limitaciones y deuda técnica

1. **Java Sound en Windows:** no ofrece control WASAPI fino ni garantías de baja latencia.
2. **Formato único:** no hay conversión general de frecuencia, canales o bits.
3. **Compensación lineal:** el buffer adapta reloj con interpolación lineal, suficiente para deriva pequeña pero no equivalente a un resampler band-limited de alta calidad.
4. **Limitador de pico de muestra:** no detecta true peak entre muestras.
5. **Búsqueda de pico:** escanea toda la ventana por frame; puede optimizarse.
6. **Mezcla completa:** no separa usuarios.
7. **Latencia estimada:** no mide el trayecto físico.
8. **Etiqueta de bloque:** la UI dice 128 mientras el default fuente es 256; debe hacerse dinámica.
9. **Tema oscuro:** el campo existe pero no se aplica.
10. **Compatibilidad visible:** `requestedFormatSupported` se almacena pero la UI no explica ni filtra claramente dispositivos incompatibles.
11. **Métricas acumuladas:** media y máximo no se reinician explícitamente entre sesiones.
12. **Errores de escritura:** se cuentan, pero no se presentan en la UI.
13. **Configuración DSP:** cambios se guardan de forma diferida salvo que coincidan con otra operación de guardado.
14. **Sin hotplug automático:** es necesario pulsar actualizar.
15. **Sin firma digital:** Windows puede mostrar SmartScreen en paquetes distribuidos.

## 22. Puntos de extensión recomendados

### Backend WASAPI

Implementar `AudioBackend`, `AudioInput` y `AudioOutput` conserva `AudioEngine` y DSP. Debe añadirse selección de modo compartido/exclusivo, formatos negociados y métricas reales de dispositivo.

### Resampler de mayor calidad

Puede sustituirse la interpolación de `AdaptivePcmBuffer` por sinc polifásico o biblioteca especializada manteniendo el mismo controlador de ocupación.

### Limitador true peak

Añadir sobremuestreo antes del detector y mantener el contrato `AudioProcessor`.

### Configuración de latencia

Exponer perfiles “baja”, “equilibrada” y “robusta” que ajusten `blockFrames`, mínimo físico, objetivo adaptativo y precarga de forma coordinada.

### Persistencia versionada

Añadir número de esquema y migraciones explícitas para futuras versiones de records.

### Pruebas de larga duración

Crear backend simulado con relojes configurables, jitter y desconexiones para verificar estabilidad durante horas de audio virtual.

## 23. Guía de mantenimiento

### Al modificar formato

Actualizar conjuntamente validación, codecs, buffer adaptativo, UI, configuración persistida y pruebas. No asumir que cambiar solo `AudioFormat` habilita un formato nuevo.

### Al añadir una etapa DSP

1. Mantener procesamiento estéreo coherente.
2. Reservar memoria en constructor.
3. Declarar latencia adicional.
4. Añadir parámetros inmutables y rangos.
5. Añadir controles UI.
6. Publicar métricas necesarias.
7. Probar silencio, señal normal, extremos y bloques consecutivos.

### Al tocar concurrencia

Verificar parada durante `STARTING`, lectura, escritura, espera por datos y espera por espacio. Nunca bloquear el hilo JavaFX con `join` o I/O de dispositivo.

### Al publicar una versión

1. Incrementar `version`.
2. Ejecutar `clean test`.
3. Generar `jpackageImage` y probar launcher.
4. Generar instalador con WiX.
5. Probar instalación, actualización, acceso directo, icono y desinstalación.
6. Verificar configuración y logs con un perfil de usuario limpio.

## 24. Glosario

| Término | Definición |
|---|---|
| PCM | Representación digital directa de amplitudes muestreadas. |
| Frame | Conjunto de muestras simultáneas; aquí una L y una R. |
| dBFS | Decibelios relativos al máximo digital. 0 dBFS es el límite numérico. |
| DSP | Procesamiento digital de señal. |
| Threshold | Nivel a partir del cual actúa el compresor. |
| Ratio | Relación entre incremento de entrada y salida sobre threshold. |
| Attack | Velocidad de entrada de la reducción. |
| Release | Velocidad de recuperación. |
| Knee | Zona de transición alrededor del threshold. |
| Makeup | Ganancia posterior al compresor. |
| Ceiling | Techo máximo del limitador. |
| Lookahead | Retraso que permite conocer un pico antes de reproducirlo. |
| Jitter | Variación temporal en disponibilidad o planificación de bloques. |
| Drift | Diferencia acumulativa entre relojes nominalmente iguales. |
| ppm | Partes por millón; aquí mide corrección relativa de velocidad. |
| Ring buffer | Almacenamiento circular con escritor y lector continuos. |
| Bypass | Mezcla hacia señal seca sin las etapas dinámicas. |
| RMS | Medida energética media de una señal. |
| True peak | Estimación de picos entre muestras tras reconstrucción. |

## 25. Conclusión técnica

El diseño separa correctamente presentación, coordinación, hardware, transporte PCM y DSP. Sus elementos más importantes son la publicación inmutable de parámetros, la reutilización de buffers, el enlace estéreo, la precarga de salida y la compensación adaptativa entre relojes. Para un MVP de escritorio, la arquitectura ofrece una base clara y extensible. El principal límite para evolucionar hacia latencias profesionales es el backend Java Sound; el principal límite DSP es la ausencia de true peak y de resampling de mayor orden.
