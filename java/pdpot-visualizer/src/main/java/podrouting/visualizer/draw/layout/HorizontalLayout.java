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
package podrouting.visualizer.draw.layout;

import podrouting.visualizer.draw.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HorizontalLayout implements ElementLayout {

    private boolean center = true;
    private List<DrawElement> elements;

    public HorizontalLayout(List<DrawElement> elements) {
        this.elements = elements;
    }

    @Override
    public Bounds getBounds(DrawContext ctx) {
        return new Bounds(0, computeWidth(ctx), 0, computeHeight(ctx));
    }

    private int computeHeight(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();
        int max = elements.stream()
                .mapToInt(de -> de.computeHeight(ctx))
                .max()
                .orElse(0);
        return max
                + cfg.getTimeMargin()
                + 2 * cfg.getPadding();
    }

    private int computeWidth(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();
        int sum = elements.stream()
                .mapToInt(de -> de.computeWidth(ctx))
                .sum();
        return sum
                + Math.max(0, elements.size()-1) * cfg.getSectionSpacing()
                + 2 * cfg.getPadding();
    }

    @Override
    public Map<DrawElement, Bounds> getLayout(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();

        int padding = cfg.getPadding();
        int height = computeHeight(ctx);
        int width = computeWidth(ctx);
        int xOffset = cfg.getPadding();
        int yOffset = cfg.getPadding() + cfg.getTimeMargin();

        Map<DrawElement, Bounds> result = new LinkedHashMap<>();
        for (DrawElement el : elements) {
            int elWidth = el.computeWidth(ctx);
            int elHeight = el.computeHeight(ctx);
            int ySlack = height - 2*padding - cfg.getTimeMargin() - elHeight;
            int y = center ? yOffset + (int) Math.round(ySlack * 0.5) : yOffset;
            result.put(el, new Bounds(xOffset, xOffset+elWidth, y, y+elHeight));
            xOffset += elWidth;
            xOffset += cfg.getSectionSpacing();
        }
        return result;
    }

}
