# Remove Update Checker Design

## Goal

Remove RavenAuction's update-checking feature completely so the plugin performs no release/version network checks and exposes no related configuration or messages.

## Changes

- Delete the `VersionChecker` implementation.
- Remove its field, imports, startup call, listener registration, and shutdown cleanup from `ZAuctionPlugin`.
- Remove `enable-version-checker` from every bundled locale configuration.
- Remove the `VERSION_AVAILABLE` message identifier and every bundled localized `version-available` message.
- Preserve normal static version metadata in `plugin.yml` and Gradle.

## Verification

- Search production sources/resources for update-checker symbols and settings.
- Run the rebrand acceptance checks.
- Run a clean Gradle build and inspect the resulting JAR for the deleted class and configuration key.
