param(
    [int]$IdeaProcessId = 0,
    [ValidateRange(1, 5)][int]$Samples = 3,
    [string]$LogDirectory = ''
)

$ErrorActionPreference = 'Stop'
$ideaProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'idea64.exe'")
if ($IdeaProcessId -eq 0) {
    if ($ideaProcesses.Count -ne 1) {
        throw 'Specify -IdeaProcessId when zero or multiple IDEA processes are found.'
    }
    $IdeaProcessId = [int]$ideaProcesses[0].ProcessId
}
$ideaProcess = $ideaProcesses | Where-Object { $_.ProcessId -eq $IdeaProcessId } | Select-Object -First 1
if ($null -eq $ideaProcess) { throw "IDEA process $IdeaProcessId was not found." }

$ideaDirectory = Split-Path (Split-Path $ideaProcess.ExecutablePath -Parent) -Parent
$jcmdCandidates = @(Join-Path $ideaDirectory 'jbr/bin/jcmd.exe')
if ($env:JAVA_HOME) { $jcmdCandidates += Join-Path $env:JAVA_HOME 'bin/jcmd.exe' }
$jcmdCommand = Get-Command jcmd.exe -ErrorAction SilentlyContinue
if ($null -ne $jcmdCommand) { $jcmdCandidates += $jcmdCommand.Source }
$jcmdPath = $jcmdCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $jcmdPath) { throw 'jcmd.exe was not found. Point JAVA_HOME to an installed JDK.' }

$outputDirectory = Join-Path $PSScriptRoot ('../build/diagnostics/jcef-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$outputDirectory = [System.IO.Path]::GetFullPath($outputDirectory)
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

function Save-JcefProcesses([string]$Name) {
    $processes = @(Get-CimInstance Win32_Process | Where-Object {
        $_.ProcessId -eq $IdeaProcessId -or $_.Name -in @('cef_server.exe', 'jcef_helper.exe')
    })
    $ids = [System.Collections.Generic.HashSet[uint32]]::new()
    [void]$ids.Add([uint32]$IdeaProcessId)
    do {
        $changed = $false
        foreach ($process in $processes) {
            if ($ids.Contains([uint32]$process.ParentProcessId)) {
                if ($ids.Add([uint32]$process.ProcessId)) { $changed = $true }
            }
        }
    } while ($changed)
    $processes | Where-Object { $ids.Contains([uint32]$_.ProcessId) } |
        Select-Object Name, ProcessId, ParentProcessId, ExecutablePath, CommandLine,
            CreationDate, WorkingSetSize, KernelModeTime, UserModeTime |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputDirectory $Name) -Encoding UTF8
}

Save-JcefProcesses 'processes-before.json'
for ($sample = 1; $sample -le $Samples; $sample++) {
    $stdoutPath = Join-Path $outputDirectory "threads-$sample.txt"
    $stderrPath = Join-Path $outputDirectory "threads-$sample.stderr.txt"
    $helper = Start-Process -FilePath $jcmdPath -ArgumentList @("$IdeaProcessId", 'Thread.print', '-l') `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    # Cache the handle before exit so Windows PowerShell can retrieve the exit code.
    $null = $helper.Handle
    if (-not $helper.WaitForExit(15000)) {
        # Only stop the diagnostic helper launched above; never stop IDEA or JCEF.
        Stop-Process -Id $helper.Id -ErrorAction SilentlyContinue
        "jcmd timed out for sample $sample" | Add-Content -LiteralPath (Join-Path $outputDirectory 'capture-errors.txt')
    } elseif ($null -ne $helper.ExitCode -and $helper.ExitCode -ne 0) {
        "jcmd failed for sample $sample (exit $($helper.ExitCode)); inspect its stderr." |
            Add-Content -LiteralPath (Join-Path $outputDirectory 'capture-errors.txt')
    }
    if ($sample -lt $Samples) { Start-Sleep -Seconds 1 }
}
Save-JcefProcesses 'processes-after.json'

if (-not $LogDirectory) {
    $productInfoPath = Join-Path $ideaDirectory 'product-info.json'
    if (Test-Path -LiteralPath $productInfoPath) {
        $productInfo = Get-Content -LiteralPath $productInfoPath -Raw | ConvertFrom-Json
        if ($productInfo.dataDirectoryName) {
            $LogDirectory = Join-Path $env:LOCALAPPDATA "JetBrains/$($productInfo.dataDirectoryName)/log"
        }
    }
}
if ($LogDirectory -and (Test-Path -LiteralPath $LogDirectory -PathType Container)) {
    $logsOutput = Join-Path $outputDirectory 'logs'
    New-Item -ItemType Directory -Path $logsOutput | Out-Null
    $files = @(Get-ChildItem -LiteralPath $LogDirectory -File)
    $selected = @($files | Where-Object { $_.Name -match '^idea(?:\.\d+)?\.log$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 2)
    $selected += @($files | Where-Object { $_.Name -match '^jcef.*\.log$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 6)
    foreach ($file in $selected) {
        Copy-Item -LiteralPath $file.FullName -Destination $logsOutput
    }
}

Write-Output "Diagnostics saved to: $outputDirectory"
Write-Output 'Capture while the browser panels are frozen, before restarting IDEA.'
