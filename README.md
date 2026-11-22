Este es un proyecto de Kotlin Multiplatform dirigido a Android y Escritorio (JVM).

* [/shared](https://www.google.com/search?q=./shared/src) es para el código que se compartirá entre tus aplicaciones de Compose Multiplatform.
Contiene varias subcarpetas:
* [commonMain](https://www.google.com/search?q=./shared/src/commonMain/kotlin) es para el código común a todas las plataformas de destino (targets).
* Las demás carpetas son para el código Kotlin que se compilará únicamente para la plataforma indicada en el nombre de la carpeta.
Por ejemplo, si quieres usar CoreCrypto de Apple para la parte de iOS de tu aplicación Kotlin,
la carpeta [iosMain](https://www.google.com/search?q=./shared/src/iosMain/kotlin) sería el lugar adecuado para dichas llamadas.
De manera similar, si deseas editar la parte específica de Escritorio (JVM), la carpeta [jvmMain](https://www.google.com/search?q=./shared/src/jvmMain/kotlin)
es la ubicación apropiada.

### Ejecutar las aplicaciones

Usa las configuraciones de ejecución proporcionadas en el menú desplegable de ejecución (run widget) en la barra de herramientas de tu IDE. También puedes usar estos comandos y opciones:

* Aplicación Android: `./gradlew :androidApp:assembleDebug`
* Aplicación de Escritorio:
* Recarga en caliente (Hot reload): `./gradlew :desktopApp:hotRun --auto`
* Ejecución estándar: `./gradlew :desktopApp:run`



### Ejecutar pruebas

Usa el botón de ejecución en el margen del editor (gutter) de tu IDE, o ejecuta las pruebas usando tareas de Gradle:

* Pruebas de Android: `./gradlew :shared:testAndroidHostTest`
* Pruebas de Escritorio: `./gradlew :shared:jvmTest`

---

Obtén más información sobre [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
