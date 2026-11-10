@echo off
rem configuration and tournaments live in the user's own data folder
set "PAIRGOTH_HOME=%LOCALAPPDATA%\Pairgoth"
if not exist "%PAIRGOTH_HOME%" mkdir "%PAIRGOTH_HOME%"
cd /d "%PAIRGOTH_HOME%"
"%~dp0jre\bin\java.exe" -jar "%~dp0lib\pairgoth-engine.jar"
