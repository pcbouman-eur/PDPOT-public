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

import podrouting.data.*;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.visualizer.RoadIndexTable;
import podrouting.visualizer.TolColorScheme;

import java.awt.*;
import java.util.*;

import static podrouting.visualizer.draw.DrawUtils.halfway;

public class DrawContext {

    private Instance instance;
    private Solution solution;
    private AssignedSolution assignment;
    private RoadIndexTable roadTable;

    private DrawConfig cfg;

    private Map<Vehicle,Color> vehicleColors;
    private Map<Passenger,Color> passengerColors;
    private Map<Location,String> locationLabels;

    public DrawContext(AssignedSolution assignment, DrawConfig cfg) {
        this.assignment = assignment;
        this.solution = assignment.getSolution();
        this.instance = solution.getInstance();
        this.roadTable = new RoadIndexTable(solution);
        this.cfg = cfg;

        this.vehicleColors = TolColorScheme.getColorMap(instance.getVehicles());
        this.passengerColors = getPassengerColors(vehicleColors);
        this.locationLabels = DrawUtils.computeLocationLabels(instance);
    }

    public Instance getInstance() {
        return instance;
    }

    public Solution getSolution() {
        return solution;
    }

    public AssignedSolution getAssignment() {
        return assignment;
    }

    public RoadIndexTable getRoadTable() {
        return roadTable;
    }

    public DrawConfig getConfig() {
        return cfg;
    }

    public Map<Passenger,Color> getPassengerColors() {
        return Collections.unmodifiableMap(passengerColors);
    }

    public Map<Vehicle,Color> getVehicleColors() {
        return Collections.unmodifiableMap(vehicleColors);
    }

    public Map<Location,String> getLocationLabels() {
        return Collections.unmodifiableMap(locationLabels);
    }

    public void drawBox(Graphics2D g2d, Bounds bounds, Color color, int labelOffset, String label) {
        int xStart = bounds.leftX;
        int xEnd = bounds.rightX;
        int firstY = bounds.topY;
        int lastY = bounds.bottomY;

        double labelWidth = g2d.getFont().getStringBounds(label, g2d.getFontRenderContext()).getWidth();
        int margin = Math.min(cfg.getVehicleMargin(), (int) Math.ceil(labelWidth));

        g2d.drawLine(xStart, firstY, xEnd, firstY);
        g2d.drawLine(xStart, lastY, xEnd, lastY);

        if (color != null) {
            g2d.setColor(color);
            g2d.fillRect(cfg.getPadding(), firstY, margin, lastY - firstY);
        }
        g2d.setColor(Color.BLACK);
        int fontHeight = g2d.getFontMetrics().getHeight();
        int yPos = (int) Math.round(halfway(firstY, lastY) + 0.25 * fontHeight);
        g2d.drawString(label, labelOffset, yPos);
    }

    public double computeXOffset(double time) {
        return cfg.getPadding() + cfg.getVehicleMargin()
                + Math.max(0,Math.floor(time)-1) * cfg.getTimeUnitGutter()
                + time * cfg.getTimeUnitWidth();
    }

    public int computeXRoundOffset(double time) {
        return (int)Math.round(computeXOffset(time));
    }

    private Map<Passenger,Color> getPassengerColors(Map<Vehicle,Color> vehicleColors) {
        Map<Passenger,Color> result = new HashMap<>();
        for (Path<Passenger> path : assignment.getSolution().getPassengerPaths()) {
            Passenger p = path.getCommodity();
            Vehicle first = null;
            for (TimedArc ta : path.getPath()) {
                Vehicle v = assignment.getVehicleForPassenger(p,ta,true);
                if (v != null) {
                    first = v;
                    break;
                }
            }
            result.put(p, vehicleColors.getOrDefault(first, Color.LIGHT_GRAY));
        }
        return result;
    }

    public void debugLine(Graphics2D g2d, Color c, int x1, int y1, int x2, int y2) {
        if (cfg.isDebug()) {
            Color old = g2d.getColor();
            g2d.setColor(c);
            g2d.drawLine(x1, y1, x2, y2);
            g2d.setColor(old);
        }
    }

    public void debugRect(Graphics2D g2d, Color c, int x, int y, int width, int height) {
        if (cfg.isDebug()) {
            Color old = g2d.getColor();
            g2d.setColor(c);
            g2d.drawRect(x, y, width, height);
            g2d.setColor(old);
        }
    }

}
