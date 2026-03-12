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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import podrouting.data.Instance;
import podrouting.data.Passenger;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;

public class SolutionHelper {

	private final Instance instance;
	private final Solution solution;
	
	private final Map<Object,List<TimedArc>> paths = new HashMap<>();
	
	private final Map<TimedArc,List<Vehicle>> vehicles = new HashMap<>();
	private final Map<TimedArc,List<Passenger>> passengers = new HashMap<>();
	private final Map<TimedArc,List<Passenger>> passengersWaiting = new HashMap<>();
	private final Map<TimedArc,List<Passenger>> passengersArrived = new HashMap<>();
	private int maxListLength;
	
	private final boolean padBefore;
	private final boolean padAfter;
	private final boolean waitOutToWaitInside;

	public SolutionHelper(Solution sol) {
		this(sol, false, false, false);
	}
	
	public SolutionHelper(Solution sol, boolean padBefore, boolean padAfter, boolean waitOutToWaitInside) {
		this.solution = sol;
		this.instance = sol.getInstance();
		this.padBefore = padBefore;
		this.padAfter = padAfter;
		this.waitOutToWaitInside = waitOutToWaitInside;
		computeLists();
	}
	
	private void computeLists() {
		int endTime = instance.getMaximumTime();
		passengers.clear();
		for (Path<Passenger> path : solution.getPassengerPaths()) {
			Passenger p = path.getCommodity();
			if (path.getPath().isEmpty()) {
				continue;
			}
			
			List<TimedArc> arcList = new ArrayList<>();

			if (padBefore) {
				for (int t=0; t < path.getFirst().getFromTime(); t++) {
					TimedArc arc = path.getFirst().getFromLocation().toWaitArc(t, ArcPurpose.WAIT_OUT);
					arcList.add(arc);
					addPassengerArc(p, arc);
				}
			}
			
			TimedArc prev = null;
			for (TimedArc ta : path.getPath()) {
				if(waitOutToWaitInside && prev!=null && prev.getPurpose()==ArcPurpose.DRIVE && ta.getPurpose()==ArcPurpose.WAIT_OUT) {
					ta = ta.toWaitInside();
				}
				
				arcList.add(ta);
				addPassengerArc(p, ta);
				prev = ta;
			}
			
			if (padAfter) {
				for (int t=path.getLast().getToTime(); t <= endTime; t++) {
					TimedArc arc = path.getLast().getToLocation().toWaitArc(t, ArcPurpose.WAIT_OUT);
					arcList.add(arc);
					addPassengerArc(p, arc);
				}
			}
			paths.put(p, arcList);
		}

		vehicles.clear();
		for (Path<Vehicle> path : solution.getVehiclePaths()) {
			Vehicle v = path.getCommodity();
			List<TimedArc> arcList = new ArrayList<>();
			if (padBefore) {
				for (int t=0; t < path.getFirst().getFromTime(); t++) {
					TimedArc arc = path.getFirst().getFromLocation().toWaitArc(t, ArcPurpose.WAIT_OUT);
					arcList.add(arc);
					addVehicleArc(v, arc);
				}
			}
			for (TimedArc ta : path.getPath()) {
				arcList.add(ta);
				addVehicleArc(v, ta);
			}
			if (padAfter) {
				for (int t=path.getLast().getToTime(); t <= endTime; t++) {
					TimedArc arc = path.getLast().getToLocation().toWaitArc(t, ArcPurpose.WAIT_OUT);
					arcList.add(arc);
					addVehicleArc(v, arc);
				}
			}
			paths.put(v, arcList);
		}

		for (List<Passenger> lst : passengers.values()) {
			lst.sort((p1,p2) -> p1.getId() - p2.getId());
		}
		for (List<Passenger> lst : passengersWaiting.values()) {
			lst.sort((p1,p2) -> p1.getTimeStart() == p2.getTimeStart() ? p1.getId() - p2.getId() : p1.getTimeStart() - p2.getTimeStart());
		}
		for (List<Passenger> lst : passengersArrived.values()) {
			lst.sort((p1,p2) -> p1.getTimeEnd() == p2.getTimeEnd() ? p1.getId() - p2.getId() : p1.getTimeEnd() - p2.getTimeEnd());
		}
		for (List<Vehicle> lst : vehicles.values()) {
			lst.sort((v1,v2) -> v1.getId() - v2.getId());
		}
	}
	
	private void addPassengerArc(Passenger p, TimedArc ta) {
		if (ta.getPurpose() == ArcPurpose.WAIT_OUT) {
			if (ta.getFromLocation().equals(p.getDestination())) {
				if (!passengersArrived.containsKey(ta)) {
					passengersArrived.put(ta, new ArrayList<>());
				}
				passengersArrived.get(ta).add(p);
				maxListLength = Math.max(passengersArrived.get(ta).size(), maxListLength);
			}
			else {
				if (!passengersWaiting.containsKey(ta)) {
					passengersWaiting.put(ta, new ArrayList<>());
				}
				passengersWaiting.get(ta).add(p);
				maxListLength = Math.max(passengersWaiting.get(ta).size(), maxListLength);
			}
		}
		else {
			if (!passengers.containsKey(ta)) {
				passengers.put(ta, new ArrayList<>());
			}
			passengers.get(ta).add(p);
			maxListLength = Math.max(passengers.get(ta).size(), maxListLength);
		}
	}
	
	private void addVehicleArc(Vehicle v, TimedArc ta) {
		if (!vehicles.containsKey(ta)) {
			vehicles.put(ta, new ArrayList<>());
		}
		vehicles.get(ta).add(v);
		maxListLength = Math.max(maxListLength, vehicles.get(ta).size());
	}

	public List<Passenger> getPassengersArrived(TimedArc ta) {
		return Collections.unmodifiableList(passengersArrived.getOrDefault(ta,Collections.emptyList()));
	}

	public List<Passenger> getPassengersWaiting(TimedArc ta) {
		return Collections.unmodifiableList(passengersWaiting.getOrDefault(ta,Collections.emptyList()));
	}

	public List<Passenger> getPassengers(TimedArc ta) {
		return Collections.unmodifiableList(passengers.getOrDefault(ta,Collections.emptyList()));
	}

	public List<Vehicle> getVehicles(TimedArc ta) {
		return Collections.unmodifiableList(vehicles.getOrDefault(ta, Collections.emptyList()));
	}

	public int getMaxListLength() {
		return maxListLength;
	}

	public List<TimedArc> getPath(Object commodity) {
		return Collections.unmodifiableList(paths.getOrDefault(commodity, Collections.emptyList()));
	}
	
}
