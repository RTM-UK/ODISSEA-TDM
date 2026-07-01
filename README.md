# TDMEvent

A Paper 1.21.11 Team Deathmatch event plugin.

## Build

```bash
gradle build
```

The jar will be created at `build/libs/TDMEvent-1.0.0.jar`.

## Commands

- `/tdm start` - starts a match using everyone currently online.
- `/tdm stop` - force-stops the active match and restores participants.
- `/tdm setspawn red` - saves the red team spawn at your current location.
- `/tdm setspawn blue` - saves the blue team spawn at your current location.
- `/tdm kitset` - saves your current inventory, armor, and offhand as the event kit.

All commands require `odissea.tdm.admin`.

The kit is saved to `plugins/TDMEvent/kit.yml`. TDM will refuse to start until a kit has been set.

## Inventory safety

Before a match touches any inventory, every participant gets a pending snapshot written to
`plugins/TDMEvent/pending-restores/<uuid>.tdm-snapshot`. If the server stops, reloads, or crashes,
the plugin treats the event as over on the next enable and restores pending players immediately
when they are online, or on their next login.
