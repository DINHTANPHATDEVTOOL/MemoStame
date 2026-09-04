# MemoStamp Autofix Background Startup Script
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$venvDir = Join-Path $scriptDir "venv"
if (-not (Test-Path $venvDir)) {
    Write-Host "Creating Python virtualenv at $venvDir..."
    python -m venv $venvDir
}

$pythonBin = Join-Path $venvDir "Scripts\python.exe"
$pipBin = Join-Path $venvDir "Scripts\pip.exe"

Write-Host "Installing requirements..."
& $pipBin install -q -r (Join-Path $scriptDir "requirements.txt")

Write-Host "Starting MemoStamp Autofix Orchestrator in watch mode..."
& $pythonBin (Join-Path $scriptDir "orchestrator.py") --watch
