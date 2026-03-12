/*
 * Copyright © 2026, Erasmus Univeristy Rotterdam,
 * Paul Bouman, Rick Willemsen, Gizem Özbaygın,
 * bouman@ese.eur.nl, rick_willemsen@sutd.edu.sg, ozbaygin@bilkent.edu.tr
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with this program.  If not, see
 *  <https://www.gnu.org/licenses/>.
 */
package podrouting.visualizer.draw;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface ElementLayout {

    Bounds getBounds(DrawContext ctx);
    Map<DrawElement, Bounds> getLayout(DrawContext ctx);

    default void render(Graphics2D g2d, DrawContext ctx) {

        // Debug lines for the padding
        if (ctx.getConfig().isDebug()) {
            Bounds full = getBounds(ctx);
            int padding = ctx.getConfig().getPadding();
            int width = full.getWidth();
            int height = full.getHeight();
            ctx.debugLine(g2d, Color.GREEN, 0, padding, width, padding);
            ctx.debugLine(g2d, Color.GREEN, 0, height - padding, width, height - padding);
            ctx.debugLine(g2d, Color.GREEN, padding, 0, padding, height);
            ctx.debugLine(g2d, Color.GREEN, width - padding, 0, width - padding, height);
        }

        Map<DrawElement, Bounds> positions = getLayout(ctx);
        for (Map.Entry<DrawElement, Bounds> entry : positions.entrySet()) {
            DrawElement el = entry.getKey();
            Bounds b = entry.getValue();
            el.draw(g2d, ctx, b.leftX, b.topY);

            // Debug bounding box of the element
            ctx.debugRect(g2d, Color.CYAN, b.leftX, b.topY, b.getWidth(), b.getHeight());
        }
    }

    default BufferedImage renderImage(DrawContext ctx) {
        Bounds full = getBounds(ctx);
        BufferedImage bi = new BufferedImage(full.getWidth(), full.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0,0,full.getWidth(), full.getHeight());
        render(g2d, ctx);
        g2d.dispose();
        return bi;
    }

    default void writePNGImage(DrawContext ctx, File target) throws IOException {
        BufferedImage img = renderImage(ctx);
        ImageIO.write(img, "png", target);
    }

}
