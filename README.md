# Del Do a Wave

Ruta interactiva de piano desde cero hasta *Wave* (Antonio Carlos Jobim): teclado, lectura en clave de Sol y Fa, ritmo, oído, acordes, ii–V–I, voicings, bossa nova y estudio de la armonía de *Wave* compás a compás.

Funciona en el navegador. Con Chrome o Edge y un piano digital conectado por USB (por ejemplo, un Roland FP-10), los ejercicios responden a las teclas reales mediante Web MIDI.

La melodía, la letra y el arreglo de Ogerman de *Wave* no están incluidos; para tocarla hace falta la partitura.

`wave.html` recorre los 58 compases de *Wave* paso a paso con el piano conectado por MIDI: la app espera a que toques cada cambio de acorde con la mano izquierda (bajo o shells), con la derecha (acordes de cuatro notas) o con las dos. No incluye la melodía: si tienes la partitura en MusicXML o MIDI, puedes cargarla y la app te guía también en la melodía y en el piano del arreglo. El archivo se queda en tu navegador.

`wave.html` tiene dos vistas: el teclado con las teclas marcadas y una **cascada de notas** que caen hacia el teclado, coloreadas por mano.

## App para Android

La carpeta `android/` contiene la app para móvil y tableta: lleva dentro `index.html` y `wave.html`, funciona sin conexión y lee el piano por cable USB (`android.media.midi`), porque el WebView de Android no trae Web MIDI. Para compilarla hace falta el SDK de Android (plataforma 34) y Gradle 8.9 o posterior:

```
cd android
echo "sdk.dir=/ruta/al/sdk" > local.properties
gradle assembleRelease
```

Sin las variables `DDW_KEYSTORE` y `DDW_KEYSTORE_PASSWORD` el APK se firma con la clave de depuración. Con la misma clave de siempre, Android permite instalar una versión nueva encima sin perder los datos.
