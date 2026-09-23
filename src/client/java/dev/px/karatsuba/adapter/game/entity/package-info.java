/**
 * Everything alive that is not the player, and what the crosshair is on.
 *
 * <p>{@code GameEntity} is the whole reason this package exists. Core has no
 * entity type and says so deliberately &mdash; an entity is the one thing that
 * cannot be made version independent &mdash; so the adapter supplies one, as a
 * <b>snapshot</b> rather than a wrapper. Its fields are read once, on the game
 * thread, and are then values: a module cannot dereference a removed entity
 * three frames later, because there is nothing left to dereference.
 *
 * <p>{@code Entities} builds those snapshots and {@code Targets} answers what
 * the crosshair is pointed at, which is either one of them or a block. The three
 * are together because {@code GameEntity.of} is package-private on purpose: a
 * snapshot is obtained from the two classes that know it must happen on the game
 * thread, and nowhere else.
 *
 * <p><b>Game thread only.</b>
 */
package dev.px.karatsuba.adapter.game.entity;
