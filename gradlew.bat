@echo off
setlocal
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.properties" goto bootstrap
:bootstrap
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle -p "%APP_HOME%" %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Please run the build from GitHub Actions or install Gradle locally.
exit /b 1
