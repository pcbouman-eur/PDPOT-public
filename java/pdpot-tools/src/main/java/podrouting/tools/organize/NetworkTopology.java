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
package podrouting.tools.organize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Road;

import java.util.*;

/**
 * Simplify the network topology, ignoring location stops
 */
public class NetworkTopology {

    public static TopologyDifference computeDiff(Instance i1, Instance i2) {
        DirectedWeightedMultigraph<Location, Road> network1 = withParkingType(i1);
        DirectedWeightedMultigraph<Location, Road> network2 = withParkingType(i2);

        Set<Location> missingLocations = new LinkedHashSet<>(network2.vertexSet());
        missingLocations.removeAll(network1.vertexSet());
        Set<Location> addedLocations = new LinkedHashSet<>(network1.vertexSet());
        addedLocations.removeAll(network2.vertexSet());

        Set<Road> missingRoads = new LinkedHashSet<>(network2.edgeSet());
        missingRoads.removeAll(network1.edgeSet());
        Set<Road> addedRoads = new LinkedHashSet<>(network1.edgeSet());
        addedRoads.removeAll(network1.edgeSet());

        return new TopologyDifference(missingLocations, addedLocations, missingRoads, addedRoads);
    }

    public static DirectedWeightedMultigraph<Location,Road> withParkingType(Instance instance) {
        // The trick is to extract the network by setting all location types to the same
        DirectedWeightedMultigraph<Location, Road> result = new DirectedWeightedMultigraph<>(Road.class);
        for (Location loc : instance.getLocations()) {
            result.addVertex(loc.withType(LocationType.PARKING));
        }
        for (Road road : instance.getRoads()) {
            Location v = road.getOrigin().withType(LocationType.PARKING);
            Location w = road.getDestination().withType(LocationType.PARKING);
            Road newRoad = new Road(v, w, road.getDistance(), road.getName());
            result.addEdge(v, w, newRoad);
        }
        return result;
    }

    public static final class TopologyDifference {
        private final List<Location> missingLocations;
        private final List<Location> addedLocations;
        private final List<Road> missingRoads;
        private final List<Road> addedRoads;

        public TopologyDifference(Collection<Location> missingLocations, Collection<Location> addedLocations,
                                  Collection<Road> missingRoads, Collection<Road> addedRoads) {
            this.missingLocations = new ArrayList<>(missingLocations);
            this.addedLocations = new ArrayList<>(addedLocations);
            this.missingRoads = new ArrayList<>(missingRoads);
            this.addedRoads = new ArrayList<>(addedRoads);
        }

        @JsonIgnore
        public String getSummary() {
            return String.format(Locale.ROOT,
                    "Status: %s\nMissing Locations: %d\nMissing Roads   : %d\nAdded Locations :%d\nAdded Roads     :%d",
                    getStatus(), missingLocations.size(), missingRoads.size(), addedLocations.size(), addedRoads.size());
        }

        public String getStatus() {
            if (missingLocations.isEmpty() && addedLocations.isEmpty()
                    && missingRoads.isEmpty() && addedRoads.isEmpty()) {
                return "equal";
            }
            else if (missingLocations.isEmpty() && missingRoads.isEmpty()) {
                return "superset";
            }
            else if (addedLocations.isEmpty() && addedRoads.isEmpty()) {
                return "subset";
            }
            return "different";
        }

        public List<Location> getMissingLocations() {
            return Collections.unmodifiableList(missingLocations);
        }

        public List<Location> getAddedLocations() {
            return Collections.unmodifiableList(addedLocations);
        }

        public List<Road> getMissingRoads() {
            return Collections.unmodifiableList(missingRoads);
        }

        public List<Road> getAddedRoads() {
            return Collections.unmodifiableList(addedRoads);
        }
    }

}
