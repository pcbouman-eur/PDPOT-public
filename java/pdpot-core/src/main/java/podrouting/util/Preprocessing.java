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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jgrapht.GraphPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.Passenger;
import podrouting.data.Road;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;
import podrouting.data.timed.TimedNetwork;

public class Preprocessing {

	private static final Logger log = LoggerFactory.getLogger(Preprocessing.class);

	private final Instance instance;
	private final TimedNetwork network;

	private final int min;
    private final int max;
	
	private final Map<Location,Integer> earliestDepature;
    private final Map<Location,Integer> latestDeparture;
    private final Map<Location,Integer> earliestArrival;
    private final Map<Location,Integer> latestArrival;
	
	public Preprocessing(TimedNetwork nw) {
		this.network = nw;
		this.instance = nw.getInstance();
		min = instance.getMinimumTime();
		max = instance.getMaximumTime();
		earliestArrival = new HashMap<>();
		earliestDepature = new HashMap<>();
		latestArrival = new HashMap<>();
		latestDeparture = new HashMap<>();
	}
	
	public void resetBounds() {
		earliestDepature.clear();
		latestDeparture.clear();
		earliestArrival.clear();
		latestArrival.clear();
	}

	public void initBounds(Passenger p) {
		GraphPath<Location, Road> path;
		path = instance.shortestPath(p.getOrigin(), p.getDestination());
		int pathLength = InstanceTools.roundedPathLength(path);
		if (pathLength > p.getTimeEnd() - p.getTimeStart()) {
			// Skipping this passenger as there is no feasible route
			log.warn("Skipping bound initialization for passenger with id {}, as there is no feasible route",
					p.getId());
			return;
		}
		earliestArrival.merge(p.getDestination(), p.getTimeStart()+pathLength, Math::min);
		earliestDepature.merge(p.getOrigin(), p.getTimeStart(), Math::min);
		latestDeparture.merge(p.getOrigin(), p.getTimeEnd()-pathLength, Math::max);
		latestArrival.merge(p.getDestination(), p.getTimeEnd(), Math::max);
	}
	
	public void initBounds(Vehicle v) {
		GraphPath<Location, Road> path;
		path = instance.shortestPath(v.getOrigin(), v.getDestination());
		//int pathLength = (int)Math.ceil(path.getWeight());
		int pathLength = InstanceTools.roundedPathLength(path);
		if (pathLength > instance.getMaximumTime() - instance.getMinimumTime()) {
			log.warn("Skipping bound initialization for vehicle with id {}, as there is not feasible route",
					v.getId());
			return;
		}
		earliestArrival.merge(v.getDestination(), min+pathLength, Math::min);
		earliestDepature.merge(v.getOrigin(), min, Math::min);
		latestArrival.merge(v.getDestination(), max, Math::max);
		latestDeparture.merge(v.getOrigin(), max-pathLength, Math::max);		
	}

	public void initializeAll() {
		instance.getVehicles().forEach(this::initBounds);
		instance.getPassengers().forEach(this::initBounds);
	}
		
	private List<TimedLocation> findSources() {
		return findInRange(earliestDepature,latestDeparture);
	}

	private List<TimedLocation> findSinks() {
		return findInRange(earliestArrival,latestArrival);
	}
	
	private List<TimedLocation> findInRange(Map<Location,Integer> lb, Map<Location,Integer> ub) {
		List<TimedLocation> result = new ArrayList<>();
		for (TimedLocation tl : network.getTimedLocations()) {
			Location loc = tl.getLocation();
			int time = tl.getTime();
			if (!lb.containsKey(loc) && !ub.containsKey(loc)) {
				continue;
			}
			if (lb.getOrDefault(loc, min) <= time && time <= ub.getOrDefault(loc, max)) {
				result.add(tl);
			}
		}
		return result;
	}

	private Set<TimedLocation> forwardSearch(Collection<TimedLocation> origins) {
		// Run the reachable method with a neighborhood function that returns
		// the set of neighbours that can be reached via outgoing arcs
		return reachable(origins, tl -> network.getOutgoingTimedArcs(tl)
				                               .stream()
				                               .map(TimedArc::getTo)
				                               .collect(Collectors.toSet()));
	}

	private Set<TimedLocation> backwardSearch(Collection<TimedLocation> destinations) {
		// Run the reachable method with a neighbourhood function that returns
		// the set of neighbours that can be reached via incoming arcs
		return reachable(destinations, tl -> network.getIncomingTimedArcs(tl)
					                                .stream()
					                                .map(TimedArc::getFrom)
					                                .collect(Collectors.toSet()));
	}

	
	private Set<TimedLocation> reachable(Collection<TimedLocation> origins, Function<TimedLocation,Set<TimedLocation>> f) {
		Set<TimedLocation> result = new HashSet<>(origins);
		LinkedList<TimedLocation> queue = new LinkedList<>(origins);
		while (!queue.isEmpty()) {
			TimedLocation tl = queue.pop();
			for (TimedLocation nb : f.apply(tl)) {
				if (!result.contains(nb)) {
					result.add(nb);
					queue.add(nb);
				}
			}
		}
		return result;
	}
	
	public Set<TimedLocation> getReachable() {
		List<TimedLocation> sources = findSources();
		List<TimedLocation> sinks = findSinks();
		Set<TimedLocation> result = new HashSet<>(forwardSearch(sources));
		result.retainAll(backwardSearch(sinks));
		return result;
	}
	
	public Set<TimedLocation> getNonReachable() {
		Set<TimedLocation> result = new HashSet<>(network.getTimedLocations());
		result.removeAll(getReachable());
		return result;
	}
	
	public TimedNetwork getSubnetwork() {
		return network.filter(getNonReachable());
	}
	
	public static Map<Passenger,TimedNetwork> getPassengerSubnetworks(TimedNetwork tn) {
		Preprocessing pre = new Preprocessing(tn);
		Map<Passenger,TimedNetwork> result = new LinkedHashMap<>();
		for (Passenger p : tn.getInstance().getPassengers()) {
			pre.resetBounds();
			pre.initBounds(p);
			TimedNetwork sn = pre.getSubnetwork();
			result.put(p, sn);
			if (sn.getTimedLocations().isEmpty()) {
				log.info("Passenger with id {} has an empty timed subnetwork after preprocessing.", p.getId());
				// Let's do it again for debugging...
				pre.getSubnetwork();
			}
		}
		return result;
	}
	
	public static Map<Vehicle,TimedNetwork> getVehicleSubnetworks(TimedNetwork tn) {
		Preprocessing pre = new Preprocessing(tn);
		Map<Vehicle,TimedNetwork> result = new LinkedHashMap<>();
		for (Vehicle v : tn.getInstance().getVehicles()) {
			pre.resetBounds();
			pre.initBounds(v);
			TimedNetwork subNetwork = pre.getSubnetwork();
			// Test if this makes the objective worse or not
			// Comment this line if this breaks stuff
			subNetwork = subNetwork.filterArcs(arc -> arc.getPurpose()== ArcPurpose.WAIT_OUT);
			result.put(v, subNetwork);
		}
		return result;		
	}

	public static Map<Vehicle,PathFinder> getVehiclePathFinders(TimedNetwork tn) {
		Instance i = tn.getInstance();
		Map<Vehicle,TimedNetwork> subnets = getVehicleSubnetworks(tn);
		Map<Vehicle,PathFinder> result = new LinkedHashMap<>();
		for (Entry<Vehicle,TimedNetwork> e : subnets.entrySet()) {
			Vehicle v = e.getKey();
			TimedNetwork subnet = e.getValue();
			TimedLocation from = new TimedLocation(v.getOrigin(),i.getMinimumTime());
			TimedLocation to = new TimedLocation(v.getDestination(), i.getMaximumTime());
			PathFinder pathfinder = new PathFinder(subnet, from, to);
			pathfinder.setDefaultVehicleArcCosts(v);
			result.put(v, pathfinder);
		}
		return result;
	}
	
	public static Map<Passenger,PathFinder> getPassengerPathFinders(TimedNetwork tn) {
		Instance i = tn.getInstance();
		Map<Passenger,TimedNetwork> subnets = getPassengerSubnetworks(tn);
		Map<Passenger,PathFinder> result = new LinkedHashMap<>();
		for (Entry<Passenger,TimedNetwork> e : subnets.entrySet()) {
			Passenger p = e.getKey();
			TimedNetwork subnet = e.getValue();
			TimedLocation from = new TimedLocation(p.getOrigin(),p.getTimeStart());
			TimedLocation to = new TimedLocation(p.getDestination(), p.getTimeEnd());
			if (!subnet.getTimedLocations().isEmpty()) {
				PathFinder pathfinder = new PathFinder(subnet, from, to);
				pathfinder.setDefaultArcCosts(p);
				result.put(p, pathfinder);
			}
			else {
				log.info("Skip initialization for passenger {} since there are no feasible routes", p.getId());
			}
		}
		return result;
	}
	
}
