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

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.Solution;
import podrouting.util.SolutionHelper;

public class DrawScaler {

    private final double defaultTranslate = 10;

    private final double translateX;
    private final double translateY;
    private final double scale;
    private final int shiftAmount = 7;

    public DrawScaler(Solution solution, int width, int height) {
        Instance instance = solution.getInstance();
        SolutionHelper helper = new SolutionHelper(solution);

        double minX, maxX, minY, maxY;
        minX = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        for (Location loc : instance.getLocations()) {
            if(loc.getName().equals("sink")) {
                continue;
            }
            minX = Math.min(minX, loc.getX());
            maxX = Math.max(maxX, loc.getX());
            minY = Math.min(minY, loc.getY());
            maxY = Math.max(maxY, loc.getY());
        }

        double baseTranslate = defaultTranslate;
        if (helper != null) {
            baseTranslate = defaultTranslate + helper.getMaxListLength() * shiftAmount;
        }

        double scaleX = (width - 2d * baseTranslate) / (maxX - minX);
        double scaleY = (height - 2d * baseTranslate) / (maxY - minY);
        this.scale = Math.min(scaleX, scaleY);

        double xSlack = width - 2d * baseTranslate - scale * (maxX-minX);
        double ySlack = height - 2d * baseTranslate - scale * (maxY-minY);
        this.translateX = xSlack/2 + baseTranslate - scale * minX;
        this.translateY = ySlack/2 + baseTranslate - scale * minY;
    }

    public int scaleXInt(double x) {
        return (int) Math.round(translateX + (scale * x));
    }

    public int scaleYInt(double y) {
        return (int) Math.round(translateY + (scale * y));
    }

}
