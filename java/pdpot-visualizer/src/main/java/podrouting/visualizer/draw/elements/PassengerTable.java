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
import podrouting.data.Passenger;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.visualizer.draw.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static podrouting.visualizer.draw.DrawUtils.halfwayRound;

public class PassengerTable implements DrawElement {

    @Override
    public int computeHeight(DrawContext ctx) {
        DrawConfig cfg = ctx.getConfig();
        Solution solution = ctx.getSolution();
        Instance instance = ctx.getInstance();
        if (cfg.isSkipRejected()) {
            int nonRejected = solution.getPassengerPaths()
                    .stream()
                    .filter(DrawUtils::isValid)
                    .mapToInt(i -> 1)
                    .sum();

            return nonRejected * cfg.getPassengerHeight() +
                    Math.max(0, nonRejected - 1) * cfg.getPassengerSpacing();

        }
        int numPassengers = instance.getPassengers().size();
        return numPassengers * cfg.getPassengerHeight() +
               Math.max(0, numPassengers - 1) * cfg.getPassengerSpacing();
    }

    @Override
    public void draw(Graphics2D g2d, DrawContext ctx, int xOffset, int yOffset) {
        AssignedSolution assignment = ctx.getAssignment();
        Map<Passenger,Color> passengerColors = ctx.getPassengerColors();
        Map<Vehicle,Color> vehicleColors = ctx.getVehicleColors();
        Map<Passenger, Bounds> passengerBounds = computePassengerRectangles(ctx, xOffset, yOffset);
        for (Path<Passenger> path : assignment.getSolution().getPassengerPaths()) {
            Passenger p = path.getCommodity();
            Color passengerColor = passengerColors.get(p);
            drawPassengerPathRow(g2d, ctx, xOffset, assignment, path, passengerBounds.get(p),
                    vehicleColors, passengerColor);
        }

        drawPassengerBoxes(g2d, ctx, xOffset, assignment, passengerBounds, passengerColors);
    }

    public void drawPassengerPathRow(Graphics2D g2d, DrawContext ctx, int xOffset,
                                     AssignedSolution assignment, Path<Passenger> path, Bounds passengerBounds,
                                     Map<Vehicle,Color> vehicleColors, Color passengerColor) {
        Color old = g2d.getColor();
        int yTop = passengerBounds.topY;
        int yBottom = passengerBounds.bottomY;
        int yHalf = halfwayRound(yTop, yBottom);
        int xMin = Integer.MAX_VALUE;
        int xMax = 0;
        boolean usesVehicle = false;
        Passenger p = path.getCommodity();
        for (TimedArc ta : assignment.getTimedPathForPassenger(p)) {
            Vehicle start = assignment.getVehicleForPassenger(p, ta, true);
            Vehicle end = assignment.getVehicleForPassenger(p, ta, false);
            int xStart = xOffset + ctx.computeXRoundOffset(ta.getFromTime());
            int xEnd = xOffset + ctx.computeXRoundOffset(ta.getToTime());
            int xHalf = halfwayRound(xStart, xEnd);

            boolean startAtOD = ta.getFromLocation().equals(p.getOrigin()) || ta.getFromLocation().equals(p.getDestination());
            boolean endAtOD = ta.getToLocation().equals(p.getOrigin()) || ta.getToLocation().equals(p.getDestination());
            drawPassengerPathElement(g2d, vehicleColors.get(start), xStart, xHalf, yTop, yHalf, yBottom-yTop, startAtOD);
            drawPassengerPathElement(g2d, vehicleColors.get(end), xHalf, xEnd, yTop, yHalf, yBottom-yTop, endAtOD);

            if (start != null) {
                xMin = Math.min(xMin, xStart);
                xMin = Math.min(xMin, xHalf);
                usesVehicle = true;
            }
            if (end != null) {
                xMax = Math.max(xMax, xEnd);
                usesVehicle = true;
            }
            if (ta.getPurpose() == ArcPurpose.WAIT_IN) {
                xMin = Math.min(xMin, xHalf);
                xMax = Math.max(xMax, xHalf);
            }
        }

        // TODO: this does currently not work for outside transfers...
        int xTimeStart = xOffset + ctx.computeXRoundOffset(p.getTimeStart());
        int xTimeEnd = xOffset + ctx.computeXRoundOffset(p.getTimeEnd());
        g2d.setColor(passengerColor);
        g2d.drawLine(xTimeStart, yTop, xTimeStart, yBottom);
        if (usesVehicle) {
            g2d.drawLine(xTimeStart, yHalf, xMin, yHalf);
            g2d.drawLine(xMax, yHalf, xTimeEnd, yHalf);
        }
        else {
            g2d.drawLine(xTimeStart, yHalf, xTimeEnd, yHalf);
        }
        g2d.drawLine(xTimeEnd, yTop, xTimeEnd, yBottom);

        g2d.setColor(old);
    }

    private static void drawPassengerPathElement(Graphics2D g2d, Color c, int xLeft, int xRight,
                                                 int yTop, int yHalf, int height, boolean atOD) {
        if (c != null) {
            g2d.setColor(c);
            g2d.fillRect(xLeft, yTop, xRight-xLeft, height);
            g2d.setColor(Color.BLACK);
        }
        else if (!atOD){
            g2d.setColor(Color.BLACK);
            g2d.drawLine(xLeft, yHalf, xRight, yHalf);
        }
    }

    public Map<Passenger, Rectangle> drawPassengerBoxes(Graphics2D g2d,
                                                        DrawContext ctx,
                                                        int xOffset,
                                                        AssignedSolution assignment,
                                                        Map<Passenger, Bounds> positions,
                                                        Map<Passenger,Color> colors) {
        List<Passenger> passengers = assignment.getSolution().getInstance().getPassengers();

        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        Map<Passenger,Rectangle> map = new HashMap<>();
        for (Passenger p : passengers) {
            Bounds bounds = positions.get(p);
            if (bounds != null) {
                ctx.drawBox(g2d, bounds, colors.get(p), xOffset,"Passenger " + p.getId());
            }
        }

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
        return map;
    }

    public Map<Passenger, Bounds> computePassengerRectangles(DrawContext ctx, int xOffset, int yOffset) {
        DrawConfig cfg = ctx.getConfig();
        Solution solution = ctx.getSolution();
        AssignedSolution assignment = ctx.getAssignment();
        Instance i = assignment.getSolution().getInstance();
        List<Passenger> passengers = new ArrayList<>(i.getPassengers());
        if (cfg.isSortPassengersByOd()) {
            passengers.sort(Comparator.comparing((Passenger p) -> p.getOrigin().getName())
                    .thenComparing((Passenger p) -> p.getDestination().getName()));
        }
        Map<Passenger, Path<Passenger>> pMap = solution.getPassengerPaths()
                .stream()
                .collect(Collectors.toMap(Path::getCommodity, p -> p));
        int numVehicles = i.getVehicles().size();
        int numSeats = i.getVehicles().stream().mapToInt(Vehicle::getCapacity).sum();

        int currentY = yOffset;

        Map<Passenger, Bounds> map = new HashMap<>();
        for (Passenger p : passengers) {
            if (cfg.isSkipRejected() && !DrawUtils.isValid(pMap.get(p))) {
                continue;
            }
            int xStart = xOffset + ctx.computeXRoundOffset(p.getTimeStart());
            int xEnd = xOffset + ctx.computeXRoundOffset(p.getTimeEnd());
            int firstY = currentY;
            int lastY = firstY + cfg.getPassengerHeight();
            map.put(p, new Bounds(xStart, xEnd, firstY, lastY));
            currentY = lastY + cfg.getPassengerSpacing();
        }
        return map;
    }

}
