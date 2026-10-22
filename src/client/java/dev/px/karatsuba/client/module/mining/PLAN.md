# Autonomous mining plan

Started 2026-10-22. The goal is an Ender Chest Farmer that can run on its own, using Smart Mine's
features (Quiet Mining, Early Break) without any module knowing another exists. Do one step per
pass, check it, tick it here, then stop.

## Decisions (user, 2026-10-22)

- **No module names another module.** Shared work lives in a service or an automation step,
  and modules sit on top of it. There is never an `if (smartMine.enabled)`.
- **Quiet Mining and Early Break move to Client Settings > Mining.** Every digger (Smart Mine, the
  farmer, tasks) follows that one page.
- **There is one `FastBreakBudget` per connection, inside the dig service.** Grim keeps one
  balance per connection, so two copies would spend it twice.
- **Autonomous mode does all four:** collects stray drops, restocks ender chests from shulker
  boxes, stores obsidian in shulker boxes when full, and finds a farm spot itself.
- **A non-friend player in range pauses the farm** until they leave, and hands the controls back.
- **Two front ends, one plan:** Ender Chest Farmer's `Mode: Assist | Autonomous`, and
  `.task echest [count]`. Neither switches the other on.

## Layers

```
Front ends   Smart Mine   Ender Chest Farmer (Assist | Autonomous)   .task echest
Plans        automation/plan/EnderChestFarm (the loop, built from steps)
Steps        automation/step: Dig, PlaceBlock, PickUpDrops, OpenContainer, TakeItems, PutItems
Services     DigService (new), ActionService, InventoryService (+ container clicks)
Settings     Client Settings > Mining (new)
```

## Steps

1. [x] **DigService and Mining settings.** (built 2026-10-22; in-game check by the user pending) Move the dig phasing out of `SmartMineModule` into
   `client/service/DigService`, reached as `Services.digging()`. That covers the look and swing on
   START/FINISHED only, Early Break, `FastBreakBudget`, pause rather than abort, and the
   `Digging.continues` restart guard. Callers ask each tick with an owner and a priority, and get
   back a status: WAITING_LOOK, DIGGING(progress), DONE or REFUSED(reason). Add the
   `settings/mining/MiningSettings` page with Quiet Mining, Early Break, Early Budget and Min
   Progress. Smart Mine and the farmer then call the service. *Done when* both behave exactly as
   before, the settings show in Client Settings, and Smart Mine has no dig code left.
2. [x] **Steps and a basic Autonomous mode.** (built 2026-10-22; probe: farms in a world, first placement slow, user approved) Add the `Dig`, `PlaceBlock` (placement planner plus
   ActionService) and `PickUpDrops` steps, and the `EnderChestFarm` loop: place, dig, collect, repeat,
   with retries and stuck checks. Add Mode Assist | Autonomous to the farmer; Autonomous hosts the
   loop as a Core flow. *Done when* the loop farms a stack with drops scattered by knockback, and
   Assist is unchanged.
3. [ ] **Container clicks.** Add clicks inside an open container to the adapter (`Inventories`) and
   `InventoryService`, under the same rules: one click a tick, only while the reported input is
   still. Then add `OpenContainer`, `TakeItems` and `PutItems`. *Done when* a test task can take
   items from a placed shulker box and put them back with no Grim alerts.
4. [ ] **Logistics.** Restock: place a shulker box, take ender chests, close it, break it with any
   tool (a shulker drops itself) and pick it up. Store: put obsidian into an empty or part-full
   shulker box the same way. *Done when* a farm runs through two shulkers of chests by itself.
5. [ ] **A spot, safety and `.task`.** Find a nearby cell with a floor, room and a clear line,
   walk there, and farm. While a non-friend player is within range, pause with an interrupt and
   resume once they're gone. Add `.task echest [count]`. *Done when* the task starts from chat
   anywhere with room and pauses for the Fake Player.

## Known constraints

- The flow system arbitrates only ROTATION, MOVEMENT, ATTACK and USE; it does not own the hotbar.
  Hotbar contention is settled by `InventoryService` priority, so the loop claims the hotbar below
  combat modules, as Auto Eat does.
- Long-range travel is not available (no Baritone). "Find a spot" is local: within the local
  planner's range.
- Grim: placements and digs follow `Grim.md`. Container clicks follow MultiActionsC/D: no movement
  keys, no jump and no sprint reported.
