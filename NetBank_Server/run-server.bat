
@echo off
cd /d "%~dp0"

if not exist "data" mkdir "data"
if not exist "bin" mkdir "bin"

echo Compiling NetBank Server...

javac -encoding UTF-8 -cp "lib\*" -d "bin" src\Server\*.java

if errorlevel 1 (
    echo [ERROR] Server compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!
echo.

java -cp "bin;lib\*" Server.BankServer

pause
