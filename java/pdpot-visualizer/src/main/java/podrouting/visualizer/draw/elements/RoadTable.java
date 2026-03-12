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

import podrouting.data.*;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.visualizer.RoadIndexTable;
import podrouting.visualizer.draw.Bounds;
import podrouting.visualizer.draw.DrawConfig;
import podrouting.visualizer.draw.DrawContext;
import podrouting.visualizer.draw.DrawElement;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class RoadTable implements DrawElement {

    @Override
    public int computeHeight(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();
        RoadIndexTable roadTable = ctx.getRoadTable();
        int result = cfg.getArcSpacing() * Math.max(0,roadTable.getRoadCount()-1);
        result += cfg.getVehicleHeight() * roadTable.getVehicleRows();
        return result;
    }

    @Override
    public void draw(Graphics2D g2d, DrawContext ctx, int xOffset, int yOffset) {
        Map<Road, Bounds> roadBounds = computeRoadRectangles(ctx, xOffset, yOffset);
        Map<Vehicle, Color> vehicleColors = ctx.getVehicleColors();
        Map<Location,String> labels = ctx.getLocationLabels();
        drawRoadTable(g2d, ctx, xOffset, roadBounds, vehicleColors, labels);
    }

    public void drawRoadTable(Graphics2D g2d, DrawContext ctx, int xOffset, Map<Road, Bounds> positions,
                              Map<Vehicle,Color> colors, Map<Location,String> labels) {
        DrawConfig cfg = ctx.getConfig();
        Solution solution = ctx.getSolution();
        RoadIndexTable roadTable = ctx.getRoadTable();
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();

        for (Path<Vehicle> path : solution.getVehiclePaths()) {
            Vehicle v = path.getCommodity();
            for (TimedArc ta : path.getPath()) {
                Road r = ta.getRoad();
                if (r == null) {
                    continue;
                }
                Bounds b = positions.get(r);
                if (b == null) {
                    System.out.println("huh??");
                    continue;
                }
                int index = roadTable.getVehicleIndex(v, ta);
                int xStart = xOffset + ctx.computeXRoundOffset(ta.getFromTime());
                int xEnd = xOffset +ctx.computeXRoundOffset(ta.getToTime());
                int firstY = b.topY + index * cfg.getVehicleHeight();
                g2d.setColor(colors.get(v));
                g2d.fillRect(xStart, firstY, xEnd-xStart, cfg.getVehicleHeight());
            }
        }

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        for (Road r : roadTable.getRelevantRoads()) {
            String label = labels.get(r.getOrigin()) + " "+(char)0x2192+" " + labels.get(r.getDestination());
            ctx.drawBox(g2d, positions.get(r), null, xOffset, label);
        }

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    public Map<Road, Bounds> computeRoadRectangles(DrawContext ctx, int xOffset, int yOffset) {
        DrawConfig cfg = ctx.getConfig();
        Instance instance = ctx.getInstance();
        RoadIndexTable roadTable = ctx.getRoadTable();
        int currentY = yOffset;
        int xStart = xOffset + ctx.computeXRoundOffset(instance.getMinimumTime());
        int xEnd = xOffset + ctx.computeXRoundOffset(instance.getMaximumTime());
        Map<Road, Bounds> result = new HashMap<>();
        for (Road r : roadTable.getRelevantRoads()) {
            int firstY = currentY;
            int lastY = firstY + (1 + roadTable.getMaxVehicleIndex(r)) * cfg.getVehicleHeight();
            result.put(r, new Bounds(xStart, xEnd, firstY, lastY));
            currentY = lastY + cfg.getArcSpacing();
        }
        return result;
    }

}
