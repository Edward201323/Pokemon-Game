package PlayerInventory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.ExpCurves;
import Pokemon.GetPokemonImages;
import Pokemon.Pokemon;

// Custom-drawn party UI. Dark gradient background, six rounded-card slots in a 2x3 grid,
// active/fainted/selectable states all visually distinct. Also hosts the reusable
// selection mode used by the battle switcher and the PC swap flow.
public class OpenPlayerInventory {
    private final GamePanel gp;
    private final KeyHandler keyH;
    private final GetPokemonImages pokemonImages = new GetPokemonImages();

    // Layout constants tuned for 864x672. Two columns x three rows, centered horizontally,
    // with a title strip at the top.
    private static final int CARD_W = 380;
    private static final int CARD_H = 150;
    private static final int GAP_X = 24;
    private static final int GAP_Y = 16;
    private static final int GRID_TOP = 96;

    private Font titleFont;
    private Font nameFont;
    private Font metaFont;
    private Font promptFont;
    private Font faintedFont;

    // Edge detection.
    private boolean prevP, prevI;
    private boolean prevZ, prevUp, prevDown, prevLeft, prevRight, prevEnter, prevEsc;

    // Selection mode (shared by battle switching and PC swap).
    private boolean selectionMode;
    private boolean cancellable;
    private IntPredicate selectableFilter;
    private IntConsumer onSelected;
    private Runnable onCancelled;
    private int cursor;
    private int returnState;
    private String prompt;

    // Regular P-menu state: a cursor + a "swap source" so the player can reorder party
    // members. First Enter/Z picks the source (slot lights up blue), second Enter/Z on
    // another slot swaps them. X cancels a pending source, or closes the menu otherwise.
    private int viewCursor;
    private int swapSource = -1;

    public OpenPlayerInventory(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            titleFont = base.deriveFont(Font.BOLD, 44f);
            nameFont = base.deriveFont(Font.BOLD, 30f);
            metaFont = base.deriveFont(Font.BOLD, 22f);
            promptFont = base.deriveFont(Font.BOLD, 24f);
            faintedFont = base.deriveFont(Font.BOLD, 22f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Public entry point: open the party UI in selection mode. Caller provides a prompt
    // string, whether X can cancel, an optional per-slot selectable filter, and the two
    // callbacks. The current gameState is captured and restored on close.
    public void openInSelectionMode(String prompt,
                                    boolean cancellable,
                                    IntPredicate selectable,
                                    IntConsumer onSelected,
                                    Runnable onCancelled) {
        this.selectionMode = true;
        this.cancellable = cancellable;
        this.selectableFilter = selectable;
        this.onSelected = onSelected;
        this.onCancelled = onCancelled;
        this.prompt = prompt;
        this.returnState = gp.gameState;

        int n = gp.playerPokemon.pokemonEquipped.size();
        this.cursor = 0;
        for (int i = 0; i < n; i++) {
            if (isSlotSelectable(i)) { this.cursor = i; break; }
        }
        // Seed every edge-tracked key so the same press that opened the selector doesn't
        // immediately confirm/cancel on the next frame.
        prevP = keyH.pPressed; prevI = keyH.iPressed;
        prevZ = keyH.zPressed;
        prevEnter = keyH.enterPressed; prevEsc = keyH.escPressed;
        prevUp = keyH.upPressed; prevDown = keyH.downPressed;
        prevLeft = keyH.leftPressed; prevRight = keyH.rightPressed;

        gp.gameState = gp.InventoryPokemonState;
    }

    private boolean isSlotSelectable(int slot) {
        int n = gp.playerPokemon.pokemonEquipped.size();
        if (slot < 0 || slot >= n) return false;
        if (selectableFilter == null) return true;
        return selectableFilter.test(slot);
    }

    private void closeSelector(boolean confirmed) {
        int selected = cursor;
        IntConsumer cb = onSelected;
        Runnable cancel = onCancelled;
        boolean wasCancellable = cancellable;
        selectionMode = false;
        selectableFilter = null;
        onSelected = null;
        onCancelled = null;
        prompt = null;
        gp.gameState = returnState;
        if (confirmed && cb != null) cb.accept(selected);
        else if (!confirmed && wasCancellable && cancel != null) cancel.run();
    }

    public void draw(Graphics2D g2) {
        openInventory();
        if (gp.gameState == gp.InventoryPokemonState) {
            drawInventoryPokemon(g2);
        }
        if (gp.gameState == gp.InventoryBagState) {
            drawInventoryBag(g2);
        }
    }

    private void openInventory() {
        if (selectionMode) {
            handleSelectionInput();
            return;
        }
        boolean justP = keyH.pPressed && !prevP;
        boolean justI = keyH.iPressed && !prevI;
        boolean justConfirm = (keyH.enterPressed && !prevEnter) || (keyH.zPressed && !prevZ);
        boolean justEsc = keyH.escPressed && !prevEsc;
        boolean justUp = keyH.upPressed && !prevUp;
        boolean justDown = keyH.downPressed && !prevDown;
        boolean justLeft = keyH.leftPressed && !prevLeft;
        boolean justRight = keyH.rightPressed && !prevRight;
        prevP = keyH.pPressed; prevI = keyH.iPressed;
        prevZ = keyH.zPressed;
        prevEnter = keyH.enterPressed; prevEsc = keyH.escPressed;
        prevUp = keyH.upPressed; prevDown = keyH.downPressed;
        prevLeft = keyH.leftPressed; prevRight = keyH.rightPressed;

        if (gp.gameState == gp.playState) {
            if (justP) {
                gp.gameState = gp.InventoryPokemonState;
                viewCursor = 0;
                swapSource = -1;
            } else if (justI) {
                gp.gameState = gp.InventoryBagState;
            }
            return;
        }
        if (gp.gameState == gp.InventoryBagState && justI) {
            gp.gameState = gp.playState;
            return;
        }
        if (gp.gameState != gp.InventoryPokemonState) return;

        // P-menu interaction: arrow keys move the cursor, Enter/Z picks a swap source
        // then a swap target, X cancels the pending source or closes the menu, P closes.
        int n = gp.playerPokemon.pokemonEquipped.size();
        if (justUp && viewCursor >= 2) viewCursor -= 2;
        if (justDown && viewCursor + 2 < n) viewCursor += 2;
        if (justLeft && viewCursor % 2 == 1) viewCursor -= 1;
        if (justRight && viewCursor % 2 == 0 && viewCursor + 1 < n) viewCursor += 1;

        if (justConfirm) {
            if (swapSource < 0) {
                swapSource = viewCursor;
            } else if (swapSource != viewCursor) {
                // Swap positions in the party list.
                java.util.ArrayList<Pokemon> party = gp.playerPokemon.pokemonEquipped;
                Pokemon a = party.get(swapSource);
                Pokemon b = party.get(viewCursor);
                party.set(swapSource, b);
                party.set(viewCursor, a);
                swapSource = -1;
            } else {
                // Re-confirmed the same slot: deselect.
                swapSource = -1;
            }
            return;
        }
        if (justEsc) {
            if (swapSource >= 0) { swapSource = -1; return; }
            gp.gameState = gp.playState;
            return;
        }
        if (justP) {
            swapSource = -1;
            gp.gameState = gp.playState;
        }
    }

    private void handleSelectionInput() {
        // Enter is the primary confirm; Z still works as an alternative.
        // Cancel is ESC only — not X — so a held X in battle (CATCH) can't double-fire
        // as both "cancel selector" and then "throw poke ball" when the selector hands
        // control back to BattleSystem.
        boolean justConfirm = (keyH.enterPressed && !prevEnter) || (keyH.zPressed && !prevZ);
        boolean justEsc = keyH.escPressed && !prevEsc;
        boolean justUp = keyH.upPressed && !prevUp;
        boolean justDown = keyH.downPressed && !prevDown;
        boolean justLeft = keyH.leftPressed && !prevLeft;
        boolean justRight = keyH.rightPressed && !prevRight;
        prevZ = keyH.zPressed;
        prevEnter = keyH.enterPressed; prevEsc = keyH.escPressed;
        prevUp = keyH.upPressed; prevDown = keyH.downPressed;
        prevLeft = keyH.leftPressed; prevRight = keyH.rightPressed;
        prevP = keyH.pPressed; prevI = keyH.iPressed;

        int n = gp.playerPokemon.pokemonEquipped.size();
        if (justUp && cursor >= 2) cursor -= 2;
        if (justDown && cursor + 2 < n) cursor += 2;
        if (justLeft && cursor % 2 == 1) cursor -= 1;
        if (justRight && cursor % 2 == 0 && cursor + 1 < n) cursor += 1;

        if (justConfirm && isSlotSelectable(cursor)) {
            closeSelector(true);
        } else if (justEsc && cancellable) {
            closeSelector(false);
        }
    }

    // ----- drawing -----

    private void drawInventoryPokemon(Graphics2D g2) {
        // Background gradient: deep teal → near-black for clean contrast against the cards.
        g2.setPaint(new GradientPaint(0, 0, new Color(26, 47, 61),
                                      0, gp.screenHeight, new Color(14, 22, 32)));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        // Title strip.
        if (titleFont != null) g2.setFont(titleFont);
        g2.setColor(new Color(230, 240, 250));
        g2.drawString("PARTY", 32, 60);

        // Header hint at top-right.
        if (promptFont != null) g2.setFont(promptFont);
        String hint;
        Color hintColor;
        if (selectionMode && prompt != null) {
            hint = prompt + (cancellable ? "   (ESC to cancel)" : "");
            hintColor = new Color(255, 220, 60);
        } else if (swapSource >= 0) {
            hint = "Pick a slot to swap with   (ESC cancel)";
            hintColor = new Color(120, 200, 255);
        } else {
            hint = "Enter swap   ESC close";
            hintColor = new Color(160, 175, 190);
        }
        g2.setColor(hintColor);
        int tw = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, gp.screenWidth - tw - 32, 56);

        int gridW = CARD_W * 2 + GAP_X;
        int startX = (gp.screenWidth - gridW) / 2;
        for (int i = 0; i < 6; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (CARD_W + GAP_X);
            int y = GRID_TOP + row * (CARD_H + GAP_Y);
            if (i < gp.playerPokemon.pokemonEquipped.size()) {
                drawCard(g2, gp.playerPokemon.pokemonEquipped.get(i), x, y, CARD_W, CARD_H, i);
            } else {
                drawEmptyCard(g2, x, y, CARD_W, CARD_H);
            }
        }
    }

    private void drawCard(Graphics2D g2, Pokemon p, int x, int y, int w, int h, int slot) {
        boolean fainted = p.currentHP <= 0;
        boolean cursorHere = selectionMode ? slot == cursor : slot == viewCursor;
        boolean selectable = !selectionMode || isSlotSelectable(slot);
        boolean swapPicked = !selectionMode && slot == swapSource;

        // Card panel.
        g2.setColor(fainted ? new Color(60, 30, 32, 230) : new Color(30, 45, 60, 230));
        g2.fillRoundRect(x, y, w, h, 18, 18);

        // Inner highlight for the lead party member (slot 0) when not in selection mode,
        // since slot 0 is what the game uses as "active" by default.
        if (!selectionMode && slot == 0) {
            g2.setColor(new Color(80, 200, 130, 60));
            g2.fillRoundRect(x, y, 6, h, 6, 6); // little accent strip on the left
        }

        // Border: blue when this is the picked swap source, gold for the cursor, dim slate otherwise.
        Stroke oldStroke = g2.getStroke();
        Color borderColor;
        float borderW;
        if (swapPicked) {
            borderColor = new Color(120, 200, 255);
            borderW = 4f;
        } else if (cursorHere) {
            borderColor = new Color(255, 220, 60);
            borderW = 4f;
        } else {
            borderColor = new Color(80, 110, 140, 200);
            borderW = 2f;
        }
        g2.setStroke(new BasicStroke(borderW));
        g2.setColor(borderColor);
        g2.drawRoundRect(x, y, w, h, 18, 18);
        g2.setStroke(oldStroke);

        // Sprite.
        int spriteSize = h - 24;
        int sX = x + 14;
        int sY = y + (h - spriteSize) / 2;
        BufferedImage sprite = pokemonImages.getPokemonFront(p);
        if (sprite != null) {
            g2.drawImage(sprite, sX, sY, spriteSize, spriteSize, null);
        }

        // Text area.
        int textX = sX + spriteSize + 14;
        if (nameFont != null) g2.setFont(nameFont);
        g2.setColor(fainted ? new Color(220, 130, 130) : Color.white);
        g2.drawString(p.name, textX, y + 38);
        int nameW = g2.getFontMetrics().stringWidth(p.name);
        PokemonEncounters.BattleSystem.drawGender(g2, p.gender, textX + nameW + 10, y + 38);

        if (metaFont != null) g2.setFont(metaFont);
        g2.setColor(new Color(190, 200, 210));
        g2.drawString("Lv " + p.level, textX, y + 64);

        // HP bar — sits higher than before to leave room for the EXP bar underneath.
        int barX = textX;
        int barY = y + h - 78;
        int barW = (x + w) - barX - 16;
        int barH = 12;
        drawHpBar(g2, p, barX, barY, barW, barH);

        // HP text.
        if (metaFont != null) g2.setFont(metaFont);
        g2.setColor(Color.white);
        g2.drawString(p.currentHP + "/" + p.maxHP, barX, barY + barH + 18);

        // EXP bar (blue) along the bottom edge of the card.
        int expY = y + h - 18;
        int expH = 6;
        if (metaFont != null) g2.setFont(metaFont);
        g2.setColor(new Color(150, 200, 255));
        g2.drawString("EXP", barX, expY - 3);
        int expLabelW = 56;
        int expBarX = barX + expLabelW;
        int expBarW = (x + w) - expBarX - 16;
        double frac = ExpCurves.levelProgress(p);
        int expFilled = (int) Math.round(expBarW * frac);
        g2.setColor(new Color(20, 25, 32));
        g2.fillRoundRect(expBarX, expY, expBarW, expH, expH, expH);
        if (expFilled > 0) {
            g2.setColor(new Color(80, 150, 240));
            g2.fillRoundRect(expBarX, expY, expFilled, expH, expH, expH);
        }

        // FAINTED tag.
        if (fainted && faintedFont != null) {
            g2.setFont(faintedFont);
            g2.setColor(new Color(220, 80, 80));
            g2.drawString("FAINTED", textX + 110, y + 38);
        }

        // Dim non-selectable slots after the card is drawn so the dim layer covers everything.
        if (selectionMode && !selectable) {
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fillRoundRect(x, y, w, h, 18, 18);
        }
    }

    private void drawEmptyCard(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(new Color(30, 45, 60, 110));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(60, 80, 100, 170));
        g2.drawRoundRect(x, y, w, h, 18, 18);
        g2.setStroke(oldStroke);
        if (metaFont != null) g2.setFont(metaFont);
        g2.setColor(new Color(90, 110, 130));
        g2.drawString("Empty", x + 22, y + h / 2 + 6);
    }

    private void drawHpBar(Graphics2D g2, Pokemon p, int x, int y, int w, int h) {
        if (p.maxHP <= 0 || w <= 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, p.currentHP / (double) p.maxHP));
        int filled = (int) Math.round(w * ratio);
        Color fill;
        if (ratio > 0.5)      fill = new Color(80, 200, 80);
        else if (ratio > 0.2) fill = new Color(230, 200, 60);
        else                  fill = new Color(220, 60, 60);
        // Track + fill with subtle rounded ends.
        g2.setColor(new Color(20, 25, 32));
        g2.fillRoundRect(x, y, w, h, h, h);
        if (filled > 0) {
            g2.setColor(fill);
            g2.fillRoundRect(x, y, filled, h, h, h);
        }
    }

    private void drawInventoryBag(Graphics2D g2) {
        // TODO: bag inventory screen
    }
}
