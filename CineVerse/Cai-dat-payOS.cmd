@echo off
setlocal
set "CINEVERSE_PWSH="
if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "CINEVERSE_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined CINEVERSE_PWSH for /f "delims=" %%P in ('where pwsh.exe 2^>nul') do if not defined CINEVERSE_PWSH set "CINEVERSE_PWSH=%%P"
if not defined CINEVERSE_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "CINEVERSE_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined CINEVERSE_PWSH (
  echo Khong tim thay PowerShell 7. Can cai PowerShell 7 de tiep tuc.
  pause
  exit /b 1
)
cd /d "%~dp0" || exit /b 1
"%CINEVERSE_PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-payos.ps1" %*
set "CINEVERSE_EXIT=%ERRORLEVEL%"
if /i "%~1"=="-CheckOnly" exit /b %CINEVERSE_EXIT%
pause
exit /b %CINEVERSE_EXIT%
