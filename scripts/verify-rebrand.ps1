$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Assert-Condition {
    param(
        [bool] $Condition,
        [string] $Message
    )

    if (-not $Condition) {
        $failures.Add($Message)
    }
}

$pluginYml = Get-Content -Raw (Join-Path $repositoryRoot "src/main/resources/plugin.yml")
$settings = Get-Content -Raw (Join-Path $repositoryRoot "settings.gradle.kts")
$build = Get-Content -Raw (Join-Path $repositoryRoot "build.gradle.kts")

Assert-Condition ($pluginYml -match "(?m)^name:\s+RavenAuction\s*$") "plugin.yml name must be RavenAuction"
Assert-Condition ($pluginYml -match "(?m)^main:\s+fr\.maxlego08\.zauctionhouse\.ZAuctionPlugin\s*$") "plugin.yml must retain the existing Java package"
Assert-Condition ($settings -match 'rootProject\.name\s*=\s*"RavenAuction"') "Gradle root project must be RavenAuction"
Assert-Condition ($build -match 'archiveBaseName\.set\("RavenAuction"\)') "Shadow JAR base name must be RavenAuction"
Assert-Condition ($pluginYml -match "(?m)^\s+- PlaceholderAPI\s*$") "PlaceholderAPI must remain declared"
Assert-Condition ($pluginYml -match "(?m)^\s+- LuckPerms\s*$") "LuckPerms must remain declared"

$removedHooks = @(
    "AxShulkers", "CraftEngine", "CrazyAuctions", "Denizen", "DonutAuction",
    "ExecutableBlocks", "ExecutableItems", "HeadDatabase", "ItemsAdder", "MMOItems",
    "Nexo", "Nova", "Oraxen", "Slimefun", "ZelAuction"
)

foreach ($hook in $removedHooks) {
    Assert-Condition (-not (Test-Path (Join-Path $repositoryRoot "Hooks/$hook"))) "Removed hook directory still exists: $hook"
    Assert-Condition ($pluginYml -notmatch [regex]::Escape($hook)) "plugin.yml still declares removed hook: $hook"
}

$textFiles = Get-ChildItem $repositoryRoot -Recurse -File |
    Where-Object {
        $_.FullName -notmatch '[\\/](\.git|build|target|target-api)[\\/]' -and
        $_.Extension -in @(".java", ".yml", ".yaml", ".md", ".kts", ".properties")
    }

$permissionMatches = $textFiles | Select-String -Pattern '["'']zauctionhouse\.'
Assert-Condition ($permissionMatches.Count -eq 0) "Legacy zauctionhouse.* permission nodes remain"

$placeholderScanFiles = $textFiles | Where-Object {
    $_.Name -ne "LegacyPlaceholderMigrator.java" -and
    $_.Name -ne "TestLegacyPlaceholderMigration.java"
}
$placeholderIdentifiers = $placeholderScanFiles | Select-String -Pattern '%zauctionhouse_|return\s+["'']zauctionhouse["'']'
Assert-Condition ($placeholderIdentifiers.Count -eq 0) "Legacy zauctionhouse PlaceholderAPI identifiers remain"

$migratorSource = Get-Content -Raw (Join-Path $repositoryRoot "src/main/java/fr/maxlego08/zauctionhouse/utils/LegacyPlaceholderMigrator.java")
Assert-Condition ($migratorSource -match '"%zauctionhouse_"') "Legacy placeholder migrator must recognize %zauctionhouse_*%"
Assert-Condition ($migratorSource -match '"%ravenauction_"') "Legacy placeholder migrator must write %ravenauction_*%"

$technicalIdFiles = Get-ChildItem (Join-Path $repositoryRoot "src/main/java"), (Join-Path $repositoryRoot "src/main/resources") -Recurse -File |
    Where-Object { $_.Extension -in @(".java", ".yml", ".yaml") }
$renamedTechnicalIds = $technicalIdFiles | Select-String -Pattern 'RAVENAUCTION_[A-Z_]+' -CaseSensitive
$preservedTechnicalIds = $technicalIdFiles | Select-String -Pattern 'ZAUCTIONHOUSE_[A-Z_]+' -CaseSensitive
Assert-Condition ($renamedTechnicalIds.Count -eq 0) "zMenu technical button IDs must remain ZAUCTIONHOUSE_* for existing inventory compatibility"
Assert-Condition ($preservedTechnicalIds.Count -gt 0) "Preserved ZAUCTIONHOUSE_* zMenu technical button IDs were not found"

$packageDeclarations = Get-ChildItem (Join-Path $repositoryRoot "src"), (Join-Path $repositoryRoot "API") -Recurse -Filter "*.java" |
    Select-String -Pattern '^package fr\.maxlego08\.zauctionhouse'
Assert-Condition ($packageDeclarations.Count -gt 0) "The retained fr.maxlego08.zauctionhouse package namespace was not found"

$updateCheckerFiles = Get-ChildItem (Join-Path $repositoryRoot "src"), (Join-Path $repositoryRoot "API") -Recurse -File |
    Where-Object { $_.Extension -in @(".java", ".yml", ".yaml") }
$updateCheckerReferences = $updateCheckerFiles | Select-String -Pattern 'VersionChecker|enable-version-checker|VERSION_AVAILABLE|version-available' -CaseSensitive
Assert-Condition ($updateCheckerReferences.Count -eq 0) "Update checker code, configuration, or messages remain"

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    exit 1
}

Write-Host "RavenAuction rebrand assertions passed."
