#!/usr/bin/env bash
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ]; then
  echo "Please set JAVA_HOME to your JDK 17+ path and rerun."
  exit 1
fi

JPACKAGE="$JAVA_HOME/bin/jpackage"

if [ ! -x "$JPACKAGE" ]; then
  echo "jpackage not found at $JPACKAGE"
  exit 1
fi

mvn -B clean package dependency:copy-dependencies -DincludeScope=runtime

"$JPACKAGE" --type deb \
  --input target \
  --main-jar Cardify-1.0-SNAPSHOT.jar \
  --main-class org.example.cardify.MainApp \
  --name Cardify \
  --app-version 1.0.0 \
  --vendor "KJSDC" \
  --icon src/main/resources/app-icon.png \
  --dest dist \
  --linux-shortcut \
  --module-path target/dependency \
  --add-modules javafx.controls,javafx.fxml,javafx.web,java.desktop,java.xml,java.naming,java.logging,java.management,java.sql,java.prefs,jdk.unsupported,jdk.localedata,jdk.charsets \
  --java-options "-Djava.awt.headless=false" \
  --java-options "--add-opens java.desktop/sun.print=ALL-UNNAMED" \
  --java-options "--add-opens java.desktop/java.awt.print=ALL-UNNAMED" \
  --verbose

echo
printf 'Linux installer created in dist/\n'
