package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.accounts.AccountHeader;
import dev.px.karatsuba.client.gui.apple.shell.section.accounts.AccountRow;
import dev.px.karatsuba.client.gui.apple.shell.section.accounts.SignInBody;
import dev.px.karatsuba.client.gui.apple.shell.section.accounts.SignInCard;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.avatar;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * Accounts ({@code gui/apple/DESIGN.md} §2.6c): account rows in their card, and the
 * first card of the list and of an account's page. Drawn as Friends' are, monogram
 * and all, so a person reads the same way on both pages. Rows sit in cards, so the
 * card draws their highlight and hairlines.
 */
final class AccountsLook {

    private AccountsLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // An account: face, or monogram (the dot while in use), its name, how it signs in, ›.
        look.register(WidgetRenderer.of(AccountRow.class, (row, c) -> c.align(Align.STRETCH).row(r -> r
                .height(CELL_HEIGHT).padding(CELL_INSET, 0f).gap(GAP_CONTROL).align(Align.CENTER)
                .custom(AVATAR_SIZE, AVATAR_SIZE, (x, y, w, h) ->
                        avatar(x, y, w, row.name(), row.face(), row.isInUse(), caption()))
                .text(text(), row.name(), LABEL.get())
                .fill().text(text(), row.status(), LABEL_SECONDARY.get()).space(6f - GAP_CONTROL, 0f)
                .custom(6f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.FORWARD, x - 1.5f, y, h, LABEL_TERTIARY.get())))));

        // Who an account is, leading its page, as the account card leads System Settings.
        look.register(WidgetRenderer.of(AccountHeader.class, (header, c) -> c.align(Align.STRETCH).row(r -> r
                .padding(CELL_INSET, 10f).gap(GAP_CONTROL + 4f).align(Align.CENTER)
                .custom(AVATAR_LARGE, AVATAR_LARGE, (x, y, w, h) ->
                        avatar(x, y, w, header.name(), header.face(), header.isInUse(), heading()))
                .column(names -> names.gap(3f)
                        .text(textStrong(), header.name(), LABEL.get())
                        .text(caption(), header.caption(), LABEL_SECONDARY.get())))));

        // The sign-in card: a popover, solid and elevated (§5.1), its buttons in a row under the words.
        look.register(WidgetRenderer.of(SignInCard.class, (card, c) -> c.align(Align.STRETCH)
                .backdrop(Shapes::popupSurface).padding(INSET_CONTENT).slot()));

        // Its words: a title, a sentence, the code on a neutral tile, and where it has got to.
        look.register(WidgetRenderer.of(SignInBody.class, (body, c) -> {
            String code = body.code();
            String status = body.status();
            c.align(Align.STRETCH).column(column -> {
                column.align(Align.STRETCH).gap(6f)
                        .text(textStrong(), body.title(), LABEL.get())
                        .paragraph(text(), body.sentence(), LABEL_SECONDARY.get());
                if (!code.isEmpty()) {
                    Color ink = body.codeLive() ? LABEL.get() : LABEL_TERTIARY.get();
                    column.space(0f, 4f).row(tile -> tile.height(CODE_TILE_HEIGHT).align(Align.CENTER)
                            .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, R_BUTTON, SELECTED.get()))
                            .fill().text(code(), code, ink).fill());
                }
                if (!status.isEmpty()) {
                    column.space(0f, 2f).text(caption(), status, LABEL_SECONDARY.get());
                }
            });
        }));
    }
}
