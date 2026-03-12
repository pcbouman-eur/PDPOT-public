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
package podrouting.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;
import podrouting.util.Pair;

public class Solution {

	private static final Logger log = LoggerFactory.getLogger(Solution.class);

	private final Instance instance;
	private final List<Path<Passenger>> passengerPaths;
	private final List<Path<Vehicle>> vehiclePaths;

	private double costs;

	private final Map<String,Object> metadata;

	@JsonCreator
	public Solution(@JsonProperty("instance") Instance instance,
			@JsonProperty("passengerPaths") List<Path<Passenger>> passengerPaths,
			@JsonProperty("vehiclePaths") List<Path<Vehicle>> vehiclePaths,
			@JsonProperty("costs") double costs,
			@JsonProperty("metadata") Map<String,Object> metadata) {
		this(instance,passengerPaths,vehiclePaths);
		if (Math.abs(costs - this.costs) > 10e-12) {
			throw new IllegalArgumentException("The provided costs do not match the instance");
		}
		if (metadata != null) {
			this.metadata.putAll(metadata);
		}
	}

	public Solution(Instance instance, List<Path<Passenger>> passengerPaths, List<Path<Vehicle>> vehiclePaths) {
		this.instance = instance;
		this.passengerPaths = new ArrayList<>(passengerPaths);
		this.vehiclePaths = new ArrayList<>(vehiclePaths);
		this.metadata = new TreeMap<>();

		int emptyPath = 0;
		Set<Passenger> passengerCheck = new HashSet<>();
		Set<Vehicle> vehicleCheck = new HashSet<>();
		this.costs = 0;
		for (Path<Passenger> path : passengerPaths) {
			Passenger p = path.getCommodity();
			if (passengerCheck.contains(path.getCommodity())) {
				throw new IllegalArgumentException("Two paths were defined for passenger " + p);
			}
			passengerCheck.add(p);
			this.costs += instance.computePassengerPathCost(path);
			if (path.getPath().isEmpty() && !p.getOrigin().equals(p.getDestination())) {
				emptyPath++;
			}
		}
		if (!instance.getPassengers().containsAll(passengerCheck)) {
			throw new IllegalArgumentException("This solution contains passengers unknown to the instance");
		}
		int rejected = instance.getPassengers().size() - passengerCheck.size() + emptyPath;
		this.costs += instance.getRejectionPenalty() * rejected;

		Map<TimedArc,Integer> arcCounts = new HashMap<>();
		for (Path<Vehicle> path : vehiclePaths) {
			Vehicle v = path.getCommodity();
			if (vehicleCheck.contains(v)) {
				throw new IllegalArgumentException("Two paths were defined for vehicle " + v);
			}
			vehicleCheck.add(v);
			this.costs += instance.computeVehiclePathCost(path);
			path.getPath().forEach(arc -> arcCounts.merge(arc, 1, Integer::sum));
		}
		if (!instance.getVehicles().containsAll(vehicleCheck) /*|| !vehicleCheck.containsAll(instance.getVehicles())*/) {
			throw new IllegalArgumentException(
					"The set of vehicles in the instance and the vehicles for which a path is defined are not equal");
		}
		for (Entry<TimedArc,Integer> e : arcCounts.entrySet()) {
			this.costs -= instance.getPlatooningDiscount() * (e.getValue()-1) * e.getKey().getDistance();
		}
	}

	public static Solution removeSink(Solution other) {
		//Change sink to last visited location
		List<Path<Vehicle>> vehiclePaths = new ArrayList<>();
		List<Vehicle> vehicles = new ArrayList<>();
		for(Path<Vehicle> path: other.getVehiclePaths()) {
			Location lastLocation = null;
			List<TimedArc> timedArcs = new ArrayList<>();
			boolean hasSink = false;
			for(TimedArc ta: path.getPath()) {
				if(lastLocation==null && ta.getToLocation().getName().equals("sink")) {
					lastLocation = ta.getFromLocation();
					hasSink = true;
				}
				if(lastLocation!=null) {
					ta = new TimedArc(new TimedLocation(lastLocation, ta.getFromTime()), new TimedLocation(lastLocation, ta.getToTime()), ArcPurpose.WAIT_OUT, 0d, null);
				}
				timedArcs.add(ta);
			}
			Vehicle oldVehicle = path.getCommodity();
			if(lastLocation==null) {
				lastLocation = oldVehicle.getDestination();
			}
			
			//Because of the sink, there are too many WAIT_IN arcs at the end of the path, change these to WAIT_OUT.
			if(hasSink) {
				Collections.reverse(timedArcs);
				
				TimedArc prev = null;
				for(int i = 0; i < timedArcs.size(); i++) {
					TimedArc ta = timedArcs.get(i);
					if(!ta.getFromLocation().equals(lastLocation) || ta.getPurpose()==ArcPurpose.DRIVE) {
						if(prev!=null) { //There should always be at least 1 wait_in arc for the passenger
							timedArcs.remove(i-1);
							prev = prev.toWaitInside();
							timedArcs.add(i-1, prev);
						}
						break;
					}
					timedArcs.remove(i);
					ta = ta.toWaitOutside();
					prev = ta;
					timedArcs.add(i, ta);
				}
				
				Collections.reverse(timedArcs);
			}
			
			Vehicle newVehicle = new Vehicle(oldVehicle.getRange(), oldVehicle.getId(), oldVehicle.getCapacity(), oldVehicle.getOrigin(), lastLocation, oldVehicle.isRepeatable(), oldVehicle.getVehicleCost());
			vehiclePaths.add(new Path<>(newVehicle, timedArcs));
			vehicles.add(newVehicle);
		}
		Instance instance = Instance.removeSink(other.getInstance(), vehicles);
		
		Solution result = new Solution(instance, other.getPassengerPaths(), vehiclePaths);
		
		//Add metadata again
		for(Entry<String, Object> entry: other.getMetadata().entrySet()) {
			result.addMetaData(entry.getKey(), entry.getValue());
		}
		return result;
	}

	public Instance getInstance() {
		return instance;
	}

	public List<Path<Passenger>> getPassengerPaths() {
		return passengerPaths != null ? Collections.unmodifiableList(passengerPaths) : Collections.emptyList();
	}

	public List<Path<Vehicle>> getVehiclePaths() {
		return vehiclePaths != null ? Collections.unmodifiableList(vehiclePaths) : Collections.emptyList();
	}

	public double getCosts() {
		return costs;
	}

	public void writeToFile(File f) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.writeValue(f, this);
	}

	public void addMetaData(String key, Object data) {
		metadata.put(key, data);
	}

	public Map<String,Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	public static Solution readFromFile(File f) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(f, Solution.class);
	}

	public static boolean checkVehicleFeasbility(Path<Vehicle> path, Instance instance) {
		return checkVehicleFeasbility(path, instance, null, null, null);
	}

	private static boolean checkVehicleFeasbility(Path<Vehicle> p,
			Instance instance,
			Map<TimedArc,Integer> capacity,
			Map<Pair<TimedArc,TimedArc>,Integer> pairCapacity,
			Map<Pair<TimedArc,TimedArc>,Integer> egressCapacity) {
		Vehicle v = p.getCommodity();
		TimedArc prev = null;
		for (TimedArc ta : p.getPath()) {
			if (prev == null && !ta.getFromLocation().equals(v.getOrigin())) {
				log.info("At the start of its path, a vehicle is not at its origin depot.");
				return false;
			}
			if (ta.getPurpose() == ArcPurpose.WAIT_IN || ta.getPurpose() == ArcPurpose.WAIT_OUT) {
				if (!ta.getFromLocation().equals(ta.getToLocation())) {
					log.info("A vehicle waiting arc can not result in teleportation.");
					return false;
				}
			}
			if (capacity != null && (ta.getPurpose() == ArcPurpose.DRIVE || ta.getPurpose() == ArcPurpose.WAIT_IN)) {
				capacity.merge(ta, v.getCapacity(), Integer::sum);
			}
			if (instance.isStrictStopping() && prev != null && prev.getPurpose() == ArcPurpose.WAIT_IN && ta.getPurpose() == ArcPurpose.WAIT_IN && ta.getFromLocation().getType()!= LocationType.PARKING) {
				log.info("A vehicle can not stop at a non-parking location for more than one time step.");
				return false;
			}
			if (prev != null) {
				if (prev.getToTime() != ta.getFromTime() || !prev.getToLocation().equals(ta.getFromLocation())) {
					log.info("For two consecutive vehicle arcs, either the arrival/departure times or the arrival/departure locations do not match.");
					return false;
				}
			}

			if (pairCapacity != null && capacitatedPair(prev,ta)) {
				Pair<TimedArc,TimedArc> pair = Pair.of(prev, ta);
				pairCapacity.merge(pair, v.getCapacity(), Integer::sum);
			}

			if (egressCapacity != null && egressPair(prev,ta,true)) {
				TimedArc snd = new TimedArc(ta.getFrom(),ta.getTo(),ArcPurpose.WAIT_OUT,ta.getDistance());
				Pair<TimedArc,TimedArc> pair = Pair.of(prev, snd);
				egressCapacity.merge(pair, v.getCapacity(), Integer::sum);
			}

			prev = ta;
		}
		if (prev != null && !prev.getToLocation().equals(v.getDestination())) {
			log.info("At the end of its path, a vehicle is not at its destination depot.");
			return false;
		}
		return true;
	}

	public static boolean checkPassengerFeasibility(Path<Passenger> path, Instance instance) {
		return checkPassengerFeasbility(path, instance, null, null, null);
	}

	private static boolean checkPassengerFeasbility(Path<Passenger> path,
			Instance instance,
			Map<TimedArc,Integer> capacity,
			Map<Pair<TimedArc,TimedArc>,Integer> pairCapacity,
			Map<Pair<TimedArc,TimedArc>,Integer> egressCapacity) {

		Passenger p = path.getCommodity();
		TimedArc prev = null;

		if (!instance.getPassengers().contains(p)) {
			log.info("The solution contains a passenger that is unknown to the instance");
			return false;
		}

		for (TimedArc ta : path.getPath()) {
			if (prev == null && !ta.getFromLocation().equals(p.getOrigin())) {
				log.info("At the start of its path, passenger {} is not at its origin location.", p.getId());
				return false;
			}
			if (ta.getPurpose() == ArcPurpose.WAIT_IN || ta.getPurpose() == ArcPurpose.DRIVE) {
				if (ta.getFromTime() < p.getTimeStart() || ta.getToTime() > p.getTimeEnd()) {
					log.info("A passenger's driving arc or wait inside arc violated the passenger's time window. "
							+ "Arc from {}, Arc until {}, passenger from {}, passenger until {}",
							ta.getFromTime(), ta.getToTime(), p.getTimeStart(), p.getTimeEnd());
					return false;
				}
			}
			if (ta.getPurpose() == ArcPurpose.WAIT_IN || ta.getPurpose() == ArcPurpose.WAIT_OUT) {
				if (!ta.getFromLocation().equals(ta.getToLocation())) {
					log.info("A passenger's waiting arc can not result in teleportation.");
					return false;
				}
			}
			if  (capacity != null && (ta.getPurpose() == ArcPurpose.DRIVE || ta.getPurpose() == ArcPurpose.WAIT_IN)) {
				if (capacity.getOrDefault(ta, 0) < 1) {
					log.info("The capacity required for a passenger's driving arc or wait inside arc was violated.");
					return false;
				}
				capacity.merge(ta, -1, Integer::sum);
			}
			if (prev != null) {
				if (prev.getToTime() != ta.getFromTime() || !prev.getToLocation().equals(ta.getFromLocation())) {
					log.info("For two consecutive passenger arcs, either the arrival/departure times or the arrival/departure locations do not match.");
					return false;
				}
			}

			if (prev != null
					&& prev.getPurpose() == ArcPurpose.WAIT_OUT
					&& ta.getPurpose() == ArcPurpose.DRIVE) {
				log.info("A passenger performs a DRIVE arc immediately after a WAIT OUT arc");
				return false;
			}

			Pair<TimedArc,TimedArc> pair = Pair.of(prev, ta);
			if (pairCapacity != null && capacitatedPair(prev,ta)) {
				if (pairCapacity.getOrDefault(pair,0) < 1) {
					log.info("The capacity required for a passenger's consecutive arc pair was violated.");
					return false;
				}
				pairCapacity.merge(pair, -1, Integer::sum);
			}

			if (egressCapacity != null && egressPair(prev,ta,false)) {
				if (egressCapacity.getOrDefault(pair, 0) < 1) {
					log.info("The capacity required for a passenger's egress action was violated.");
					return false;
				}
				egressCapacity.merge(pair, -1, Integer::sum);
			}

			prev = ta;
		}
		if (prev != null && !prev.getToLocation().equals(p.getDestination())) {
			log.info("At the end of its path, a passenger is not at its destination location.");
			return false;
		}
		return true;
	}

	@JsonIgnore
	public boolean checkFeasibility() {
		// The checks for duplicate paths for passengers/vehicles are done in the constructor
		Map<TimedArc,Integer> capacity = new LinkedHashMap<>();
		Map<Pair<TimedArc,TimedArc>,Integer> pairCapacity = new LinkedHashMap<>();
		Map<Pair<TimedArc,TimedArc>,Integer> egressCapacity = new LinkedHashMap<>();
		Set<Vehicle> vehicles = new HashSet<>();
		for (Path<Vehicle> p : vehiclePaths) {
			if (!checkVehicleFeasbility(p, instance, capacity, pairCapacity, egressCapacity)) {
				return false;
			}
			if (!vehicles.add(p.getCommodity())) {
				log.info("The solution contains multiple paths for vehicle {}", p.getCommodity().getId());
				return false;
			}
		}

		Set<Passenger> passengers = new HashSet<>();
		for (Path<Passenger> path : passengerPaths) {
			if (!checkPassengerFeasbility(path, instance, capacity, pairCapacity, egressCapacity)) {
				return false;
			}
			if (!passengers.add(path.getCommodity())) {
				log.info("The solution contains multiple paths for passenger {}", path.getCommodity().getId());
				return false;
			}
		}

		return true;
	}

	private static boolean capacitatedPair(TimedArc prev, TimedArc cur) {
		if (prev == null || cur == null) {
			return false;
		}
		return (prev.getPurpose() == ArcPurpose.DRIVE || prev.getPurpose() == ArcPurpose.WAIT_IN)
				&& (cur.getPurpose() == ArcPurpose.DRIVE || cur.getPurpose() == ArcPurpose.WAIT_IN);
	}

	private static boolean egressPair(TimedArc prev, TimedArc cur, boolean vehicle) {
		if (prev == null || cur == null) {
			return false;
		}
		if (vehicle) {
			return prev.getPurpose() == ArcPurpose.DRIVE && cur.getPurpose() == ArcPurpose.WAIT_IN;
		}
		else {
			return prev.getPurpose() == ArcPurpose.DRIVE && cur.getPurpose() == ArcPurpose.WAIT_OUT;
		}
	}

}
