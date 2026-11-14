# Thin Windows launcher around shared/protocol-consumers/python's nua_client
# package. See clients/windows/README.md.
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PackageDir = Join-Path $ScriptDir "..\..\shared\protocol-consumers\python"

$PythonBin = $env:NUA_CLIENT_PYTHON
if (-not $PythonBin) { $PythonBin = "python" }

& $PythonBin -c "import nua_client" 2>$null
if ($LASTEXITCODE -ne 0) {
    & $PythonBin -m pip install --quiet -e $PackageDir
}

& $PythonBin -m nua_client.cli @args
exit $LASTEXITCODE
