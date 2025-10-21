@echo off
echo ========================================
echo   Mise a jour automatique du mod
echo ========================================
echo.

REM Configuration
set GITHUB_USER=minecraftcomportementhumain
set GITHUB_REPO=MOD1
set MOD_NAME=mysubmod-1.0-SNAPSHOT.jar
set PRISM_INSTANCE_NAME=ÉtudeComportementHumain

REM Chemin automatique vers Prism Launcher
set PRISM_DIR=%APPDATA%\PrismLauncher\instances\%PRISM_INSTANCE_NAME%\.minecraft\mods
set DOWNLOAD_URL=https://github.com/%GITHUB_USER%/%GITHUB_REPO%/releases/download/latest/%MOD_NAME%

echo Configuration:
echo - GitHub: %GITHUB_USER%/%GITHUB_REPO%
echo - Instance: %PRISM_INSTANCE_NAME%
echo - Destination: %PRISM_DIR%
echo.

REM Vérifier si le dossier existe
if not exist "%PRISM_DIR%" (
    echo ERREUR: Le dossier de mods n'existe pas!
    echo Verifiez le nom de votre instance Prism Launcher.
    echo Chemin: %PRISM_DIR%
    pause
    exit /b 1
)

echo Telechargement du mod depuis GitHub...
curl -L -o "%PRISM_DIR%\%MOD_NAME%" "%DOWNLOAD_URL%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   Mod mis a jour avec succes!
    echo ========================================
    echo.
    echo Le mod a ete telecharge dans:
    echo %PRISM_DIR%\%MOD_NAME%
    echo.
    echo Vous pouvez maintenant lancer Minecraft.
) else (
    echo.
    echo ERREUR: Le telechargement a echoue.
    echo Verifiez votre connexion internet et la configuration GitHub.
)

echo.
pause
