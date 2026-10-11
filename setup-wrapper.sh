#!/usr/bin/env bash
# Gera gradlew, gradlew.bat e gradle-wrapper.jar (arquivos binarios que nao vem no zip).
# Requer: JDK 17 e Gradle instalado (https://gradle.org/install/). So precisa rodar UMA vez.
set -e
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle nao encontrado. Instale o Gradle 8.x OU copie gradlew, gradlew.bat e gradle/wrapper/gradle-wrapper.jar"
  echo "do MDK oficial do Forge 1.20.1 (https://files.minecraftforge.net)."
  exit 1
fi
gradle wrapper --gradle-version 8.1.1
echo "Pronto. Agora rode: ./gradlew build"
