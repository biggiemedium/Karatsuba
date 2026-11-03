# Dev notes:

## UI:
- Move UI default to apple/Tahoe look
- Stop HUD Elements rendering in game GUI overlay (Currently covering inventory and options GUI)
- Turn all HUD elements off by default
- Fix flickering of HUD elements while in GUI

## Modules

### render
- Hole ESP: Fix settings to make easier -> Lower opacity by default, make setting for max hole rendering, Add hole glow (Look at leapfrog code), make mode (box/outline, filled, both, glow)
- Projectiles: Clean up rendering and fix prediction while falling
- Shaders: Fix "lag" why moving mouse around. players hand is rendering and shader is falling behind it.
- Combat Info: fix weird smoothing look, make UI less cluttered and easier glancability/readability

### mining
- AutoTool: Stop fighting with Smart Mine

### Movement
- NoSlow: Allow for sprinting and eating in vanilla, Turn off sprint by default in grim mode
- ElytraFly: Vanilla Control mode (see leapfrogs code)
- Step: Harden module and make steping faster
- Reverse step: TODO
- Boat Fly: TODO -> Implement vanilla only mode

### Combat
- Auto Crystal: Turn off Hurt Sync and Sync Margin by default
- Auto Totem: fix fighting and more control over what is defaulted to offhand. 
