package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.asset.PlayerFaces;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The first card on the list and on an account's page, as the account card leads
 * System Settings: a large face (the monogram while it loads), a name, and a line
 * under it. A heading, not a control: it never highlights. Read live, so a sign-in
 * finishing changes it without a rebuild.
 */
public final class AccountHeader extends ControlRow {

    private final Supplier<String> name;
    private final Supplier<String> uuid;
    private final Supplier<String> caption;
    private final BooleanSupplier inUse;

    /**
     * @param uuid the UUID whose face to show, as text; null for the monogram
     */
    AccountHeader(Supplier<String> name, Supplier<String> uuid, Supplier<String> caption, BooleanSupplier inUse) {
        this.name = name;
        this.uuid = uuid;
        this.caption = caption;
        this.inUse = inUse;
    }

    @Override
    public String name() {
        return name.get();
    }

    /** @return the face, or null for the monogram */
    public Texture face() {
        return PlayerFaces.face(uuid.get());
    }

    public String caption() {
        return caption.get();
    }

    /** @return whether the presence dot shows: the game is playing as this account */
    public boolean isInUse() {
        return inUse.getAsBoolean();
    }

    @Override
    public float hover() {
        return 0f;
    }

    @Override
    protected boolean pressed(float x, float y) {
        return false;
    }

    @Override
    protected void reset() {
    }
}
