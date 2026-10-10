package dev.px.karatsuba.client.automation.task;

import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.client.automation.step.Equip;
import dev.px.karatsuba.client.automation.step.Throw;

import java.util.List;

/**
 * Quick things done with an item: a pearl thrown, an item put in the offhand.
 *
 * <pre>
 * .task pearl 100 64 -20     land a pearl on top of that block
 * .task offhand              a totem into the offhand
 * .task offhand shield
 * </pre>
 *
 * <p>See {@link Throw} and {@link Equip}.
 */
public final class ItemTasks {

    public static final TaskType PEARL = TaskType.of("pearl", "<x y z>",
            "Throws an ender pearl to land on top of a block", args -> pearl(args.block(0)))
            .completes(index -> index < 3 ? List.of("~") : List.of());

    public static final TaskType OFFHAND = TaskType.of("offhand", "[item]",
            "Puts an item in the offhand, a totem unless told otherwise",
            args -> offhand(args.get(0, "totem_of_undying")))
            .completes(index -> index == 0 ? List.of("totem_of_undying", "shield", "end_crystal") : List.of());

    private ItemTasks() {
    }

    /** @return a task that lands an ender pearl on top of {@code block} */
    public static Task pearl(Vec3i block) {
        Vec3 top = Vec3.of(block.getX() + 0.5d, block.getY() + 1d, block.getZ() + 0.5d);
        return Task.of("Pearl", "to " + block.getX() + ", " + block.getY() + ", " + block.getZ(),
                navigation -> Throw.pearl(top));
    }

    /** @return a task that puts {@code item} in the offhand */
    public static Task offhand(String item) {
        return Task.of("Offhand", item, navigation -> Equip.offhand(item));
    }
}
