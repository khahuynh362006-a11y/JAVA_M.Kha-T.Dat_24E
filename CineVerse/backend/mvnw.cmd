@echo off
pwsh -NoProfile -File "%~dp0..\scripts\maven.ps1" %*
exit /b %ERRORLEVEL%
