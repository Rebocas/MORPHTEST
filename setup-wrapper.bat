@echo off
REM Gera gradlew, gradlew.bat e gradle-wrapper.jar. Requer JDK 17 e Gradle instalado. Rode UMA vez.
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle nao encontrado. Instale o Gradle 8.x ou copie gradlew, gradlew.bat e gradle\wrapper\gradle-wrapper.jar do MDK do Forge 1.20.1.
  exit /b 1
)
gradle wrapper --gradle-version 8.1.1
echo Pronto. Agora rode: gradlew.bat build
