# Project EVOLVE Server v0.7.1

Hunter combat foundation for Paper 1.21.1 / Java 21.

## Implemented
- ASSAULT: Assault Rifle + Shotgun
- TRACKER: Tracker Carbine + Harpoon Wire
- MEDIC: Medic Rifle (special healing equipment comes next)
- SUPPORT: Support Rifle (shield equipment comes next)
- Server-authoritative raycast gunfire
- Magazines, reload timing, fire rate, range and damage in config.yml
- F (swap-hand key) reloads the held Project EVOLVE weapon
- Hit feedback through ActionBar
- Tracker Harpoon: raycast connection, visible electric wire, distance pull, duration and break distance
- Monster Armor/HP damage uses the existing two-layer damage system

This is the common weapon framework. Dedicated Heal Gun, Support Shield, Tracker deployable traps and richer client recoil/ADS are intended for following revisions.
