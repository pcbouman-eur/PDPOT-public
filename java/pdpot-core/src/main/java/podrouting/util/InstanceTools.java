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
package podrouting.util;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.graph.GraphWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.*;
import podrouting.data.timed.TimedNetwork;

import java.util.*;

public class InstanceTools {
    private static final Logger log = LoggerFactory.getLogger(InstanceTools.class);

    private final Instance instance;
    private final ShortestPathAlgorithm<Location, Road> roundedPaths;
    private final ShortestPathAlgorithm<Location, Road> paths;

    private final TimedNetwork timedNetwork;

    public InstanceTools(Instance instance) {
        this.instance = instance;
        this.roundedPaths = instance.getRoundedUpShortestPaths();
        this.paths = instance.shortestPaths();

        this.timedNetwork = new TimedNetwork(instance);
    }

    private static <V, E> GraphWalk<V, E> toWalk(GraphPath<V, E> path) {
        if (path instanceof GraphWalk) {
            return (GraphWalk<V, E>) path;
        }
        return new GraphWalk<>(path.getGraph(), path.getStartVertex(), path.getEndVertex(),
                path.getVertexList(), path.getEdgeList(), path.getWeight());
    }

    public static <V, E> GraphPath<V, E> concat(GraphPath<V, E> p1, GraphPath<V, E> p2) {
        return toWalk(p1).concat(toWalk(p2), GraphPath::getWeight);
    }

    public static int roundedPathLength(GraphPath<Location, Road> path) {
        int result = 0;
        for (Road r : path.getEdgeList()) {
            result += r.getRoundedDistance();
        }
        return result;
    }

    /**
     * Searches for the shortest path that starts at a vehicle, then transports the given passenger
     * over the shortest path
     *
     * @param passenger the passenger to transport
     * @param rounded   whether the rounded distance network should be used to compute the shortest path
     */
    public GraphPath<Location, Road> shortestVehiclePassengerPath(Passenger passenger, boolean rounded) {
        ShortestPathAlgorithm<Location, Road> algo = rounded ? roundedPaths : paths;
        Location origin = passenger.getOrigin();
        Set<Location> processed = new HashSet<>();
        GraphPath<Location, Road> firstPart = null;
        for (Vehicle v : instance.getVehicles()) {
            Location vehicleSource = v.getOrigin();
            if (!processed.contains(vehicleSource)) {
                GraphPath<Location, Road> candidate = algo.getPath(vehicleSource, origin);
                processed.add(vehicleSource);
                if (candidate == null) {
                    continue;
                }
                if (firstPart == null || candidate.getWeight() < firstPart.getWeight()) {
                    firstPart = candidate;
                }
            }
        }

        if (firstPart == null) {
            return null;
        }

        GraphPath<Location, Road> secondPart = algo.getPath(origin, passenger.getDestination());
        if (secondPart == null) {
            return null;
        }

        return concat(firstPart, secondPart);
    }


    public boolean checkPassengersFeasible(boolean rounded) {
        Map<Passenger, TimedNetwork> subnetworks = Preprocessing.getPassengerSubnetworks(timedNetwork);
        for (Passenger p : instance.getPassengers()) {
            double pathLength = shortestVehiclePassengerPath(p, rounded).getWeight();
            if (pathLength > p.getTimeEnd() - p.getTimeStart()) {
                log.info("Passenger {} infeasible due to shortest vehicle path too long", p.getId());
                return false;
            }
            if (subnetworks.get(p) == null || subnetworks.get(p).getTimedLocations().isEmpty()) {
                log.info("Passenger {} infeasible due to empty subnetwork after preprocessing", p.getId());
                return false;
            }
        }
        return true;
    }

    public boolean checkVehiclesFeasible(boolean rounded) {
        Map<Vehicle, TimedNetwork> subnetworks = Preprocessing.getVehicleSubnetworks(timedNetwork);
        for (Vehicle v : instance.getVehicles()) {
            double weight = rounded
                    ? roundedPaths.getPathWeight(v.getOrigin(), v.getDestination())
                    : paths.getPathWeight(v.getOrigin(), v.getDestination());
            if (weight > instance.getMaximumTime() - instance.getMinimumTime()) {
                log.info("Vehicle {} infeasible due to shortest vehicle path too long", v.getId());
                return false;
            }
            if (subnetworks.get(v) == null || subnetworks.get(v).getTimedLocations().isEmpty()) {
                log.info("Vehicle {} infeasible due to empty subnetwork after preprocessing", v.getId());
                return false;
            }
        }
        return true;
    }

    public static boolean checkFeasibility(Instance instance, boolean rounded) {
        InstanceTools tools = new InstanceTools(instance);
        return tools.checkPassengersFeasible(rounded) && tools.checkVehiclesFeasible(rounded);
    }

}