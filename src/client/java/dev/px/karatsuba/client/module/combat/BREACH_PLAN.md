# Breach plan

Started 2026-10-22. Breach is our name for "city": mine the block that stands between an
enemy and a crystal, picked by the real damage that crystal would do once the block is gone.
The reference was spidermod's AutoCity/CityESP: four feet-ring obsidian, "could a crystal go
past it", mine the nearest. Do one step per pass, check it, tick it here, then stop.

## Decisions (user, 2026-10-22)

- **Name:** Breach. That means the Auto Breach module, `BreachFinder`, the `Breach` plan and
  `.task breach`.
- **The candidates are drawn by the module** (coloured by score, the chosen one growing as it is
  mined), with a Show Only mode that draws without mining.
- **The crystal is optional in the plan:** a setting, off by default. Off, Crystal Aura's own
  search finds the opened spot.
- **First version scenarios:** surrounded or in a hole, burrowed, trapped, and the re-surround race.

## Layers (no module names another)

```
client/combat/breach/BreachFinder   analysis: scenario, candidates, scores (CombatKit only)
automation/plan/Breach              the flow: into reach -> Dig (DigService) -> optional crystal
front ends                          Auto Breach (Combat), .task breach; AutoCombat later, as a reflex
```

## Choosing a candidate (user, 2026-10-22: geometry and mine time; CrystalRules untouched)

Since 1.13 a crystal fits a one-block hole: one air above an obsidian or bedrock base, and
no entity in the two cells above it. So a wall block is a candidate when the block under it is
obsidian or bedrock and nothing stands in it or above it: break it, and a crystal goes straight
into the hole beside their feet. Candidates are ranked by time to mine with the best tool in
the hotbar (`Digging.progressPerTick(block, slot)`), then by distance from you, with those in
reach first. Unbreakable blocks and your own surround are never candidates. There are no
damage sums and no hypothetical crystal rules.

## Steps

1. [x] **BreachFinder.** (built 2026-10-22; probe vs Fake Player: surrounded, trapped, open, sealed, burrowed all correct) Build `client/combat/breach/` with `Scenario` (OPEN, SURROUNDED, BURROWED,
   SEALED), `Breach` (the block, the crystal base under the hole, FEET or FACE, mine ticks, in reach)
   and `Assessment` (scenario, trapped, candidates best first, from `BreachFinder.assess(kit, target)`).
   The rules: burrowed gives the burrow block; surrounded gives feet-ring blocks on an
   obsidian or bedrock floor; trapped (the head ring and roof up) also offers head-ring blocks
   standing on such a ring block, to open a face place; sealed is when no feet-ring block will do
   (bedrock, or a floor of the wrong kind), leaving only face blocks; open, a hole a crystal fits
   already, needs no breach. *Done when* the assessments read sensibly in a probe against the Fake Player: in a hole, in
   a burrow, and under a trap.
2. [ ] **Auto Breach, Show Only.** The module in Combat: target selection (range, priority, no
   friends) and the rendering: every candidate coloured by score, the best outlined, and the
   scenario in the ArrayList. *Done when* it draws the right blocks around the Fake Player in each
   scenario.
3. [ ] **The Breach plan and Auto mode.** `automation/plan/Breach`: within reach (walking if the
   caller allows), `Dig` through DigService, re-ranked each round. If they re-surround, it goes
   again until it is open, they leave, or it times out. With Place Crystal on, a crystal goes onto
   the opened spot through `Services.actions()` the tick the block is gone. *Done when* Auto mode
   opens the Fake Player's surround and Crystal Aura (or the plan) hits it.
4. [ ] **`.task breach [player]`,** plus notes in the plan for AutoCombat to start it as a reflex.
   *Done when* the task runs from chat.

## Follow-ups (not in the first version)

Double holes (two- and four-cell), anvils and ender chests as walls (slower, kept), the enemy's
own Surround Alert, rebreak timing, and protecting your own surround from the same attack (the
defensive side of Block Overlay's Surround Alert).
