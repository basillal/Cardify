@echo off
rem Build installer using jpackage. Set JAVA_HOME to your JDK 17 path.
if "%JAVA_HOME%"=="" (
  echo Please set JAVA_HOME to your JDK and rerun.
  exit /b 1
)

set "JPACKAGE=%JAVA_HOME%\bin\jpackage"

rem Auto-increment version number in version.properties and read it
powershell -Command ^
  "$propsFile = 'src\main\resources\version.properties';" ^
  "if (Test-Path $propsFile) {" ^
  "  $props = ConvertFrom-StringData (Get-Content $propsFile -Raw);" ^
  "  $version = $props.version;" ^
  "} else {" ^
  "  $version = '1.0.0';" ^
  "}" ^
  "if ($version -match '^(\d+)\.(\d+)\.(\d+)$') {" ^
  "  $major = [int]$Matches[1];" ^
  "  $minor = [int]$Matches[2];" ^
  "  $patch = [int]$Matches[3] + 1;" ^
  "  $newVersion = \"$major.$minor.$patch\";" ^
  "} else {" ^
  "  $newVersion = '1.0.1';" ^
  "}" ^
  "'version=' + $newVersion | Set-Content $propsFile;" ^
  "Write-Output $newVersion" > temp_version.txt

set /p APP_VERSION=<temp_version.txt
del temp_version.txt

echo Incrementing app version to: %APP_VERSION%

echo Rebuilding jar with Maven...
call mvn package
if %ERRORLEVEL% neq 0 (
  echo Maven build failed.
  exit /b 1
)

echo Generating installer package...

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
  --app-version %APP_VERSION% ^
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
