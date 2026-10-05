# Karatsuba TODO

Legend: `[x]` done · `[ ]` planned · **P1** essential · **P2** important · **P3** nice to have

---

## Combat

- [x] [KillAura](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/combat/KillAuraModule.java)
- [x] [Velocity](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/combat/VelocityModule.java)
- [ ] **P1** AutoCrystal – place/break, min/max damage, self-damage cap, anti-suicide, ID predict, render
- [ ] **P1** AutoTotem / Offhand – totem on health threshold, gapple/crystal switching
- [ ] **P1** Surround – obsidian around feet, center player, extension/anti-cev
- [ ] **P1** Criticals – packet / jump / mini-jump modes
- [ ] **P1** AutoArmor – best armor, elytra swap support
- [ ] **P2** HoleFill – fill holes near enemies (smart mode)
- [ ] **P2** AutoTrap – trap targets in obsidian
- [ ] **P2** SelfTrap – cover your head when targeted
- [ ] **P2** AutoWeb – webs on target's feet
- [ ] **P2** BowAim – aim prediction with gravity/velocity
- [ ] **P2** AnchorAura – respawn anchor placing for Nether / 1.16+ servers
- [ ] **P3** BedAura – bed placing in Nether/End
- [ ] **P3** TriggerBot – attack when crosshair is on entity
- [ ] **P3** AimAssist – smooth aim toward target
- [ ] **P3** AntiBot – filter fake/NPC players from targeting

---

## Movement

- [x] [Speed](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/movement/SpeedModule.java)
- [x] [Sprint](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/movement/SprintModule.java)
- [ ] **P1** Step – vanilla / NCP modes, configurable height
- [ ] **P1** NoSlow – items, webs, soul sand, inventory movement
- [ ] **P1** NoFall – packet / bucket modes
- [ ] **P2** ReverseStep – snap down into holes
- [ ] **P2** HoleAnchor – stop horizontal motion above holes
- [ ] **P2** ElytraFly – boost / control / packet modes
- [ ] **P2** Flight – vanilla / glide
- [ ] **P3** Jesus – walk on liquids
- [ ] **P3** Scaffold – tower, safewalk, rotations via RotationManager
- [ ] **P3** SafeWalk – don't walk off edges
- [ ] **P3** AutoWalk

---

## Render

- [x] [BlockOverlay](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/BlockOverlayModule.java)
- [x] [ESP](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/EspModule.java)
- [x] [FreeCam](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/FreeCamModule.java)
- [x] [Fullbright](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/FullbrightModule.java)
- [x] [HoleESP](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/HoleEspModule.java)
- [x] [NameTags](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/NameTagsModule.java)
- [x] [RenderAdjustments](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/render/RenderAdjustmentsModule.java)
- [ ] **P2** StorageESP – chests, shulkers, ender chests, barrels
- [ ] **P2** Trajectories – pearls, arrows, potions
- [ ] **P2** Chams – through-wall player/crystal rendering
- [ ] **P2** PopChams – ghost model on totem pop
- [ ] **P2** LogoutSpots – mark where players logged out
- [ ] **P3** Breadcrumbs – your movement trail
- [ ] **P3** Search – highlight specific blocks
- [ ] **P3** ViewModel – hand position/scale tweaks
- [ ] **P3** CameraClip – third-person camera through blocks

---

## Player

- [ ] **P1** AutoEat – hunger/health threshold, gapple priority, pause combat while eating
- [ ] **P1** FastPlace / FastUse – exp bottles, crystals, blocks
- [ ] **P1** PacketMine / SpeedMine – instant rebreak, double mine, auto-switch
- [ ] **P1** Replenish – refill hotbar stacks from inventory
- [ ] **P2** AutoRespawn
- [ ] **P2** NoRotate – ignore server rotation packets
- [ ] **P2** NoEntityTrace – mine through entities
- [ ] **P2** Reach
- [ ] **P3** InventoryManager / Sorter
- [ ] **P3** ChestStealer
- [ ] **P3** Blink – hold movement packets

---

## Utility

- [x] [AntiAFK](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/utility/AntiAfkModule.java)
- [x] [AntiSound](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/utility/AntiSoundModule.java)
- [x] [AutoTool](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/utility/AutoToolModule.java)
- [x] [MiddleClick](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/utility/MiddleClickModule.java)
- [x] [XCarry](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/utility/XCarryModule.java)
- [ ] **P1** AutoReconnect – delay + countdown on disconnect screen
- [ ] **P1** PopCounter – chat notifications for totem pops / deaths
- [ ] **P1** AutoLog – disconnect on low health / totem count / player in range
- [ ] **P2** VisualRange – notify when players enter/leave render distance
- [ ] **P2** AutoMend – throw XP while armor is damaged
- [ ] **P2** ChatTweaks – timestamps, infinite history, anti-spam
- [ ] **P2** AutoFish
- [ ] **P3** AutoSign – preset sign text
- [ ] **P3** PacketLogger – debug tool for development

---

## Exploit

- [x] [AntiHunger](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/exploit/AntiHungerModule.java)
- [ ] **P1** PacketFly – phase/factor modes
- [ ] **P2** Timer – tick speed multiplier
- [ ] **P2** PingSpoof
- [ ] **P2** PortalGodMode – cancel teleport confirm in portals
- [ ] **P3** NewChunks – detect freshly generated chunks
- [ ] **P3** Phase
- [ ] **P3** EntityControl / BoatFly

---

## Misc

- [x] [FakePlayer](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/misc/FakePlayerModule.java)
- [x] [Recorder](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/misc/RecorderModule.java)
- [x] [UpdateCheck](https://github.com/biggiemedium/Karatsuba/blob/main/src/client/java/dev/px/karatsuba/client/module/misc/UpdateCheckModule.java)
- [ ] **P3** AutoGG / KillSay

---
