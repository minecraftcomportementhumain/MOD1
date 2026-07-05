@echo off
REM ============================================================
REM  Lanceur serveur + mise a jour auto du mod (fenetre unique).
REM  L'echange du jar est fait ICI, aucun processus externe.
REM  Placez ce fichier a la racine du serveur (a cote de mods\).
REM ============================================================
setlocal EnableExtensions
set "MODJAR=mods\mysubmod-1.0.0.jar"
set "MAJ=mods\.updates"
set "STAGING=%MAJ%\staging\mysubmod-1.0.0.jar"
set "BACKUP=%MAJ%\backup.jar"
set "SWAP=%MAJ%\swap.lock"
set "PENDING=%MAJ%\pending.txt"
set "BOOT=%MAJ%\boot-ok.flag"

:boucle
del "%BOOT%" >nul 2>&1
java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.3.39/win_args.txt %*

if exist "%SWAP%" goto appliquer
if exist "%PENDING%" if not exist "%BOOT%" goto rollback
goto normal

:appliquer
echo.
echo [MAJ] Installation du nouveau build...
if not exist "%STAGING%" ( echo [MAJ] Staging introuvable, annulation. & del "%SWAP%" >nul 2>&1 & goto boucle )
if exist "%MODJAR%" copy /y "%MODJAR%" "%BACKUP%" >nul
set /a sw=0
:swaploop
move /y "%STAGING%" "%MODJAR%" >nul 2>nul
if not errorlevel 1 goto swapok
set /a sw+=1
if %sw% geq 30 ( echo [MAJ] Jar verrouille, annulation. & del "%SWAP%" >nul 2>&1 & goto boucle )
timeout /t 2 /nobreak >nul
goto swaploop
:swapok
del "%SWAP%" >nul 2>&1
echo [MAJ] Nouveau build installe, redemarrage...
goto boucle

:rollback
echo.
echo [MAJ] Le nouveau build n'a pas demarre : restauration de l'ancien...
set /a rb=0
:rbloop
if not exist "%BACKUP%" goto rbfin
move /y "%BACKUP%" "%MODJAR%" >nul 2>nul
if not errorlevel 1 goto rbfin
set /a rb+=1
if %rb% geq 30 goto rbfin
timeout /t 2 /nobreak >nul
goto rbloop
:rbfin
set "BADID="
if exist "%PENDING%" set /p BADID=<"%PENDING%"
if defined BADID echo mauvais> "%MAJ%\mauvais-%BADID%.marker"
del "%PENDING%" >nul 2>&1
echo [MAJ] Ancien build restaure, redemarrage...
goto boucle

:normal
del "%PENDING%" >nul 2>&1
echo.
pause
endlocal
