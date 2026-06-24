$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$DefaultPython = Join-Path $Root ".venv_models\Scripts\python.exe"
$ModelPython = if ($env:MODEL_PYTHON) { $env:MODEL_PYTHON } else { $DefaultPython }
$LamaPython = if ($env:LAMA_PYTHON) { $env:LAMA_PYTHON } else { $ModelPython }
$SmolVlmPython = if ($env:SMOLVLM_PYTHON) { $env:SMOLVLM_PYTHON } else { $ModelPython }

foreach ($python in @($ModelPython, $LamaPython, $SmolVlmPython) | Select-Object -Unique) {
    if (-not (Test-Path $python)) {
        throw "Python interpreter not found: $python. Create .venv_models or set MODEL_PYTHON/LAMA_PYTHON/SMOLVLM_PYTHON."
    }
}

function Test-PortListening {
    param([int]$Port)
    $match = netstat -ano | Select-String -Pattern ":\s*$Port\s"
    if ($match) { return $true }
    return [bool](netstat -ano | Select-String -Pattern ":$Port\s+")
}

function Quote-CmdArg {
    param([string]$Value)
    return '"' + ($Value -replace '"', '\"') + '"'
}

function Start-ModelProcess {
    param(
        [string]$Name,
        [int]$Port,
        [string]$Exe,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$LogName
    )

    if (Test-PortListening -Port $Port) {
        Write-Host "$Name already running on port $Port" -ForegroundColor Yellow
        return
    }

    $outLog = Join-Path $Root "$LogName.out.log"
    $errLog = Join-Path $Root "$LogName.err.log"
    Write-Host "Starting $Name on port $Port..." -ForegroundColor Cyan

    $quotedExe = Quote-CmdArg $Exe
    $quotedArgs = ($Arguments | ForEach-Object { Quote-CmdArg $_ }) -join " "
    $quotedWorkDir = Quote-CmdArg $WorkingDirectory
    $quotedOutLog = Quote-CmdArg $outLog
    $quotedErrLog = Quote-CmdArg $errLog
    $commandLine = "cd /d $quotedWorkDir && $quotedExe $quotedArgs > $quotedOutLog 2> $quotedErrLog"

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c $commandLine"
    $psi.WorkingDirectory = $Root
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    [void]$process.Start()
}

Start-ModelProcess `
    -Name "GFPGAN face restore" `
    -Port 5001 `
    -Exe $ModelPython `
    -Arguments @("app.py") `
    -WorkingDirectory (Join-Path $Root "GFPGAN") `
    -LogName "gfpgan_service"

Start-ModelProcess `
    -Name "LaMa inpaint" `
    -Port 5002 `
    -Exe $LamaPython `
    -Arguments @("inpaint_service.py") `
    -WorkingDirectory $Root `
    -LogName "inpaint_lama"

Start-ModelProcess `
    -Name "Real-ESRGAN super resolution" `
    -Port 8000 `
    -Exe $ModelPython `
    -Arguments @("-m", "uvicorn", "realesrgan_http_api:app", "--host", "0.0.0.0", "--port", "8000") `
    -WorkingDirectory (Join-Path $Root "Real-ESRGAN") `
    -LogName "realesrgan_service"

# SmolVLM is used for album classification, not restoration. It is optional.
if (Test-Path (Join-Path $Root "vlm_server.py")) {
    Start-ModelProcess `
        -Name "SmolVLM album classifier" `
        -Port 5000 `
        -Exe $SmolVlmPython `
        -Arguments @("vlm_server.py") `
        -WorkingDirectory $Root `
        -LogName "smolvlm_service"
}

Write-Host "Model service startup commands completed." -ForegroundColor Green
Write-Host "Check ports with: netstat -ano | findstr `"5000 5001 5002 8000`""
