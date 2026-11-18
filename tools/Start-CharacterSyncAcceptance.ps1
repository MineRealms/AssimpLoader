param(
    [Parameter(Mandatory=$true)][string]$LaunchSnapshot,
    [Parameter(Mandatory=$true)][string]$OwnerDirectory,
    [Parameter(Mandatory=$true)][string]$ViewerDirectory,
    [Parameter(Mandatory=$true)][string]$CoordinationDirectory
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$snapshot = Import-Clixml -LiteralPath $LaunchSnapshot
$ownerPath = (Resolve-Path -LiteralPath $OwnerDirectory).Path
$viewerPath = (Resolve-Path -LiteralPath $ViewerDirectory).Path
$coordPath = [IO.Path]::GetFullPath($CoordinationDirectory)
if (Test-Path -LiteralPath $coordPath) { throw 'Use a new coordination directory for each acceptance run' }
New-Item -ItemType Directory -Path $coordPath | Out-Null
$outputs = @(
    'assimploader%%build\classes\java\main', 'assimploader%%build\resources\main',
    'assimploader%%build\classes\java\integrationTest', 'assimploader%%build\resources\integrationTest',
    'assimpshowcase%%build\classes\java\showcase', 'assimpshowcase%%build\resources\showcase',
    'rigworks%%rigworks\build\classes\java\main', 'rigworks%%rigworks\build\resources\main',
    'rigworks%%rigworks\build\classes\java\integration'
)
$env:MOD_CLASSES = ($outputs | ForEach-Object {
    $parts = $_ -split '%%',2
    $parts[0]+'%%'+(Join-Path $repo $parts[1])
}) -join ';'
$arguments = $snapshot.Arguments -replace '-Dcharactersynctest.coordination=[^ ]+', ('-Dcharactersynctest.coordination='+$coordPath)
$arguments = '-Dcharactersynctest.timeoutSeconds=600 '+$arguments
$owner = Start-Process -FilePath $snapshot.Executable -ArgumentList $arguments -WorkingDirectory $ownerPath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $ownerPath 'launch.log') -RedirectStandardError (Join-Path $ownerPath 'launch.err.log') -PassThru
Write-Output "Owner PID $($owner.Id)"
$deadline = [DateTime]::UtcNow.AddMinutes(3)
while (-not (Test-Path -LiteralPath (Join-Path $coordPath 'owner-ready.json'))) {
    if ($owner.HasExited) { throw 'Owner exited before its server was ready; inspect launch.log' }
    if (Test-Path -LiteralPath (Join-Path $coordPath 'owner-result.json')) { throw 'Owner acceptance failed before publication' }
    if ([DateTime]::UtcNow -gt $deadline) { throw 'Owner did not publish within three minutes' }
    Start-Sleep -Milliseconds 250
}
$viewerArguments = $arguments.Replace('-Dcharactersynctest.role=owner','-Dcharactersynctest.role=viewer').Replace('--username RigOwner','--username RigViewer')
$viewerArguments = $viewerArguments -replace '-Dworkbenchtest\.[^ ]+\s*',''
$viewer = Start-Process -FilePath $snapshot.Executable -ArgumentList $viewerArguments -WorkingDirectory $viewerPath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $viewerPath 'launch.log') -RedirectStandardError (Join-Path $viewerPath 'launch.err.log') -PassThru
Write-Output "Viewer PID $($viewer.Id)"
[PSCustomObject]@{Owner=$owner.Id;Viewer=$viewer.Id;OwnerDirectory=$ownerPath;ViewerDirectory=$viewerPath} | Export-Clixml -LiteralPath (Join-Path $coordPath 'processes.xml')
