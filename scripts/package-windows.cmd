@echo off
setlocal EnableExtensions
rem ---------------------------------------------------------------------------
rem  Builds the self-contained Windows version of the Student Management System:
rem
rem    target\dist\Student Management System\Student Management System.exe
rem    target\StudentManagementSystem-windows-x64.zip   (the release asset)
rem
rem  The app image bundles a trimmed Java runtime made with jlink, so whoever
rem  runs it does not need Java installed.
rem
rem  Requirements: a JDK 17 or newer that ships jpackage and jdeps (e.g. Temurin).
rem  Point JAVA_HOME at it, or have its bin folder on PATH. Run from any folder:
rem
rem    scripts\package-windows.cmd
rem ---------------------------------------------------------------------------
cd /d "%~dp0.."

set "JDK_BIN="
if defined JAVA_HOME set "JDK_BIN=%JAVA_HOME%\bin\"
if exist "%JDK_BIN%jpackage.exe" goto :tools_ok
where /q jpackage
if errorlevel 1 (
    echo jpackage.exe was not found. Set JAVA_HOME to a JDK 17 or newer and run again.
    exit /b 1
)
set "JDK_BIN="
:tools_ok

set "APP_NAME=Student Management System"
set "JAR=target\student-management-system.jar"
set "ICON=target\icon\app.ico"
set "STAGE=target\jpackage-input"
set "DIST=target\dist"
set "ZIP=target\StudentManagementSystem-windows-x64.zip"

set "MVNW=%CD%\mvnw.cmd"

echo === [1/5] Building the jar (tests run in CI; skipped here)
call "%MVNW%" -B clean package -DskipTests
if errorlevel 1 exit /b 1

echo === [2/5] Reading the version from pom.xml
call "%MVNW%" -B -q help:evaluate -Dexpression=project.version -DforceStdout > target\version.txt
if errorlevel 1 exit /b 1
set "APP_VERSION="
set /p APP_VERSION=<target\version.txt
if not defined APP_VERSION (
    echo Could not read the project version from pom.xml.
    exit /b 1
)
set "APP_VERSION=%APP_VERSION:-SNAPSHOT=%"
echo     version %APP_VERSION%

echo === [3/5] Rendering the Windows icon
"%JDK_BIN%java" -cp "%JAR%" com.peterungab.sms.tools.IconTool "%ICON%"
if errorlevel 1 exit /b 1

echo === [4/5] Working out which Java modules the app needs
"%JDK_BIN%jdeps" --print-module-deps --ignore-missing-deps --multi-release 17 "%JAR%" > target\modules.txt
if errorlevel 1 exit /b 1
set "MODULES="
set /p MODULES=<target\modules.txt
rem H2, FlatLaf and the JDBC drivers reach some modules reflectively, so jdeps cannot see them.
set "MODULES=%MODULES%,java.desktop,java.sql,java.naming,java.logging,java.management,jdk.unsupported"
echo     %MODULES%

echo === [5/5] jpackage (app image with its own runtime)
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
copy /y "%JAR%" "%STAGE%\" >nul
if exist "%DIST%\%APP_NAME%" rmdir /s /q "%DIST%\%APP_NAME%"
"%JDK_BIN%jpackage" --type app-image ^
    --name "%APP_NAME%" ^
    --app-version %APP_VERSION% ^
    --vendor "Peter Paul Ungab" ^
    --description "Student records, courses, enrollments and grades" ^
    --copyright "Copyright (c) Peter Paul Ungab" ^
    --icon "%ICON%" ^
    --input "%STAGE%" ^
    --main-jar student-management-system.jar ^
    --main-class com.peterungab.sms.App ^
    --add-modules %MODULES% ^
    --java-options "--enable-native-access=ALL-UNNAMED" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --dest "%DIST%"
if errorlevel 1 exit /b 1

if exist "%ZIP%" del /q "%ZIP%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%DIST%\%APP_NAME%' -DestinationPath '%ZIP%' -CompressionLevel Optimal"
if errorlevel 1 exit /b 1

echo.
echo Done.
echo   Run:      "%DIST%\%APP_NAME%\%APP_NAME%.exe"
echo   Check:    "%DIST%\%APP_NAME%\%APP_NAME%.exe" --smoke-test
echo   Release:  %ZIP%
endlocal
exit /b 0
