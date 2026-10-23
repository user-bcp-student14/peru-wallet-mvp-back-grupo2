#Requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$Instalar,
    [switch]$IncluirEditor,
    [switch]$IncluirGitHubCli
)

$ErrorActionPreference = 'Stop'
$MavenVersion = '3.9.16'
$ProgramsRoot = Join-Path $env:LOCALAPPDATA 'Programs'
$MavenRoot = Join-Path $ProgramsRoot 'Apache\Maven'

function Find-Executable {
    param([string]$Name, [string[]]$ExtraDirectories = @())

    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) { return $command.Source }

    $directories = @($ExtraDirectories)
    foreach ($scope in @('User', 'Machine')) {
        $storedPath = [Environment]::GetEnvironmentVariable('Path', $scope)
        if ($storedPath) { $directories += $storedPath.Split(';') }
    }
    foreach ($directory in $directories) {
        if ([string]::IsNullOrWhiteSpace($directory)) { continue }
        $expanded = [Environment]::ExpandEnvironmentVariables($directory.Trim('"'))
        $candidate = Join-Path $expanded $Name
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    return $null
}

function Find-Jdk21 {
    $homes = @($env:JAVA_HOME)
    foreach ($scope in @('User', 'Machine')) {
        $homes += [Environment]::GetEnvironmentVariable('JAVA_HOME', $scope)
    }
    $compiler = Find-Executable 'javac.exe'
    if ($compiler) { $homes += Split-Path (Split-Path $compiler -Parent) -Parent }
    foreach ($root in @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Microsoft'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $ProgramsRoot 'Eclipse Adoptium')
    )) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $homes += Get-ChildItem -LiteralPath $root -Directory |
                Select-Object -ExpandProperty FullName
        }
    }
    foreach ($jdkHome in @($homes | Where-Object { $_ } | Select-Object -Unique)) {
        $java = Join-Path $jdkHome 'bin\java.exe'
        $javac = Join-Path $jdkHome 'bin\javac.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
        if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
        $javaOutput = (& $java --version 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0 -or $javaOutput -notmatch '(?m)^(?:openjdk|java) 21(?:[.\s]|$)') {
            continue
        }
        $compilerOutput = (& $javac --version 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -and $compilerOutput -match '^javac 21(?:[.\s]|$)') {
            return $jdkHome
        }
    }
    return $null
}

function Add-UserPath {
    param([string]$Directory)

    $stored = [Environment]::GetEnvironmentVariable('Path', 'User')
    $entries = @($stored -split ';' | Where-Object { $_ })
    $normalized = $Directory.TrimEnd('\')
    $exists = @($entries | Where-Object {
        [Environment]::ExpandEnvironmentVariables($_.Trim('"')).TrimEnd('\') -ieq $normalized
    }).Count -gt 0
    if (-not $exists) {
        [Environment]::SetEnvironmentVariable('Path', (($entries + $Directory) -join ';'), 'User')
    }
    $currentEntries = @($env:Path -split ';' | Where-Object { $_ })
    if ($currentEntries -notcontains $Directory) {
        $env:Path = "$Directory;$env:Path"
    }
}

function Install-WingetPackage {
    param([string]$PackageId)

    $winget = Find-Executable 'winget.exe'
    if (-not $winget) {
        throw 'Falta WinGet. Instala o actualiza App Installer desde Microsoft Store y abre otra terminal. No se instalaron paquetes por otro mecanismo.'
    }
    & $winget install --id $PackageId --exact --source winget `
        --accept-package-agreements --accept-source-agreements --disable-interactivity
    if ($LASTEXITCODE -ne 0) {
        throw "WinGet no pudo instalar $PackageId. Revisa permisos o politicas con el administrador; el script no se eleva automaticamente."
    }
}

function Install-Maven {
    $destination = Join-Path $MavenRoot "apache-maven-$MavenVersion"
    if (Test-Path -LiteralPath $destination) {
        throw "Existe $destination pero no se pudo usar Maven. Revisa esa carpeta; no se sobrescribira."
    }

    $temporary = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
    $null = New-Item -ItemType Directory -Path $temporary
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $filename = "apache-maven-$MavenVersion-bin.zip"
        $baseUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries"
        $archive = Join-Path $temporary $filename
        $checksum = Join-Path $temporary "$filename.sha512"
        Invoke-WebRequest "$baseUrl/$filename" -OutFile $archive -UseBasicParsing
        Invoke-WebRequest "$baseUrl/$filename.sha512" -OutFile $checksum -UseBasicParsing
        $expected = [regex]::Match((Get-Content -LiteralPath $checksum -Raw), '(?i)\b[0-9a-f]{128}\b').Value
        $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
        if (-not $expected -or $actual -ine $expected) {
            throw 'El SHA-512 de Maven no coincide. Se cancelo la extraccion.'
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $temporary
        $null = New-Item -ItemType Directory -Path $MavenRoot -Force
        Move-Item -LiteralPath (Join-Path $temporary "apache-maven-$MavenVersion") -Destination $destination
    }
    finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Recurse -Force
        }
    }
    return Join-Path $destination 'bin\mvn.cmd'
}

Write-Host 'Backend wallet: Java 21, Maven 3.9+ y Git. Persistencia JSON mock.'
if (-not $Instalar) {
    Write-Host 'MODO VERIFICACION: sin descargas, instalaciones ni cambios de configuracion.'
}

$jdk = Find-Jdk21
if (-not $jdk -and $Instalar) {
    Install-WingetPackage 'EclipseAdoptium.Temurin.21.JDK'
    $jdk = Find-Jdk21
    if (-not $jdk) { throw 'No se encontro el JDK 21 despues de la instalacion. Abre una terminal nueva y verifica.' }
}
if ($jdk -and $Instalar) {
    $env:JAVA_HOME = $jdk
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
    Add-UserPath (Join-Path $jdk 'bin')
}

$mavenDirectories = @()
foreach ($configuredMavenHome in @($env:MAVEN_HOME, $env:M2_HOME)) {
    if ($configuredMavenHome) { $mavenDirectories += Join-Path $configuredMavenHome 'bin' }
}
if (Test-Path -LiteralPath $MavenRoot -PathType Container) {
    $mavenDirectories += Get-ChildItem -LiteralPath $MavenRoot -Directory |
        ForEach-Object { Join-Path $_.FullName 'bin' }
}
$maven = Find-Executable 'mvn.cmd' $mavenDirectories
if (-not $maven -and $Instalar) { $maven = Install-Maven }
if ($maven -and $Instalar) {
    $mavenHome = Split-Path (Split-Path $maven -Parent) -Parent
    $env:MAVEN_HOME = $mavenHome
    [Environment]::SetEnvironmentVariable('MAVEN_HOME', $mavenHome, 'User')
    Add-UserPath (Join-Path $mavenHome 'bin')
}

$gitDirectories = @((Join-Path $env:ProgramFiles 'Git\cmd'), (Join-Path $ProgramsRoot 'Git\cmd'))
$git = Find-Executable 'git.exe' $gitDirectories
if (-not $git -and $Instalar) {
    Install-WingetPackage 'Git.Git'
    $git = Find-Executable 'git.exe' $gitDirectories
    if (-not $git) { throw 'Git no se encontro despues de instalarlo. Abre una terminal nueva.' }
}
if ($git -and $Instalar) { Add-UserPath (Split-Path $git -Parent) }

$tools = @(
    [pscustomobject]@{ Herramienta = 'JDK 21 (java + javac)'; Ruta = $jdk },
    [pscustomobject]@{ Herramienta = 'Maven'; Ruta = $maven },
    [pscustomobject]@{ Herramienta = 'Git'; Ruta = $git }
)
if ($IncluirEditor) {
    $editorDirectories = @((Join-Path $ProgramsRoot 'Microsoft VS Code\bin'), (Join-Path $env:ProgramFiles 'Microsoft VS Code\bin'))
    $editor = Find-Executable 'code.cmd' $editorDirectories
    if (-not $editor -and $Instalar) {
        Install-WingetPackage 'Microsoft.VisualStudioCode'
        $editor = Find-Executable 'code.cmd' $editorDirectories
    }
    $tools += [pscustomobject]@{ Herramienta = 'VS Code (opcional)'; Ruta = $editor }
}
if ($IncluirGitHubCli) {
    $ghDirectories = @((Join-Path $env:ProgramFiles 'GitHub CLI'), (Join-Path $ProgramsRoot 'GitHub CLI'))
    $githubCli = Find-Executable 'gh.exe' $ghDirectories
    if (-not $githubCli -and $Instalar) {
        Install-WingetPackage 'GitHub.cli'
        $githubCli = Find-Executable 'gh.exe' $ghDirectories
    }
    $tools += [pscustomobject]@{ Herramienta = 'GitHub CLI (opcional)'; Ruta = $githubCli }
}

$tools | Select-Object Herramienta, @{Name = 'Estado'; Expression = {
    if ($_.Ruta) { "DETECTADO: $($_.Ruta)" } else { 'FALTA' }
}} | Format-Table -AutoSize

if ($maven -and $jdk) {
    $previousJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $jdk
        $mavenOutput = (& $maven --version 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0 -or $mavenOutput -notmatch 'Apache Maven (3\.\d+\.\d+)') {
            throw 'El Maven detectado no es un Maven 3 operativo. No se reemplazo ni actualizo.'
        }
        if ([version]$Matches[1] -lt [version]'3.9.0') {
            throw 'El Maven existente es anterior a 3.9. No se actualizo automaticamente; acuerda su actualizacion con el equipo.'
        }
        Write-Host $mavenOutput
    }
    finally { $env:JAVA_HOME = $previousJavaHome }
}

if (@($tools | Where-Object { -not $_.Ruta }).Count -gt 0) {
    if ($Instalar) { throw 'Quedan requisitos pendientes. Revisa la tabla antes de continuar.' }
    Write-Warning 'Faltan herramientas. Para instalarlas, ejecuta de nuevo con -Instalar.'
}
else {
    Write-Host 'Herramientas detectadas. Si acabas de instalar o configurar, abre una terminal nueva y valida java, javac, mvn y git.'
}
Write-Host 'Este script no crea el backend, no inicializa Git ni modifica datos JSON.'