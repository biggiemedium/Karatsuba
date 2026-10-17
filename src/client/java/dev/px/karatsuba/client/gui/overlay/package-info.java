/**
 * Game overlays: the client's own pictures in place of the game's full-screen
 * ones. Today the loading screen ({@link dev.px.karatsuba.client.gui.overlay.LoadingOverlay},
 * cherry trees by day or a harbour by night, instead of the Mojang red) and the menu panorama (the client's six
 * faces turning in place of the game's, {@link dev.px.karatsuba.client.gui.overlay.OverlayStyle#MENU_PANORAMA}).
 *
 * <p>Not {@code screen/}'s {@code Overlay}s, which are drawn over the game and
 * take input. These replace a picture the game draws and take nothing: each fills
 * a slot the adapter declares ({@code adapter.screen.LoadingScreen},
 * {@code MenuPanorama}), and the game keeps its own logic &mdash; when loading is
 * done, how it fades, how the panorama turns &mdash; and hands over only the
 * pictures.
 *
 * <p>Laid out against the window's height in design units rather than the GUI
 * scale ({@link dev.px.karatsuba.client.gui.overlay.OverlayStyle#REFERENCE_HEIGHT}),
 * with their tokens in {@link dev.px.karatsuba.client.gui.overlay.OverlayStyle}.
 * Listed in {@link dev.px.karatsuba.client.gui.overlay.GameOverlays}.
 */
package dev.px.karatsuba.client.gui.overlay;
