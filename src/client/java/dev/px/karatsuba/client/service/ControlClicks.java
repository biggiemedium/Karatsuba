package dev.px.karatsuba.client.service;

import dev.px.core.control.Click;
import dev.px.core.control.ClickSink;
import dev.px.core.control.ControlService;
import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.action.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Core's attack and use buttons ({@code Core.controls()}), pressed through this
 * client's hands.
 *
 * <p>A flow step or a library part that clicks asks Core for the button, as it
 * asks for the head and the keys, and Core decides whose click wins. This is the
 * {@link ClickSink} that turns the winning click into something sent, and it sends
 * it the way every module's click is sent: as an {@link Action} through
 * {@link ActionService}, at the start of the next tick, checked against the
 * rotation the server was told. So a flow clicking gets the same reach, look and
 * timing checks as a module, and the two contend for the button in one place.
 *
 * <h2>What a click is at</h2>
 * A button carries no target, only "press it". What it is pressed at is whatever
 * the server's rotation looks at, which is what the server will judge it by:
 * <ul>
 *   <li><b>Attack:</b> the entity that look meets first, within reach. A swing at
 *       a block or at nothing is not sent: breaking is held, not clicked.</li>
 *   <li><b>Use:</b> the block face that look meets, within reach; otherwise the
 *       held item, at nothing.</li>
 * </ul>
 * The step that clicked has already turned the head there through its own
 * rotation claim, so the action is sent without turning again.
 *
 * <h2>Holding</h2>
 * Breaking a block, eating and drawing a bow hold the button down, which the
 * game handles itself, tick by tick: its own speed, its own packets. A hold is
 * passed on as the key held ({@link Inputs#hold}), and Core always lets it go.
 * The game acts on a held key where the <em>camera</em> looks, so a step that
 * holds to break turns the camera, not a silent rotation; and a step that holds
 * to eat starts the eating itself, so the hold only carries it on.
 *
 * <h2>When</h2>
 * Core's claims are applied at the start of the tick, once every flow and
 * module has filed its own, and before the game handles its buttons
 * ({@code handleInputEvents}). A hold therefore takes effect that same tick, and
 * ends the tick its claim lapses, before the game can act on it once more.
 *
 * <p>A click is only noted then. What it is at is settled at the end of the
 * tick, once that tick's rotation has gone out, and handed to the action
 * service, which sends it at the start of the next: the action service's own
 * order, see its notes on Grim's Post check.
 */
public final class ControlClicks implements Service, ClickSink {

    private final EventBus bus;
    private final ControlService controls;
    private final ActionService actions;

    /** Clicks noted at the start of the tick, settled at its end. */
    private final List<Pending> pending = new ArrayList<>(2);

    private Subscription tickStart;
    private Subscription tickEnd;

    public ControlClicks(EventBus bus, ControlService controls, ActionService actions) {
        this.bus = bus;
        this.controls = controls;
        this.actions = actions;
    }

    @Override
    public String getName() {
        return "Control Clicks";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Service>[] dependsOn() {
        return new Class[]{ControlService.class, ActionService.class};
    }

    @Override
    public void start() {
        controls.setClickSink(this);
        // LOWEST: after every flow (HIGH) and module has filed this tick's claims.
        tickStart = bus.on(TickEvent.class, Priority.LOWEST, event -> {
            if (event.getStage() == Stage.PRE) {
                controls.applyClicks();
            }
        });
        tickEnd = bus.on(TickEvent.class, event -> {
            if (event.getStage() == Stage.POST) {
                submitPending();
            }
        });
    }

    @Override
    public void stop() {
        for (Subscription subscription : new Subscription[]{tickStart, tickEnd}) {
            if (subscription != null) {
                subscription.close();
            }
        }
        tickStart = tickEnd = null;
        pending.clear();
        if (controls.getClickSink() == this) {
            controls.setClickSink(null);
        }
    }

    @Override
    public void click(Click button) {
        Object owner = controls.getHolder(button);
        pending.add(new Pending(button, owner != null ? owner : this));
    }

    @Override
    public void setHeld(Click button, boolean held) {
        Inputs.hold(button == Click.ATTACK ? Inputs.Control.ATTACK : Inputs.Control.USE, held);
    }

    /** Settles this tick's clicks against the rotation just sent, and hands them to the action service. */
    private void submitPending() {
        if (Players.present()) {
            for (Pending click : pending) {
                Action action = click.button() == Click.ATTACK ? attack() : use();
                if (action != null) {
                    actions.submit(click.owner(), action.rotate(false));
                }
            }
        }
        pending.clear();
    }

    /** @return an attack on the entity the server's look meets, or null for none */
    private static Action attack() {
        int entity = Targets.entityAlong(Players.eyePosition(), Players.serverRotation(), Players.entityReach());
        return entity == Targets.NO_ENTITY ? null : Action.attack(entity);
    }

    /** @return a click on the block face the server's look meets, else a use of the held item */
    private static Action use() {
        Vec3 eye = Players.eyePosition();
        Vec2 look = Players.serverRotation();
        Targets.Hit hit = Targets.raycast(eye, look, Players.blockReach());
        return hit == null ? Action.use() : Action.click(hit.getBlock(), hit.getFace(), hit.getPoint());
    }

    /** A click Core pressed, and whose claim it was. */
    private record Pending(Click button, Object owner) {
    }
}
