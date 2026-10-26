@echo off
setlocal enabledelayedexpansion
title Accessible Route Adviser - RVCE Campus Navigation
cd /d "%~dp0"

echo =======================================================================
echo   Accessible Route Adviser - RVCE Campus Navigation
echo   Department of CSE-AIML, RV College of Engineering
echo =======================================================================
echo.

:: 1. Check for Java
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java was not found in your system PATH.
    echo Please install JDK 21 or later from:
    echo   https://adoptium.net/temurin/releases/?version=21
    echo.
    pause
    exit /b 1
)

:: 2. Find best Maven executable
set "MVN_EXEC="

:: Check local mvnw.cmd wrapper first (guarantees standalone operation on any PC)
if exist "%~dp0mvnw.cmd" set "MVN_EXEC=%~dp0mvnw.cmd"

:: Check system mvn in PATH if wrapper not present
if not defined MVN_EXEC (
    where mvn >nul 2>&1
    if !ERRORLEVEL! EQU 0 set "MVN_EXEC=mvn"
)

:: Check known user download location as fallback
if not defined MVN_EXEC (
    if exist "C:\Users\User\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" (
        set "MVN_EXEC=C:\Users\User\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd"
    )
)

if not defined MVN_EXEC (
    echo [ERROR] Maven was not found.
    echo Neither 'mvnw.cmd' wrapper nor system 'mvn' was found.
    echo.
    pause
    exit /b 1
)

echo [OK] Launching application...
echo.
call "%MVN_EXEC%" javafx:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo =======================================================================
    echo [NOTE] Application exited with code %ERRORLEVEL%.
    echo If this was unexpected, make sure Java 21+ is installed.
    echo =======================================================================
    pause
)
