@echo off
rem Build installer using jpackage. Set JAVA_HOME to your JDK 17 path.
if "%JAVA_HOME%"=="" (
  echo Please set JAVA_HOME to your JDK and rerun.
  exit /b 1
)

set "JPACKAGE=%JAVA_HOME%\bin\jpackage"

rem -----------------------------------------------------------------------
rem Required modules explanation:
rem  javafx.controls, javafx.fxml, javafx.web  - JavaFX UI
rem  java.desktop  - AWT, javax.print (PrinterJob, PrintService API, BufferedImage)
rem  java.xml      - XML/SAX parser used by openhtmltopdf to parse XHTML
rem  java.naming   - JNDI, required internally by javax.print
rem  java.logging  - java.util.logging used by PDFBox and openhtmltopdf
rem  java.management - JMX, required by PDFBox internals
rem  java.sql      - Required transitively by Apache POI
rem  java.prefs    - Java Preferences API, used by PDFBox font cache
rem  jdk.unsupported - sun.misc.Unsafe used by Apache POI and PDFBox
rem  jdk.crypto.mscapi - Windows certificate/crypto support
rem  jdk.localedata  - Full locale data for correct date/text rendering
rem  jdk.charsets  - Extended charset support (UTF-8 variants etc.)
rem -----------------------------------------------------------------------

"%JPACKAGE%" --type exe ^
  --input target ^
  --main-jar Cardify-1.0-SNAPSHOT.jar ^
  --main-class org.example.cardify.MainApp ^
  --name Cardify ^
  --app-version 1.0.0 ^
  --vendor "KJSDC" ^
  --icon src\main\resources\app-icon.ico ^
  --dest dist ^
  --win-shortcut ^
  --win-menu ^
  --module-path target\dependency ^
  --add-modules javafx.controls,javafx.fxml,javafx.web,java.desktop,java.xml,java.naming,java.logging,java.management,java.sql,java.prefs,jdk.unsupported,jdk.crypto.mscapi,jdk.localedata,jdk.charsets ^
  --java-options "-Djava.awt.headless=false" ^
  --java-options "--add-opens java.desktop/sun.print=ALL-UNNAMED" ^
  --java-options "--add-opens java.desktop/java.awt.print=ALL-UNNAMED" ^
  --verbose

echo.
echo jpackage finished. Installer is in the dist\ folder.
