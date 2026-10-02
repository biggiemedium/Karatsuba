package dev.px.karatsuba.client.module.render.nametag;

import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter.ItemColumn;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter.Segment;

import java.util.List;

/**
 * Everything one tag shows, as of the last time its player was seen.
 *
 * <p>Kept whole so a tag fading out after its player has gone can still be drawn
 * exactly as it last looked.
 *
 * @param entity the player, frozen once they are out of sight
 * @param info   the info line
 * @param items  the item row
 */
public record NameTag(GameEntity entity, List<Segment> info, List<ItemColumn> items) {
}
