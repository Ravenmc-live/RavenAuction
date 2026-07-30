# RavenAuction Rebrand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a RavenAuction-branded plugin that retains the existing Java/API namespace and only integrates with LuckPerms and PlaceholderAPI.

**Architecture:** Keep `fr.maxlego08.zauctionhouse` and API contracts stable while changing all server-facing identifiers. Remove optional hook subprojects and registration code, retaining the vanilla item-content provider, built-in V3 migration, LuckPerms permission provider, and PlaceholderAPI expansion.

**Tech Stack:** Java 21, Paper API, Gradle Kotlin DSL, ShadowJar, PlaceholderAPI, LuckPerms

## Global Constraints

- Preserve packages under `fr.maxlego08.zauctionhouse` and public API signatures.
- Rename permissions to `ravenauction.*` and placeholders to `%ravenauction_*%` without legacy aliases.
- Rename configuration-facing and user-visible zAuctionHouse branding.
- Preserve persisted identifiers when changing them would make existing auction data unreadable.
- Remove all integrations except LuckPerms and PlaceholderAPI.

---

### Task 1: Add rebrand acceptance assertions

**Files:**
- Create: `scripts/verify-rebrand.ps1`

**Interfaces:**
- Consumes: repository sources and resources
- Produces: a process exit code that rejects stale server-facing branding and removed hook declarations

- [ ] Write a PowerShell verification script that asserts `plugin.yml` uses `RavenAuction`, permissions and placeholder identifiers use `ravenauction`, only LuckPerms and PlaceholderAPI remain as dependencies, non-retained hook projects are absent, and Java package declarations remain unchanged.
- [ ] Run `powershell -ExecutionPolicy Bypass -File scripts/verify-rebrand.ps1` and verify it fails against the current zAuctionHouse tree.
- [ ] Keep the failing assertions as the acceptance harness for subsequent tasks.

### Task 2: Rebrand build and Bukkit metadata

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/plugin.yml`
- Modify: `gradle.properties`

**Interfaces:**
- Consumes: Gradle project/module layout
- Produces: `RavenAuction-<version>.jar` with Bukkit plugin name `RavenAuction`

- [ ] Change the Gradle root name and archive base name to RavenAuction while retaining Maven group/package coordinates.
- [ ] Reduce included hook projects to `Hooks:LuckPerms`.
- [ ] Change Bukkit metadata and dependencies so PlaceholderAPI is required and LuckPerms is the only optional integration.
- [ ] Run the acceptance script and confirm the metadata assertions pass while later resource assertions still fail.

### Task 3: Remove non-retained hooks and runtime registration

**Files:**
- Delete: all `Hooks/*` projects except `Hooks/LuckPerms`
- Modify: `src/main/java/fr/maxlego08/zauctionhouse/ZAuctionPlugin.java`
- Modify: `src/main/java/fr/maxlego08/zauctionhouse/rule/ZRuleLoaderRegistry.java`
- Modify: `API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/Plugins.java`

**Interfaces:**
- Consumes: retained LuckPerms provider and core vanilla rule/content providers
- Produces: runtime initialization with no third-party integration checks other than LuckPerms/PlaceholderAPI

- [ ] Remove all optional item-content, custom-item rule, and external migration registration paths.
- [ ] Retain vanilla shulker content, built-in V3 migration, and LuckPerms offline permission registration.
- [ ] Remove obsolete plugin enum entries and imports without changing public API interfaces.
- [ ] Run `./gradlew.bat compileJava` and resolve only errors caused by removed integrations.

### Task 4: Rename permissions and PlaceholderAPI namespace

**Files:**
- Modify: `API/src/main/java/fr/maxlego08/zauctionhouse/api/utils/Permission.java`
- Modify: `src/main/java/fr/maxlego08/zauctionhouse/permissions/PermissionRegistrar.java`
- Modify: `src/main/java/fr/maxlego08/zauctionhouse/placeholder/DistantPlaceholder.java`
- Modify: placeholder registration and documentation references under `src`, `API`, and resources

**Interfaces:**
- Produces: permission nodes beginning `ravenauction.` and PlaceholderAPI identifier `ravenauction`

- [ ] Replace permission string values and dynamically generated permission prefixes.
- [ ] Change the PlaceholderExpansion identifier and server-facing examples.
- [ ] Run focused searches proving no server-facing `zauctionhouse.*` permission or placeholder remains.

### Task 5: Rebrand bundled configuration and resources

**Files:**
- Modify: YAML and documentation files under `src/main/resources`
- Modify: `readme.md`
- Modify: `changelogs.md`
- Modify: relevant source log/message constants

**Interfaces:**
- Consumes: existing resource keys and inventory loader registrations
- Produces: RavenAuction-branded default resources whose renamed identifiers match code registrations

- [ ] Rename comments, prefixes, pattern names, inventory button identifiers, URLs, and configuration-facing zAuctionHouse names to RavenAuction variants across every locale.
- [ ] Update matching loader/registration string constants in Java so renamed resources remain loadable.
- [ ] Leave package names and persistence-sensitive serialized/database identifiers unchanged and document each remaining non-package occurrence.
- [ ] Run the acceptance script until all branding assertions pass.

### Task 6: Verify the distributable

**Files:**
- Modify: `scripts/verify-rebrand.ps1` only if a discovered invariant needs a more precise assertion

**Interfaces:**
- Produces: verified RavenAuction shaded JAR

- [ ] Run `./gradlew.bat clean build`.
- [ ] Run `powershell -ExecutionPolicy Bypass -File scripts/verify-rebrand.ps1`.
- [ ] Inspect the built JAR name and embedded `plugin.yml`.
- [ ] Search for removed integration names and classify any remaining zAuctionHouse occurrence against the compatibility boundary.
- [ ] Review `git diff --check` and `git status --short`.
