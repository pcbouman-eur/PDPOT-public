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
package podrouting.data.assign;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Passenger;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.util.Pair;
import podrouting.util.SolutionHelper;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * This class models a solution that also has a vehicle assigment
 */
public class AssignedSolution {

	private static final Logger log = LoggerFactory.getLogger(AssignedSolution.class);

	private final Solution solution;
	private final SolutionHelper solutionHelper;

	private final Map<Passenger,Path<Passenger>> passengerPaths;
	private final Map<Vehicle,Path<Vehicle>> vehiclePaths;

	private final Map<Passenger, Map<TimedArc, Vehicle>> passengerStartAssignment;
	private final Map<Passenger, Map<TimedArc,Vehicle>> passengerEndAssignment;
	private final Map<Vehicle, Map<TimedArc,Set<Passenger>>> vehicleStartAssignment;
	private final Map<Vehicle, Map<TimedArc,Set<Passenger>>> vehicleEndAssignment;

	private boolean seatAssignmentComputed;
	private final Map<Vehicle,SeatAssignment> seatAssignments;

	private final Map<TimedArc, Map<Vehicle,Integer>> vehiclesPerArc;
	private final Map<TimedArc, Map<Passenger,Integer>> passengersPerArc;

	private final Map<Passenger, TreeMap<Double,TimedArc>> passengerTimeMap;
	private final Map<Vehicle, TreeMap<Double,TimedArc>> vehicleTimeMap;

	private final Map<Passenger, Set<TimedArc>> egressArcs;

	private final Map<Passenger, Map<TimedArc, TimedArc>> nextArcMap;

	public AssignedSolution(Solution solution) {
		this.solution = solution;
		this.solutionHelper = new SolutionHelper(solution, true, true, true);
		this.passengerStartAssignment = new HashMap<>();
		this.passengerEndAssignment = new HashMap<>();
		this.vehicleStartAssignment = new HashMap<>();
		this.vehicleEndAssignment = new HashMap<>();
		this.passengerPaths = new HashMap<>();
		this.vehiclePaths = new HashMap<>();
		this.passengerTimeMap = new HashMap<>();
		this.vehicleTimeMap = new HashMap<>();
		this.vehiclesPerArc = new HashMap<>();
		this.passengersPerArc = new HashMap<>();
		this.egressArcs = new HashMap<>();
		this.seatAssignments = new HashMap<>();
		this.nextArcMap = new HashMap<>();

		for (Path<Vehicle> path : solution.getVehiclePaths()) {
			Vehicle v = path.getCommodity();
			List<TimedArc> helperPath = solutionHelper.getPath(v);
			vehiclePaths.put(v, new Path<>(v, helperPath));
			TreeMap<Double,TimedArc> map = new TreeMap<>();
			for (TimedArc timedArc : helperPath) {
				map.put(1d*timedArc.getFromTime(), timedArc);
				if (!vehiclesPerArc.containsKey(timedArc)) {
					vehiclesPerArc.put(timedArc, new HashMap<>());
				}
				Map<Vehicle,Integer> indexMap = vehiclesPerArc.get(timedArc);
				indexMap.put(v, indexMap.size());
			}
			vehicleTimeMap.put(v, map);
		}

		for (Path<Passenger> path : solution.getPassengerPaths()) {
			Map<TimedArc,TimedArc> nextArc = new HashMap<>();
			Passenger p = path.getCommodity();
			List<TimedArc> helperPath = solutionHelper.getPath(p);
			passengerPaths.put(p, new Path<>(p, helperPath));
			TreeMap<Double,TimedArc> map = new TreeMap<>();
			TimedArc prev = null;
			Set<TimedArc> egressSet = new HashSet<>();
			for (TimedArc timedArc: helperPath) {
				map.put(1d*timedArc.getFromTime(), timedArc);
				if (!passengersPerArc.containsKey(timedArc)) {
					passengersPerArc.put(timedArc, new HashMap<>());
				}
				Map<Passenger,Integer> indexMap = passengersPerArc.get(timedArc);
				indexMap.put(p, indexMap.size());
				if (prev != null) {
					nextArc.put(prev, timedArc);
				}
				prev = timedArc;
			}
			nextArcMap.put(p, nextArc);
			passengerTimeMap.put(p, map);
			egressArcs.put(p, egressSet);
		}

	}

	@JsonCreator
	public static AssignedSolution createFromPairMap(@JsonProperty("solution") Solution solution,
				@JsonProperty("assignment") Map<Vehicle, Map<Pair<TimedArc, Boolean>, List<Passenger>>> assignment) {
		AssignedSolution result = new AssignedSolution(solution);
		result.addAssignments(assignment);
		return result;
	}

	public static AssignedSolution createFromMap(Solution solution, Map<Vehicle,Map<TimedArc,List<Passenger>>> map, Map<Passenger, List<TimedArc>> outsideTransferMap) {
		AssignedSolution result = new AssignedSolution(solution);
		result.addAssignmentsFromMap(map, outsideTransferMap);
		return result;
	}

	private List<PassengerState> getPassengerStates(Path<Passenger> path) {
		List<PassengerState> result = new ArrayList<>();
		Passenger p = path.getCommodity();
		for (TimedArc ta : path.getPath()) {
			Vehicle start = getVehicleForPassenger(p,ta, true);
			Vehicle end = getVehicleForPassenger(p,ta, false);
			int from = ta.getFromTime();
			int to = ta.getToTime();
			double halfWay = from + 0.5*(to-from);
			for (int i=from; i < to; i++) {
				PassengerState state;
				if (i <= halfWay && i+1 > halfWay) {
					state = PassengerState.create(p, ta, i, start, end);
				}
				else if (i > halfWay) {
					state = PassengerState.create(p, ta, i, end, end);
				}
				else {
					state = PassengerState.create(p, ta, i, start, start);
				}
				result.add(state);
			}
		}
		return result;
	}

	private List<VehicleState> getVehicleStates(Path<Vehicle> path) {
		List<VehicleState> result = new ArrayList<>();
		Vehicle v = path.getCommodity();
		for (TimedArc ta : path.getPath()) {
			Set<Passenger> start = getPassengerSet(v, ta, true);
			Set<Passenger> end = getPassengerSet(v, ta, false);
			int from = ta.getFromTime();
			int to = ta.getToTime();
			double halfWay = from + 0.5*(to-from);
			for (int i=from; i < to; i++) {
				VehicleState state;
				if (i <= halfWay && i+1 > halfWay) {
					state = new VehicleState(v, ta, i, start, end);
				}
				else if (i > halfWay) {
					state = new VehicleState(v, ta, i, end, end);
				}
				else {
					state = new VehicleState(v, ta, i, start, start);
				}
				result.add(state);
			}
		}
		return result;
	}

	public void addAssignments(Map<Vehicle, Map<Pair<TimedArc, Boolean>, List<Passenger>>> vpMap) {
		for (Map.Entry<Vehicle,Map<Pair<TimedArc,Boolean>, List<Passenger>>> e : vpMap.entrySet()) {
			Vehicle v = e.getKey();
			for (Map.Entry<Pair<TimedArc,Boolean>, List<Passenger>> e2 : e.getValue().entrySet()) {
				TimedArc arc = e2.getKey().first;
				boolean start = e2.getKey().second;
				for (Passenger p : e2.getValue()) {
					addAssignment(arc, p, v, start);
				}
			}
		}
	}

	public void addAssignmentsFromMap(Map<Vehicle, Map<TimedArc, List<Passenger>>> vpMap, Map<Passenger, List<TimedArc>> outsideTransferMap) {
		// Step 1: determine the next and previous arc for each passenger/timedarc combination
		Map<Passenger,Map<TimedArc,TimedArc>> nextArcs = new HashMap<>();
		Map<Passenger,Map<TimedArc,TimedArc>> prevArcs = new HashMap<>();
		Map<Passenger,Map<TimedArc,Vehicle>> assignment = new HashMap<>();
		for (Passenger p : solution.getInstance().getPassengers()) {
			Map<TimedArc,TimedArc> nextMap = new HashMap<>();
			Map<TimedArc,TimedArc> prevMap = new HashMap<>();
			List<TimedArc> path = solutionHelper.getPath(p);
			if (path.isEmpty()) {
				continue;
			}
			TimedArc prev = null;
			for (TimedArc arc : path) {
				if (prev != null) {
					nextMap.put(prev, arc);
					prevMap.put(arc, prev);
				}
				prev = arc;
			}
			nextArcs.put(p, nextMap);
			prevArcs.put(p, prevMap);
			assignment.put(p, new HashMap<>());
		}

		// Step 2: determine the vehicle assigned to each passenger in each arc
		for (Map.Entry<Vehicle,Map<TimedArc, List<Passenger>>> e : vpMap.entrySet()) {
			Vehicle v = e.getKey();
			for (Map.Entry<TimedArc, List<Passenger>> e2 : e.getValue().entrySet()) {
				TimedArc arc = e2.getKey();
				for (Passenger p: e2.getValue()) {
					assignment.get(p).put(arc, v);
				}
			}
		}

		// Step 3: determine the starting and ending vehicle for each passenger in each arc.
		for (Passenger p : solution.getInstance().getPassengers()) {
			Set<TimedArc> egressSet = egressArcs.get(p);
			Map<TimedArc,Vehicle> map = assignment.get(p);
			List<TimedArc> path = solutionHelper.getPath(p);
			if (path.isEmpty()) {
				continue;
			}
			for (TimedArc cur: path) {
				boolean egress = egressSet.contains(cur);
				TimedArc prev = prevArcs.get(p).get(cur);
				TimedArc next = nextArcs.get(p).get(cur);

				Vehicle curVeh = map.get(cur);
				Vehicle nextVeh = next != null ? map.get(next) : null;
				Vehicle prevVeh = prev != null ? map.get(prev) : null;
				if (egress) {
					addAssignment(cur.toWaitInside(), p, prevVeh, true);
				}
				else if(outsideTransferMap.get(p).contains(cur)) { //This used to be a WAIT_OUT arc, so this is an outside transfer/arrive at destination arc
					// Disembark, only at the start of the arc
					addAssignment(cur, p, curVeh, true);
				}
				else if (curVeh == null && prevVeh != null) {
					// Disembark, only at the start of the arc
				}
				else if(curVeh!=null && nextVeh==null) {
					// Disenmbark. TODO: is this a good idea? If there is no next vehicle, stay in current
					addAssignment(cur, p, curVeh, true);
				}
				else if (curVeh != null && prevVeh == null) {
					// Embark, only at the end of the arc
					addAssignment(cur, p, curVeh, false);
				}
				else if (curVeh == null && nextVeh!=null) {
					// First time to embark
					// Don't do anything
				}
				else {
					// Regular, both at start and end
					if (prevVeh != null && !outsideTransferMap.get(p).contains(prev)) { //Previous arc was a WAIT_OUT arc
						addAssignment(cur, p, prevVeh, true);
					}
					if (curVeh != null) {
						addAssignment(cur, p, curVeh, false);
					}
				}
			}
		}

	}

	public void addAssignment(TimedArc arc, Passenger p, Vehicle v, boolean start) {
		//        Removed for now, since we want to visualize disembarking passengers
		Map<TimedArc,Vehicle> pMap = start ? getOrCreateSubMap(passengerStartAssignment, p)
				: getOrCreateSubMap(passengerEndAssignment, p);
		Map<TimedArc,Set<Passenger>> vMap = start ? getOrCreateSubMap(vehicleStartAssignment, v)
				: getOrCreateSubMap(vehicleEndAssignment, v);
		pMap.put(arc, v);
		addOrCreateSet(vMap, arc, p);
		seatAssignments.remove(v);
	}

	public Set<Passenger> getPassengerSet(Vehicle v, double time) {
		TreeMap<Double,TimedArc> map = vehicleTimeMap.get(v);
		if (map == null) {
			throw new IllegalArgumentException("This vehicle is not known");
		}
		Map.Entry<Double,TimedArc> entry = map.floorEntry(time);
		if (entry == null) {
			// Time is before the vehicle's path starts - return empty set
			return Collections.emptySet();
		}
		return getPassengerSet(v, entry.getValue(), time);
	}

	public Set<Passenger> getPassengerSet(Vehicle v, TimedArc arc, double time) {
		return getPassengerSet(v, arc, time < arc.getHalfwayTime());
	}

	public Set<Passenger> getPassengerSet(Vehicle v, TimedArc arc, boolean start) {
		Map<Vehicle, Map<TimedArc,Set<Passenger>>> map = start ? vehicleStartAssignment : vehicleEndAssignment;
		if (!map.containsKey(v)) {
			return Collections.emptySet();
		}
		Set<Passenger> set = map.get(v).get(arc);
		if (set == null) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(set);
	}

	public TimedArc getTimedArcForVehicle(Vehicle v, double time) {
		TreeMap<Double,TimedArc> map = vehicleTimeMap.get(v);
		if (map == null) {
			throw new IllegalArgumentException("This vehicle is not known");
		}
		Map.Entry<Double,TimedArc> entry = map.floorEntry(time);
		return entry.getValue();
	}

	public TimedArc getTimedArcForPassenger(Passenger p, double time) {
		TreeMap<Double,TimedArc> map = passengerTimeMap.get(p);
		if (map == null) {
			throw new IllegalArgumentException("This passenger is not known");
		}
		Map.Entry<Double,TimedArc> entry = map.floorEntry(time);
		TimedArc arc = entry.getValue();
		if (egressArcs.get(p).contains(arc) && time < arc.getHalfwayTime()) {
			return arc.toWaitInside();
		}
		return arc;
	}

	public List<TimedArc> getTimedPathForPassenger(Passenger p) {
		Path<Passenger> path = passengerPaths.get(p);
		List<TimedArc> newPath = new ArrayList<>();
		for (TimedArc arc : path.getPath()) {
			newPath.add(getTimedArcForPassenger(p, arc.getHalfwayTime()));
		}
		return newPath;
	}

	public Vehicle getVehicleForPassenger(Passenger p, double time) {
		TreeMap<Double,TimedArc> map = passengerTimeMap.get(p);
		if (map == null) {
			throw new IllegalArgumentException("This passenger is not known");
		}
		Map.Entry<Double,TimedArc> entry = map.floorEntry(time);
		if (entry == null) {
			// Time is before the passenger's path starts - no vehicle assigned yet
			return null;
		}
		return getVehicleForPassenger(p, entry.getValue(), time);
	}

	public Vehicle getVehicleForPassenger(Passenger p, TimedArc arc, double time) {
		return getVehicleForPassenger(p, arc, time < arc.getHalfwayTime());
	}

	public Vehicle getVehicleForPassenger(Passenger p, TimedArc arc, boolean start) {
		Map<Passenger, Map<TimedArc,Vehicle>> map = start ? passengerStartAssignment : passengerEndAssignment;
		if (!map.containsKey(p)) {
			return null;
		}
		return map.get(p).get(arc);
	}

	public boolean willDisembarkAfter(Passenger p, TimedArc arc) {
		TimedArc next = getNextArc(p, arc);
		if (next == null) {
			return true;
		}
		if (next.getPurpose() == ArcPurpose.WAIT_IN || next.getPurpose() == ArcPurpose.WAIT_OUT) {
			Vehicle v = getVehicleForPassenger(p, next, false);
            return v == null;
		}
		return false;
	}

	public TimedArc getNextArc(Passenger p, TimedArc arc) {
		return nextArcMap.get(p).get(arc);
	}

	public int getPassengerIndex(TimedArc arc, Passenger p, boolean compact) {
		if (!compact) {
			throw new UnsupportedOperationException("This is not yet implemented");
		}
		if(arc.getPurpose()==ArcPurpose.WAIT_IN) {
			return passengersPerArc.get(arc).get(p);
		}
		TimedArc arc2 = arc.toWaitInside();	
		int totalWaitIn = 0;
		if(passengersPerArc.containsKey(arc2)) {
			totalWaitIn = passengersPerArc.get(arc2).size();
		}
		return totalWaitIn+passengersPerArc.get(arc).get(p);
	}

	public int getPassengerIndex(TimedArc arc, Passenger p, Vehicle v, double time, boolean compact) {
		if (!compact) {
			SeatAssignment sa = getSeatAssignment(v);
			TimedArc altArc = getTimedArcForPassenger(p, time);
			return sa.getSeatIndex(altArc, p, time);
		}
		List<Passenger> passengers = new ArrayList<>(getPassengerSet(v, arc, time));
		passengers.sort(Comparator.comparing(Passenger::getId));
		if(!passengers.contains(p)) {
			throw new IllegalArgumentException("The index of the passenger cannot be -1");
		}
		return passengers.indexOf(p);
	}

	public int getVehicleIndex(TimedArc arc, Vehicle v) {
		return vehiclesPerArc.get(arc).get(v);
	}

	public Solution getSolution() {
		return solution;
	}

	public Map<Vehicle, Map<Pair<TimedArc, Boolean>, List<Passenger>>> getAssignment() {
		Map<Vehicle, Map<Pair<TimedArc, Boolean>, List<Passenger>>> result = new HashMap<>();
		transformAndPut(vehicleStartAssignment, result, ta -> Pair.of(ta,true), ArrayList::new);
		transformAndPut(vehicleStartAssignment, result, ta -> Pair.of(ta,false), ArrayList::new);
		return result;
	}

	public List<JourneySegment> getJourneySegments(Passenger p) {
		Path<Passenger> path = passengerPaths.get(p);
		if (path == null) {
			throw new IllegalArgumentException("Unknown passenger");
		}
		List<JourneySegment> result =  new ArrayList<>();
		List<TimedArc> list = new ArrayList<>();
		double startTime = 0;
		TimedArc prev = null;
		Vehicle prevVehicle = null;
		for (TimedArc ta : path.getPath()) {
			Vehicle start = getVehicleForPassenger(p, ta, true);
			Vehicle end = getVehicleForPassenger(p, ta, false);
			if (prev != null) {
				if (prevVehicle != start) {
					result.add(new JourneySegment(p, list, prevVehicle, startTime, prev.getToTime()));
					startTime = ta.getFromTime();
					prevVehicle = start;
					list = new ArrayList<>();
				}
				if (start != end) {
					list.add(ta);
					result.add(new JourneySegment(p, list, start, startTime, ta.getHalfwayTime()));
					result.add(new JourneySegment(p, list, start, end, ta.getHalfwayTime(), ta.getToTime()));
					startTime = ta.getToTime();
					prevVehicle = end;
					list = new ArrayList<>();
				}
			}
			list.add(ta);
			if (prev == null) {
				startTime = ta.getFromTime();
				if (end != null) {
					result.add(new JourneySegment(p, list, start, end, ta.getFromTime(), ta.getHalfwayTime()));
					startTime = ta.getHalfwayTime();
				}
				prevVehicle = end;
			}
			prev = ta;
		}
		result.add(new JourneySegment(p, list, prevVehicle, startTime, prev.getToTime()));
		return result;
	}

	public List<PassengerState> getPassengerStates(Passenger p) {
		Path<Passenger> path = passengerPaths.get(p);
		if (path == null) {
			throw new IllegalArgumentException("Unknown passenger");
		}
		return getPassengerStates(path);
	}

	public List<VehicleState> getVehicleStates(Vehicle v) {
		Path<Vehicle> path = vehiclePaths.get(v);
		if (path == null) {
			throw new IllegalArgumentException("Unknown passenger");
		}
		return getVehicleStates(path);
	}

	public boolean crossValidate() {
		boolean result = crossValidate(vehicleStartAssignment, passengerStartAssignment, "the start assignments");
        if (!crossValidate(vehicleEndAssignment, passengerEndAssignment, "the end assignments")) {
			result = false;
		}
		return true;
	}

	private boolean crossValidate(Map<Vehicle,Map<TimedArc,Set<Passenger>>> vehMap,
							   Map<Passenger,Map<TimedArc,Vehicle>> pasMap, String task) {
		boolean result = true;
		for (Entry<Vehicle,Map<TimedArc,Set<Passenger>>> e1 : vehMap.entrySet()) {
			Vehicle v = e1.getKey();
			for (Entry<TimedArc,Set<Passenger>> e2 : e1.getValue().entrySet()) {
				TimedArc arc = e2.getKey();
				for (Passenger p : e2.getValue()) {
					Map<TimedArc,Vehicle> sub = pasMap.get(p);
					if (sub == null || !v.equals(sub.get(arc))) {
						log.warn("The assignment of vehicle {} to passenger {} on arc {} exist in the vehicle map," +
								" but not in the passenger map, while validating {}", v.getId(), p.getId(), arc, task);
						result = false;
					}
				}
			}
		}

		for (Entry<Passenger,Map<TimedArc,Vehicle>> e1 : pasMap.entrySet()) {
			Passenger p = e1.getKey();
			for (Entry<TimedArc,Vehicle> e2 : e1.getValue().entrySet()) {
				TimedArc arc = e2.getKey();
				Vehicle v = e2.getValue();
				Map<TimedArc,Set<Passenger>> sub = vehMap.get(v);
				if (sub == null || sub.get(arc) == null || !sub.get(arc).contains(p)) {
					log.warn("The assignment of vehicle {} to passenger {} on arc {} exist in the passenger map," +
							" but not in the vehicle map, while validating {}", v.getId(), p.getId(), arc, task);
					result = false;
				}
			}
		}
		return result;
	}


	private SeatAssignment getSeatAssignment(Vehicle v) {
		SeatAssignment sa = seatAssignments.get(v);
		if (sa != null) {
			return sa;
		}
		sa = new SeatAssignment(v, vehicleStartAssignment.get(v), vehicleEndAssignment.get(v), this::willDisembarkAfter);
		seatAssignments.put(v, sa);
		return sa;
	}

	private static <E1,E2,E3> Map<E2,E3> getOrCreateSubMap(Map<E1,Map<E2,E3>> map, E1 key) {
		return map.computeIfAbsent(key, k -> new HashMap<>());
	}

	private static <E1,E2> void addOrCreateSet(Map<E1,Set<E2>> map, E1 key, E2 element) {
		Set<E2> set = map.computeIfAbsent(key, k -> new HashSet<>());
		set.add(element);
	}

	private static <K,KO,KM,VO,VM> void transformAndPut(Map<K,Map<KO,VO>> source,
			Map<K,Map<KM,VM>> target,
			Function<KO,KM> keyMapping,
			Function<VO,VM> valueMapping) {
		for (Map.Entry<K,Map<KO,VO>> e : source.entrySet()) {
			K key = e.getKey();
			Map<KO,VO> subSource = e.getValue();
			Map<KM, VM> subTarget = target.computeIfAbsent(key, k -> new HashMap<>());
			for (Map.Entry<KO,VO> e2 : subSource.entrySet()) {
				KM mappedKey = keyMapping.apply(e2.getKey());
				VM mappedValue = valueMapping.apply(e2.getValue());
				subTarget.put(mappedKey, mappedValue);
			}
		}
	}
}
