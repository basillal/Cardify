@echo off
rem Build installer using jpackage. Set JAVA_HOME to your JDK 17 path.
if "%JAVA_HOME%"=="" (
  echo Please set JAVA_HOME to your JDK and rerun.
  exit /b 1
)

set "JPACKAGE=%JAVA_HOME%\bin\jpackage"
"%JPACKAGE%" --type exe ^
  --input target ^
  --main-jar Cardify-1.0-SNAPSHOT.jar ^
  --main-class org.example.cardify.MainApp ^
  --name Cardify ^
  --app-version 1.0.0 ^
  --vendor "Your Name or Org" ^
  --icon src\main\resources\app-icon.ico ^
  --dest installer ^
  --win-shortcut ^
  --win-menu ^
  --module-path target\dependency ^
  --add-modules javafx.controls,javafx.fxml,javafx.web,java.desktop ^
  --verbose

echo jpackage finished. Installer is in the installer\ folder.
