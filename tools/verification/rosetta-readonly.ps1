param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [int]$Port = 48790,
    [string]$ProbeFile,
    [string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$tokenFile = Join-Path $GameDirectory 'RosettaRemoteDebugBridge/remote-token.txt'
$token = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $tokenFile)).Trim()
$request = @{ token = $token; cmd = 'ping'; args = @{} }
if ($ProbeFile) {
    $request.cmd = 'exec'
    $request.args = @{ code = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $ProbeFile)) }
}
$client = [Net.Sockets.TcpClient]::new()
try {
    if (-not $client.ConnectAsync('127.0.0.1', $Port).Wait(3000)) { throw 'Bridge connection timed out' }
    $stream = $client.GetStream()
    $stream.ReadTimeout = 65000
    $stream.WriteTimeout = 5000
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false))
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    $writer.AutoFlush = $true
    $writer.WriteLine(($request | ConvertTo-Json -Compress -Depth 5))
    $response = $reader.ReadLine()
    if (-not $response) { throw 'Bridge closed without a response' }
    $safe = $response.Replace($token, '<redacted>')
    if ($OutputFile) { [IO.File]::WriteAllText($OutputFile, $safe + [Environment]::NewLine) }
    $safe
    $result = $response | ConvertFrom-Json
    if ($result.ok -eq $false -or $result.success -eq $false -or $result.error) { exit 1 }
} finally {
    $client.Dispose()
}
