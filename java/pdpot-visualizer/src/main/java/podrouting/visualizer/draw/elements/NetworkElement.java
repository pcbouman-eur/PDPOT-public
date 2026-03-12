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
package podrouting.visualizer.draw.elements;

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Road;
import podrouting.visualizer.draw.DrawContext;
import podrouting.visualizer.draw.DrawElement;
import podrouting.visualizer.draw.DrawScaler;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.util.Map;

public class NetworkElement implements DrawElement {

    private float roadWidth = 1f;
    private Color roadColor = Color.LIGHT_GRAY;
    private float highwayWidth = 5f;
    private Color highwayColor = Color.DARK_GRAY;
    private int locationRadius = 3;
    private int intersectionRadius = 1;
    private Color locationColor = Color.BLUE;
    private int labelOffset = 2;
    private float outlineWidth = 2f;

    @Override
    public int computeWidth(DrawContext ctx) {
        return ctx.getConfig().getNetworkWidth();
    }

    @Override
    public int computeHeight(DrawContext ctx) {
        return ctx.getConfig().getNetworkHeight();
    }

    @Override
    public void draw(Graphics2D g, DrawContext ctx, int xOffset, int yOffset) {
        Instance instance = ctx.getInstance();
        DrawScaler scaler = new DrawScaler(ctx.getSolution(), computeWidth(ctx), computeHeight(ctx));
        Map<Location,String> locationLabels = ctx.getLocationLabels();
        Color oldColor = g.getColor();
        Stroke oldStroke = g.getStroke();

        Font font = g.getFont();
        FontRenderContext frc = g.getFontRenderContext();
        Stroke highwayStroke = new BasicStroke(highwayWidth);
        Stroke roadStroke = new BasicStroke(roadWidth);
        Stroke outlineStroke = new BasicStroke(outlineWidth);

        // Draw roads
        for (Road r : instance.getRoads()) {
            Location o = r.getOrigin();
            Location d = r.getDestination();
            if(d.getName().equals("sink")) {
                continue;
            }

            if((o.getType().equals(LocationType.INTERSECTION))&d.getType().equals(LocationType.INTERSECTION)) {
                g.setStroke(highwayStroke);
                g.setColor(highwayColor);
                g.drawLine(xOffset + scaler.scaleXInt(o.getX()), yOffset + scaler.scaleYInt(o.getY()),
                        xOffset + scaler.scaleXInt(d.getX()), yOffset + scaler.scaleYInt(d.getY()));
            }
            else {
                g.setStroke(roadStroke);
                g.setColor(roadColor);
                g.drawLine(xOffset + scaler.scaleXInt(o.getX()), yOffset + scaler.scaleYInt(o.getY()),
                        xOffset + scaler.scaleXInt(d.getX()), yOffset + scaler.scaleYInt(d.getY()));
            }
        }

        // Draw locations
        g.setStroke(new BasicStroke());
        for (Location loc : instance.getLocations()) {
            if(loc.getName().equals("sink")) {
                continue;
            }
            int x = xOffset + scaler.scaleXInt(loc.getX());
            int y = yOffset + scaler.scaleYInt(loc.getY());
            if(loc.getType().equals(LocationType.PARKING) || loc.getType().equals(LocationType.STOP)) {
                x -= locationRadius;
                y -= locationRadius;
                g.setColor(locationColor);
                if (loc.getType().equals(LocationType.PARKING)) {
                    g.drawRect(x, y, 2 * locationRadius, 2 * locationRadius);
                }
                else {
                    g.drawOval(x, y, 2 * locationRadius, 2 * locationRadius);
                }
            }
            else {
                // Draw intersections?
                x -= intersectionRadius;
                y -= intersectionRadius;
                g.setColor(roadColor);
                g.drawRect(x, y, 2 * intersectionRadius, 2 * intersectionRadius);
            }
            GlyphVector gv = font.createGlyphVector(frc, locationLabels.get(loc));
            Shape shp = gv.getOutline(x, y - labelOffset);
            Stroke old = g.getStroke();
            g.setColor(Color.WHITE);
            g.setStroke(outlineStroke);
            g.draw(shp);
            g.setStroke(old);
            g.setColor(Color.BLACK);
            g.fill(shp);
        }

        g.setColor(oldColor);
        g.setStroke(oldStroke);
    }

}
