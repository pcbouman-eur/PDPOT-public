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

import podrouting.data.Passenger;
import podrouting.data.Vehicle;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.visualizer.draw.Bounds;
import podrouting.visualizer.draw.DrawConfig;
import podrouting.visualizer.draw.DrawContext;
import podrouting.visualizer.draw.DrawElement;

import java.awt.*;
import java.util.Map;

import static podrouting.visualizer.draw.DrawUtils.halfwayRound;

public class PassengerVehicleTable implements DrawElement {

    private boolean drawTimedArcBoxes = true;
    private PassengerTable passengerTable = new PassengerTable();
    private VehicleTable vehicleTable = new VehicleTable();

    @Override
    public int computeHeight(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();
        return passengerTable.computeHeight(ctx) +
                vehicleTable.computeHeight(ctx) +
                cfg.getSectionSpacing();
    }

    @Override
    public void draw(Graphics2D g2d, DrawContext ctx, int xOffset, int yOffset) {
        int passengerYOffset = yOffset + vehicleTable.computeHeight(ctx) + ctx.getConfig().getSectionSpacing();
        AssignedSolution assignment = ctx.getAssignment();
        Map<Passenger,Color> passengerColors = ctx.getPassengerColors();
        Map<Vehicle,Color> vehicleColors = ctx.getVehicleColors();
        Map<Passenger, Bounds> passengerBounds = passengerTable.computePassengerRectangles(ctx, xOffset, passengerYOffset);
        Map<Vehicle, Bounds> vehicleBounds = vehicleTable.computeVehicleRectangles(ctx, xOffset, yOffset);

        for (Path<Passenger> path : assignment.getSolution().getPassengerPaths()) {
            Passenger p = path.getCommodity();
            Color passengerColor = passengerColors.get(p);
            drawPassengerPath(g2d, ctx, xOffset, p, vehicleBounds, passengerBounds.get(p),
                    passengerColor, vehicleColors);
            passengerTable.drawPassengerPathRow(g2d, ctx, xOffset, assignment, path, passengerBounds.get(p),
                    vehicleColors, passengerColor);
        }
        vehicleTable.drawVehicleBoxes(g2d, ctx, xOffset, vehicleBounds, vehicleColors);
        passengerTable.drawPassengerBoxes(g2d, ctx, xOffset, assignment, passengerBounds, passengerColors);

        if (drawTimedArcBoxes) {
            Color old = g2d.getColor();
            g2d.setColor(Color.BLACK);
            for (Path<Vehicle> path : assignment.getSolution().getVehiclePaths()) {
                for (TimedArc ta : path.getPath()) {
                    int xStart = xOffset + ctx.computeXRoundOffset(ta.getFromTime());
                    int width = xOffset + ctx.computeXRoundOffset(ta.getToTime()) - xStart;
                    Bounds bounds = vehicleBounds.get(path.getCommodity());
                    int yStart = bounds.topY;
                    int height = bounds.getHeight();
                    g2d.drawRect(xStart, yStart, width, height);
                }
            }
            g2d.setColor(old);
        }
    }

    public static void drawPassengerPath(Graphics2D g2d, DrawContext ctx, int xOffset, Passenger p,
                                  Map<Vehicle, Bounds> vehiclePositions, Bounds passengerBounds, Color passengerColor,
                                  Map<Vehicle,Color> vehicleColors) {
        DrawConfig cfg = ctx.getConfig();
        AssignedSolution assignment = ctx.getAssignment();
        Color old = g2d.getColor();
        g2d.setColor(passengerColor);
        boolean egress = false;
        for (TimedArc ta : assignment.getTimedPathForPassenger(p)) {
            Vehicle start = assignment.getVehicleForPassenger(p, ta, true);
            Vehicle end = assignment.getVehicleForPassenger(p, ta, false);
            int xStart = xOffset + ctx.computeXRoundOffset(ta.getFromTime());
            int xEnd = xOffset + ctx.computeXRoundOffset(ta.getToTime());
            if (start != null) {
                Bounds bounds = vehiclePositions.getOrDefault(start, passengerBounds);
                if (bounds != null) {
                    drawStart(g2d, bounds, ta, p, start, end, cfg, assignment,
                            xStart, xEnd, egress, vehicleColors, passengerColor);
                }
            }
            if (end != null) {
                Bounds bounds = vehiclePositions.getOrDefault(end, passengerBounds);
                if (bounds != null) {
                    drawEnd(g2d, bounds, ta, p, start, end, cfg, assignment, xStart, xEnd, vehicleColors, passengerColor);
                }
            }
            egress = assignment.willDisembarkAfter(p, ta);
        }
        g2d.setColor(old);
    }

    private static void drawStart(Graphics2D g2d, Bounds bounds, TimedArc ta, Passenger p, Vehicle start, Vehicle end,
                                  DrawConfig cfg, AssignedSolution assignment, int xStart, int xEnd, boolean egress,
                                  Map<Vehicle,Color> vehicleColors, Color passengerColor) {
        int xHalf = halfwayRound(xStart, xEnd);
        int xQ1 = (int)Math.round(xHalf - 0.5*cfg.getTimeUnitWidth());
        int dotSize = cfg.getDotSize();
        int yStart = bounds.topY
                + determineOffset(ta, p, start, ta.getFromTime(), cfg, assignment);
        g2d.fillRect(xStart, yStart, (xHalf-xStart), cfg.getPassengerHeight());
        if (start != end || egress) {
            // We have an outside transfer
            int yEnd = yStart + cfg.getPassengerHeight();
            Polygon poly = new Polygon();
            poly.addPoint(xHalf, yStart);
            poly.addPoint(xHalf, yEnd);
            poly.addPoint(xQ1, yEnd);
            poly.addPoint(xHalf, yStart);
            if (end != null) {
                // Inside transfer
                g2d.setColor(vehicleColors.get(end));
            }
            else if (p.getDestination().equals(ta.getToLocation())) {
                // Arrival at destination
                g2d.setColor(Color.BLACK);
            }
            else {
                // Outside transfer
                g2d.setColor(Color.WHITE);
            }
            g2d.fillPolygon(poly);
            g2d.setColor(passengerColor);
        }
    }

    private static void drawEnd(Graphics2D g2d, Bounds bounds, TimedArc ta, Passenger p, Vehicle start, Vehicle end,
                               DrawConfig cfg, AssignedSolution assignment, int xStart, int xEnd,
                                Map<Vehicle,Color> vehicleColors, Color passengerColor) {
        int xHalf = halfwayRound(xStart, xEnd);
        int xQ3 = (int)Math.round(xEnd - 0.5*cfg.getTimeUnitWidth());
        int dotSize = cfg.getDotSize();
        int yStart = bounds.topY
                + determineOffset(ta, p, end, ta.getToTime(), cfg, assignment);
        int yEnd = yStart + cfg.getPassengerHeight();
        g2d.fillRect(xHalf, yStart, (xEnd-xHalf), cfg.getPassengerHeight());
        if (start == null || start != end) {
            Polygon poly = new Polygon();
            poly.addPoint(xHalf, yStart);
            poly.addPoint(xEnd, yStart);
            poly.addPoint(xHalf, yEnd);
            poly.addPoint(xHalf, yStart);
            boolean draw = false;
            if (start == null && p.getOrigin().equals(ta.getFromLocation())) {
                g2d.setColor(Color.GRAY);
            }
            else if (start == null) {
                g2d.setColor(Color.WHITE);
            }
            else {
                g2d.setColor(vehicleColors.get(start));
                draw = true;
            }
            g2d.fillPolygon(poly);
            if (draw) {
                // Draws an outline in case of an inside transfer
                g2d.setColor(Color.BLACK);
                g2d.drawPolygon(poly);
            }
            g2d.setColor(passengerColor);
        }
    }

    private static int determineOffset(TimedArc ta, Passenger p, Vehicle v, double time,
                                DrawConfig cfg, AssignedSolution assignment) {
        if (v == null) {
            return 0;
        }
        return cfg.getPassengerHeight() * assignment.getPassengerIndex(ta, p, v, time, false);
    }

}
