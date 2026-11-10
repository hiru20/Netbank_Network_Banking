@echo off
cd /d "%~dp0"
if not exist "bin" mkdir "bin"
javac -encoding UTF-8 -d "bin" src\Client\*.java
if errorlevel 1 (
    echo [ERROR] Client compilation failed!
    pause
    exit /b 1
)
java -cp "bin" Client.BankClientGUI

