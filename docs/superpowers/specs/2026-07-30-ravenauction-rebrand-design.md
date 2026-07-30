# RavenAuction Rebrand and Hook Reduction Design

## Goal

Rebrand the Minecraft plugin from zAuctionHouse to RavenAuction everywhere exposed to server owners and players, while preserving the existing Java package namespace and public API identifiers for source and binary compatibility. Remove all optional integrations except LuckPerms and PlaceholderAPI.

## Compatibility Boundary

The following identifiers remain unchanged:

- Java packages under `fr.maxlego08.zauctionhouse`
- Public API types and method signatures in the API module
- Internal persisted data formats where renaming would prevent existing auction data from loading

The following identifiers are intentionally changed without legacy aliases:

- Bukkit plugin name and built artifact name
- Permission nodes, from `zauctionhouse.*` to `ravenauction.*`
- PlaceholderAPI expansion identifier, from `zauctionhouse` to `ravenauction`
- Configuration-facing names, keys, pattern identifiers, prefixes, comments, and generated resource text
- User-facing messages, log labels, documentation, and branding
- Safe internal implementation names that are not part of the public API

Existing permission nodes and PlaceholderAPI placeholders will not remain as aliases.

## Hook Scope

LuckPerms and PlaceholderAPI are the only integrations retained.

Every other hook is removed from:

- Gradle project/module inclusion and dependencies
- Hook source modules
- Bukkit dependency declarations
- Runtime detection, registration, and initialization
- Hook-specific configuration, rules, services, and commands
- Migration providers, including auction-plugin import integrations
- Documentation and resource comments

Core auction behavior, storage, economies, commands, Redis synchronization, and public API functionality remain unless a component exists solely to serve a removed hook.

## Implementation Strategy

First establish an inventory of every branding occurrence and integration entry point. Update build metadata and Bukkit metadata, then rename server-facing identifiers throughout code and bundled resources. Remove hook modules and their registration paths, simplifying abstractions that no longer have multiple implementations where appropriate.

Package declarations and imports stay under `fr.maxlego08.zauctionhouse`. Public API identifiers are reviewed separately from implementation identifiers so the rebrand does not accidentally break API consumers.

Persisted identifiers are handled conservatively: database tables, serialized enum values, and other stored values remain unchanged when renaming them would make existing data unreadable. Configuration-facing identifiers are renamed as requested.

## Verification

Verification includes:

- A clean Gradle build and test run
- Inspection of the produced plugin metadata and JAR name
- Repository searches confirming that removed hooks are absent
- Repository searches classifying any remaining `zauctionhouse` occurrence as an allowed package/API or persistence identifier
- Searches confirming permission nodes use `ravenauction.*`
- Searches confirming the PlaceholderAPI namespace is `ravenauction`
- Confirmation that LuckPerms and PlaceholderAPI still compile and register

## Success Criteria

The built plugin identifies itself as RavenAuction and uses RavenAuction-branded permissions, placeholders, configuration, resources, logs, and documentation. No hook other than LuckPerms or PlaceholderAPI is compiled, declared, configured, or initialized. Existing Java package/API consumers and persisted auction data remain usable.
