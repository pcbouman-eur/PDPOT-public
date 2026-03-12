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
import podrouting.data.Vehicle;
import podrouting.data.assign.AssignedSolution;
import podrouting.visualizer.draw.Bounds;
import podrouting.visualizer.draw.DrawConfig;
import podrouting.visualizer.draw.DrawContext;
import podrouting.visualizer.draw.DrawElement;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleTable implements DrawElement {

    @Override
    public int computeHeight(DrawContext ctx) {
        Instance instance = ctx.getInstance();
        DrawConfig cfg = ctx.getConfig();
        List<Vehicle> vehicles = instance.getVehicles();
        int numVehicles = vehicles.size();
        int numSeats = vehicles.stream().mapToInt(Vehicle::getCapacity).sum();
        return  numVehicles * cfg.getVehicleLabelHeight()
                + Math.max(0, numVehicles-1) * cfg.getVehicleSpacing()
                + numSeats * cfg.getPassengerHeight();
    }

    @Override
    public void draw(Graphics2D g2d, DrawContext ctx, int xOffset, int yOffset) {
        Map<Vehicle, Bounds> vehicleBounds = computeVehicleRectangles(ctx, xOffset, yOffset);
        Map<Vehicle, Color> vehicleColors = ctx.getVehicleColors();
        drawVehicleBoxes(g2d, ctx, xOffset, vehicleBounds, vehicleColors);
    }

    public Map<Vehicle, Rectangle> drawVehicleBoxes(Graphics2D g2d,
                                                    DrawContext ctx,
                                                    int xOffset,
                                                    Map<Vehicle, Bounds> positions,
                                                    Map<Vehicle,Color> colors) {
        AssignedSolution assignment = ctx.getAssignment();
        List<Vehicle> vehicles = assignment.getSolution().getInstance().getVehicles();

        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        Map<Vehicle,Rectangle> map = new HashMap<>();
        for (Vehicle v : vehicles) {
            Bounds bounds = positions.get(v);
            ctx.drawBox(g2d, bounds, colors.get(v), xOffset, "Vehicle "+v.getId());
        }

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
        return map;
    }

    public Map<Vehicle, Bounds> computeVehicleRectangles(DrawContext ctx, int xOffset, int yOffset) {
        AssignedSolution assignment = ctx.getAssignment();
        DrawConfig cfg = ctx.getConfig();
        List<Vehicle> vehicles = assignment.getSolution().getInstance().getVehicles();
        int xStart = xOffset + ctx.computeXRoundOffset(0);
        int xEnd = xOffset + ctx.computeXRoundOffset(assignment.getSolution().getInstance().getMaximumTime());

        int currentY = yOffset;
        Map<Vehicle, Bounds> map = new HashMap<>();
        for (Vehicle v : vehicles) {
            int firstY = currentY;
            int lastY = firstY + v.getCapacity() * cfg.getPassengerHeight() + cfg.getVehicleLabelHeight();
            map.put(v, new Bounds(xStart, xEnd, firstY, lastY));
            currentY = lastY + cfg.getVehicleSpacing();
        }
        return map;
    }
}
