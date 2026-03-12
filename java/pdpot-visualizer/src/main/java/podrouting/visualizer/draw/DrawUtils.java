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
import podrouting.data.Passenger;
import podrouting.data.timed.Path;

import java.util.*;

public final class DrawUtils {
    private DrawUtils() {}

    public static boolean isValid(Path<Passenger> path) {
        if (path == null) {
            return false;
        }
        Passenger p = path.getCommodity();
        return path.getFirst().getFromLocation().equals(p.getOrigin())
                && path.getLast().getToLocation().equals(p.getDestination());
    }

    public static Map<Location,String> computeLocationLabels(Instance instance) {
        List<Location> locations = new ArrayList<>(instance.getLocations());
        locations.sort(Comparator.comparing(Location::getName));
        Map<Location,String> result = new HashMap<>();
        for (Location loc : locations) {
            result.put(loc, ""+result.size());
        }
        return result;
    }

    public static double halfway(int i, int j) {
        int low = Math.min(i, j);
        int high = Math.max(i, j);
        return low + (high-low)*0.5;
    }

    public static int halfwayRound(int i, int j) {
        return (int) Math.round(halfway(i, j));
    }


}
