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
package podrouting.visualizer;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import podrouting.data.Location;
import podrouting.data.Passenger;
import podrouting.data.Road;
import podrouting.data.Vehicle;

public class Timestep {
	
	private int time;
	private Map<Vehicle,Location> vehicleAtLocation;
	
	private Map<Vehicle,Road> vehicleOnRoad;
	private Map<Vehicle,Integer> vehicleProgress;
	
	private Map<Passenger,Location> passengerAtLocation;
	
	private Map<Passenger,Road> passengerOnRoad;
	private Map<Passenger,Integer> passengerProgress;
	
	public Timestep(int time) {
		this.vehicleAtLocation = new LinkedHashMap<>();
		
		this.vehicleOnRoad = new LinkedHashMap<>();
		this.vehicleProgress = new LinkedHashMap<>();
		
		this.passengerAtLocation = new LinkedHashMap<>();
		
		this.passengerOnRoad = new LinkedHashMap<>();
		this.passengerProgress = new LinkedHashMap<>();
		
		this.time = time;
	}
	
	public int getTime() {
		return time;
	}
	
	public String getStateClass(Passenger p) {
		if (passengerAtLocation.containsKey(p)) {
			Location loc = passengerAtLocation.get(p);
			if (loc.equals(p.getOrigin())) {
				return "origin";
			}
			if (loc.equals(p.getDestination())) {
				return "destination";
			}
			return "waiting";
		}
		if (passengerOnRoad.containsKey(p)) {
			return "road";
		}
		return "";
	}
	
	public String getStateDescr(Passenger p) {
		if (passengerAtLocation.containsKey(p)) {
			Location loc = passengerAtLocation.get(p);
			if (loc.equals(p.getOrigin())) {
				return "departing @ "+loc.getName();
			}
			if (loc.equals(p.getDestination())) {
				return "arrived @ "+loc.getName();
			}
			return "waiting @ "+loc.getName();
		}
		if (passengerOnRoad.containsKey(p)) {
			return "travel @ "+passengerOnRoad.get(p).getName();
		}
		return "";		
	}
	
	public String getStateClass(Vehicle v) {
		if (vehicleOnRoad.containsKey(v)) {
			return "road";
		}
		if (vehicleAtLocation.containsKey(v)) {
			Location loc = vehicleAtLocation.get(v);
			if (loc.equals(v.getOrigin())) {
				return "origin";
			}
			if (loc.equals(v.getDestination())) {
				return "destination";
			}
			return "waiting";
		}
		return "";
	}

	public String getStateDescr(Vehicle v) {
		if (vehicleOnRoad.containsKey(v)) {
			return "travel @ "+vehicleOnRoad.get(v).getName();
		}
		if (vehicleAtLocation.containsKey(v)) {
			Location loc = vehicleAtLocation.get(v);
			if (loc.equals(v.getOrigin())) {
				return "departing @ "+loc.getName();
			}
			if (loc.equals(v.getDestination())) {
				return "arrived @ "+loc.getName();
			}
			return "waiting @ "+loc.getName();
		}
		return "";
	}

	
	
	public void setPassengerState(Passenger p, Location l) {
		if (passengerAtLocation.containsKey(p) || passengerOnRoad.containsKey(p)) {
			throw new IllegalStateException("Passenger was already added to this timestep");
		}
		passengerAtLocation.put(p, l);
	}
	
	public void setPassengerState(Passenger p, Road r, int step) {
		if (passengerAtLocation.containsKey(p) || passengerOnRoad.containsKey(p)) {
			throw new IllegalStateException("Passenger was already added to this timestep");
		}
		if (step < 0 || step >= Math.ceil(r.getDistance())) {
			throw new IllegalStateException("The step can not exceed the distance of the road");
		}
		passengerOnRoad.put(p, r);
		passengerProgress.put(p, step);
	}
	
	public void setVehicleState(Vehicle v, Location l) {
		if (vehicleAtLocation.containsKey(v) || vehicleOnRoad.containsKey(v)) {
			throw new IllegalStateException("Passenger was already added to this timestep");
		}
		vehicleAtLocation.put(v, l);
	}
	
	public void setVehicleState(Vehicle v, Road r, int step) {
		if (vehicleAtLocation.containsKey(v) || vehicleOnRoad.containsKey(v)) {
			throw new IllegalStateException("Passenger was already added to this timestep");
		}
		if (step < 0 || step >= Math.ceil(r.getDistance())) {
			throw new IllegalStateException("The step can not exceed the distance of the road");
		}
		vehicleOnRoad.put(v, r);
		vehicleProgress.put(v, step);
	}
	
	public void printStep(PrintWriter pw) {
		pw.println("*** Timestep "+time+" ***");
		pw.println("-At Location-");
		for (Entry<Vehicle,Location> e : vehicleAtLocation.entrySet()) {
			pw.println(" Vehicle "+e.getKey().getId()+" at location "+e.getValue().getName());
		}
		for (Entry<Passenger,Location> e : passengerAtLocation.entrySet()) {
			if (e.getKey().getOrigin().equals(e.getValue())) {
				pw.println(" Passenger "+e.getKey().getId()+" at origin location ("+e.getValue().getName()+")");
			}
			else if (e.getKey().getDestination().equals(e.getValue())) {
				pw.println(" Passenger "+e.getKey().getId()+" at destination location ("+e.getValue().getName()+")");
			}
			else {
				pw.println(" Passenger "+e.getKey().getId()+" at location "+e.getValue().getName());
			}
		}
		pw.println("-On the Move-");
		for (Entry<Vehicle,Road> e : vehicleOnRoad.entrySet()) {
			int step = 1 + vehicleProgress.get(e.getKey());
			int steps = (int)Math.ceil(e.getValue().getDistance());
			Road r = e.getValue();
			String roadStr = r.getOrigin().getName() + "==>" + r.getDestination().getName();
			pw.println(" Vehicle "+e.getKey().getId()+" at road " + roadStr + " [step "+step+"/"+steps+"]");
		}
		for (Entry<Passenger,Road> e : passengerOnRoad.entrySet()) {
			int step = 1 + passengerProgress.get(e.getKey());
			int steps = (int)Math.ceil(e.getValue().getDistance());
			Road r = e.getValue();
			String roadStr = r.getOrigin().getName() + "==>" + r.getDestination().getName();
			pw.println(" Passenger "+e.getKey().getId()+" at road "+roadStr + " [step "+step+"/"+steps+"]");
		}
		pw.println();
	}
	
	
}
