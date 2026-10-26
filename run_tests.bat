@echo off
setlocal enabledelayedexpansion
title Run Tests - Accessible Route Adviser
cd /d "%~dp0"

echo =======================================================================
echo   Running JUnit 5 Test Suite (29 tests)
echo   Accessible Route Adviser - RVCE Campus Navigation
echo =======================================================================
echo.

set "MVN_EXEC="
if exist "%~dp0mvnw.cmd" set "MVN_EXEC=%~dp0mvnw.cmd"
if not defined MVN_EXEC (
    where mvn >nul 2>&1
    if !ERRORLEVEL! EQU 0 set "MVN_EXEC=mvn"
)
if not defined MVN_EXEC (
    if exist "C:\Users\User\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" (
        set "MVN_EXEC=C:\Users\User\Downloads\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd"
    )
)

if not defined MVN_EXEC (
    echo [ERROR] Maven was not found.
    pause
    exit /b 1
)

call "%MVN_EXEC%" test
pause
