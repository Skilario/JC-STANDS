@echo off
setlocal
cd /d "%~dp0"
echo.
echo === 1/4 Compilando el codigo (Maven) ===
if exist target rmdir /s /q target
call mvn package -DskipTests
if errorlevel 1 goto error

echo.
echo === 2/4 Preparando archivos ===
if exist package-input rmdir /s /q package-input
mkdir package-input\lib
copy /y target\jcstands-1.0-SNAPSHOT.jar package-input\ >nul
copy /y target\lib\*.jar package-input\lib\ >nul
if errorlevel 1 goto error

echo.
echo === 3/4 Armando la app con Java incluido (jpackage) ===
if exist dist-exe rmdir /s /q dist-exe
jpackage --type app-image --name JCSTANDS --app-version 1.0 --input package-input --main-jar jcstands-1.0-SNAPSHOT.jar --main-class com.mycompany.jcstands.App --dest dist-exe --java-options "--enable-native-access=ALL-UNNAMED"
if errorlevel 1 goto error

echo.
echo === 4/4 Generando el instalador (Inno Setup) ===
set "ISCC="
for /d %%d in ("%ProgramFiles%\Inno Setup*" "%ProgramFiles(x86)%\Inno Setup*" "%LOCALAPPDATA%\Programs\Inno Setup*") do if exist "%%~d\ISCC.exe" set "ISCC=%%~d\ISCC.exe"
if not defined ISCC for /f "delims=" %%i in ('where ISCC.exe 2^>nul') do set "ISCC=%%i"
if not defined ISCC set "ISCC=NO_ENCONTRADO"
echo Usando: %ISCC%
if not exist "%ISCC%" (
  echo No encontre Inno Setup. Abri JCSTANDS.iss con Inno Setup Compiler y apreta Compile.
  goto error
)
"%ISCC%" JCSTANDS.iss
if errorlevel 1 goto error

echo.
echo ============================================================
echo  LISTO. Instalador nuevo en:
echo  %~dp0..\INSTALADOR\JCSTANDS-Setup.exe
echo ============================================================
pause
exit /b 0

:error
echo.
echo *** Algo fallo. Copiale a Claude lo que aparece arriba. ***
pause
exit /b 1
