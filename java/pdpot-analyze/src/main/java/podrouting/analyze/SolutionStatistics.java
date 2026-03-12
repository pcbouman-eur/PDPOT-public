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
package podrouting.analyze;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import podrouting.data.*;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;
import podrouting.util.Pair;

public class SolutionStatistics {

	private final Solution sol;
	private List<Integer> rejectedPassengersList;
	private int numRejectedPassengers;
	private int numOutsideTransfers;
	private Map<Integer, ArrayList<Integer>> outsideTransferList;
	private int numInsideTransfersLB;
	private HashMap<Integer, HashSet<Integer>> insideTransferListLB;
	private int numInsideTransfersUB;
	private HashMap<Integer, HashSet<Integer>> insideTransferListUB;
	private double earlyPenalty;
	private double transferOutsidePenalty;
	private double platooningDiscount;
	private double drivingCost;
	private double rejectionPenalty;
	private double allPassengerTransportLB;
	private double servicedPassengerTransportLB;
	private double totalDrivingDistance;

	@JsonCreator
	public SolutionStatistics(	@JsonProperty("solution") Solution sol,
			@JsonProperty("rejectedPassengersList") List<Integer> rejectedPassengersList,
			@JsonProperty("numRejectedPassengers") int numRejectedPassengers,
			@JsonProperty("outsideTransferList") Map<Integer, ArrayList<Integer>> outsideTransferList,
			@JsonProperty("numOutsideTransfers") int numOutsideTransfers,
			@JsonProperty("insideTransferListLB") HashMap<Integer, HashSet<Integer>> insideTransferListLB,
			@JsonProperty("numInsideTransfersLB") int numInsideTransfersLB,
			@JsonProperty("insideTransferListUB") HashMap<Integer, HashSet<Integer>> insideTransferListUB,
			@JsonProperty("numInsideTransfersUB") int numInsideTransfersUB,
			@JsonProperty("earlyPenalty") double earlyPenalty,
			@JsonProperty("transferOutsidePenalty") double transferOutsidePenalty,
			@JsonProperty("platooningDiscount") double platooningDiscount,
			@JsonProperty("drivingCost") double drivingCost,
			@JsonProperty("rejectionPenalty") double rejectionPenalty,
			@JsonProperty("allPassengerTransportLB") double allPassengerTransportLB,
			@JsonProperty("servicedPassengerTransportLB") double servicedPassengerTransportLB,
			@JsonProperty("totalDrivingDistance") double totalDrivingDistance
			)

	{
		super();
		this.sol = sol;
		this.rejectedPassengersList = rejectedPassengersList;
		this.numRejectedPassengers = numRejectedPassengers;
		this.outsideTransferList = outsideTransferList;
		this.numOutsideTransfers = numOutsideTransfers;
		this.insideTransferListLB = insideTransferListLB;
		this.numInsideTransfersLB = numInsideTransfersLB;
		this.insideTransferListUB = insideTransferListUB;
		this.numInsideTransfersUB = numInsideTransfersUB;
		this.earlyPenalty = earlyPenalty;
		this.transferOutsidePenalty = transferOutsidePenalty;
		this.platooningDiscount = platooningDiscount;
		this.drivingCost = drivingCost;
		this.rejectionPenalty = rejectionPenalty;	
		this.allPassengerTransportLB = allPassengerTransportLB;
		this.servicedPassengerTransportLB = servicedPassengerTransportLB;
		this.totalDrivingDistance = totalDrivingDistance;
	}

	public SolutionStatistics(Solution sol) {
		this.sol = sol;
		performCalculations();
	}

	private void performCalculations(){
		calcRejectedPassengers();
		calcInsideTransfers(); //calculates outside transfers automatically

		calcDrivingCost();
		calcEarlyPenalty();
		calcTransferOutsidePenalty();
		calcPlatooningDiscount();
		calcRejectionPenalty();

		calcServicedPassengerTransportLB();
		calcAllPassengerTransportLB();
		calcTotalDrivingDistance();
	}

	private void calcDrivingCost() {
		drivingCost = 0;
		for(Path<Vehicle> vehiclePaths: sol.getVehiclePaths()) {
			for(TimedArc arc: vehiclePaths.getPath()) {
				drivingCost+=arc.getDistance();
			}
		}
		drivingCost *= sol.getInstance().getDrivingPenalty();
	}

	private void calcServicedPassengerTransportLB() {
		ShortestPathAlgorithm<Location, Road> sp = sol.getInstance().shortestPaths();
		servicedPassengerTransportLB = sol.getPassengerPaths()
		                                  .stream()
				                          .filter(path -> !path.getPath().isEmpty())
										  .map(Path::getCommodity)
										  .map(p -> sp.getPath(p.getOrigin(), p.getDestination()))
										  .mapToDouble(GraphPath::getWeight)
										  .sum();
	}

	private void calcAllPassengerTransportLB() {
		List<Passenger> passengers = sol.getInstance().getPassengers();
		ShortestPathAlgorithm<Location, Road> sp = sol.getInstance().shortestPaths();
		allPassengerTransportLB = passengers.stream()
										 .map(p -> sp.getPath(p.getOrigin(), p.getDestination()))
										 .mapToDouble(GraphPath::getWeight)
										 .sum();
	}

	private void calcTotalDrivingDistance() {
		totalDrivingDistance = sol.getVehiclePaths()
				  				  .stream()
				                  .flatMap(path -> path.getPath().stream())
				                  .mapToDouble(TimedArc::getDistance)
				                  .sum();
	}

	private void calcEarlyPenalty() {
		earlyPenalty = 0;
		double penalty = sol.getInstance().getArriveEarlyPenalty();
		for(Path<Passenger> passengerPaths: sol.getPassengerPaths()) {
			for(TimedArc arc: passengerPaths.getPath()) {
				if(arc.getPurpose().equals(ArcPurpose.WAIT_OUT) && arc.getToLocation().equals(passengerPaths.getCommodity().getDestination())) {
					earlyPenalty += penalty;
				}
			}
		}
	}

	private void calcTransferOutsidePenalty() {
		transferOutsidePenalty = 0;
		double penalty = sol.getInstance().getTransferOutsidePenalty();
		for(Path<Passenger> passengerPaths: sol.getPassengerPaths()) {
			for(Pair<TimedArc, TimedArc> pair: passengerPaths.getArcPairs()) {
				TimedArc arc1 = pair.first;
				TimedArc arc2 = pair.second;


				if ((arc1.getPurpose() == ArcPurpose.WAIT_IN || arc1.getPurpose() == ArcPurpose.DRIVE)
						&& (arc2.getPurpose() == ArcPurpose.WAIT_OUT
						&& !arc2.getToLocation().equals(passengerPaths.getCommodity().getDestination()))) {
					transferOutsidePenalty += penalty;
				}
			}
		}
	}

	private void calcPlatooningDiscount() {
		platooningDiscount = 0;
		double discount = sol.getInstance().getPlatooningDiscount();
		Map<TimedArc, Integer> countMap = new LinkedHashMap<TimedArc, Integer>();
		for(Path<Vehicle> vehiclePaths: sol.getVehiclePaths()) {
			for(TimedArc arc: vehiclePaths.getPath()) {
				if(countMap.containsKey(arc)) {
					int newValue = countMap.get(arc)+1;
					countMap.put(arc, newValue);
				}
				else {
					countMap.put(arc, 1);
				}
			}
		}
		for(Entry<TimedArc, Integer> val: countMap.entrySet()) {
			double distance = val.getKey().getDistance();
			platooningDiscount -= distance*(val.getValue()-1)*discount;
		}
	}

	private void calcRejectionPenalty() {
		rejectionPenalty = 0;
		double penalty = sol.getInstance().getRejectionPenalty();
		int numAcceptedPassengers = 0;
		for(Path<Passenger> passengerPaths: sol.getPassengerPaths()) {
			for(TimedArc arc: passengerPaths.getPath()) {
				if(arc.getToLocation().equals(passengerPaths.getCommodity().getDestination())) {
					numAcceptedPassengers++;
					break;
				}
			}
		}
		int numPassengers = sol.getInstance().getPassengers().size();
		int numRejectedPassengers = numPassengers - numAcceptedPassengers;
		rejectionPenalty = numRejectedPassengers * penalty;
	}

	public double getEarlyPenalty() {
		return earlyPenalty;
	}

	public double getTransferOutsidePenalty() {
		return transferOutsidePenalty;
	}

	public double getPlatooningDiscount() {
		return platooningDiscount;
	}

	public double getDrivingCost() {
		return drivingCost;
	}

	public double getRejectionPenalty() {
		return rejectionPenalty;
	}

	public Solution getSolution() {
		return sol;
	}

	public List<Integer> getRejectedPassengersList()
	{
		return rejectedPassengersList;
	}

	public Integer getNumRejectedPassengers()
	{
		return numRejectedPassengers;
	}

	public Map<Integer, ArrayList<Integer>> getOutsideTransferList()
	{
		return outsideTransferList;
	}

	public int getNumOutsideTransfers()
	{
		return numOutsideTransfers;
	}

	public HashMap<Integer, HashSet<Integer>> getInsideTransferListLB()
	{
		return insideTransferListLB;
	}

	public int getNumInsideTransfersLB()
	{
		return numInsideTransfersLB;
	}

	public HashMap<Integer, HashSet<Integer>> getInsideTransferListUB()
	{
		return insideTransferListUB;
	}

	public int getNumInsideTransfersUB()
	{
		return numInsideTransfersUB;
	}

	public double getAllPassengerTransportLB() {
		return allPassengerTransportLB;
	}

	public double getServicedPassengerTransportLB() {
		return servicedPassengerTransportLB;
	}

	public double getTotalDrivingDistance() {
		return totalDrivingDistance;
	}

	public double getAllPassengerTransportRatio() {
		return totalDrivingDistance / allPassengerTransportLB;
	}

	public double getServicedPassengerTransportRatio() {
		return totalDrivingDistance / servicedPassengerTransportLB;
	}

	public double getServiceFraction() {
		double numPassengers = sol.getInstance().getPassengers().size();
		return (numPassengers - numRejectedPassengers) / numPassengers;
	}

	public double getServicedPassengers() {
		return sol.getInstance().getPassengers().size() - numRejectedPassengers;
	}

	public double getServicedPassengersPerSeat() {
		double seats = sol.getInstance().getVehicles().stream().mapToInt(Vehicle::getCapacity).sum();
		return getServicedPassengers() / seats;
	}

	public double getServicedPassengersPerVehicle() {
		return getServicedPassengers() / sol.getInstance().getVehicles().size();
	}

	private void calcRejectedPassengers()
	{
		List<Passenger> passengers = sol.getInstance().getPassengers();
		List<Path<Passenger>> solPaths = sol.getPassengerPaths();
		Set<Passenger> servicedPassengers = new HashSet<>();

		for (Path<Passenger> path : solPaths) {
			Passenger p = path.getCommodity();
			Location origin = p.getOrigin();
			Location dest = p.getDestination();
			List<TimedArc> arcs = path.getPath();
			boolean serviced = arcs.isEmpty() && origin.equals(dest);
            if (!serviced && !arcs.isEmpty()) {
				TimedLocation first = arcs.get(0).getFrom();
				TimedLocation last = arcs.get(arcs.size() - 1).getTo();
				if (  first.getLocation().equals(origin) && last.getLocation().equals(dest)
				   && first.getTime() >= p.getTimeStart() && last.getTime() <= p.getTimeEnd()) {
					serviced = true;
				}
			}

			if (serviced) {
				servicedPassengers.add(p);
			}
		}

		this.rejectedPassengersList = passengers.stream()
				                          .filter(p -> !servicedPassengers.contains(p))
										  .map(Passenger::getId)
				                          .collect(Collectors.toList());

		this.numRejectedPassengers = passengers.size() - servicedPassengers.size();
	}

	private void calcOutsideTransfers()
	{
		List<Path<Passenger>> listPathPassenger = sol.getPassengerPaths();
		Map<Integer, ArrayList<Integer>> outsideTransferList = new HashMap<Integer, ArrayList<Integer>>();

		numOutsideTransfers= 0;
		for(Path<Passenger> p: listPathPassenger)
		{
			List<Location> waitLocations = new ArrayList<Location>();
			outsideTransferList.put(p.getCommodity().getId(), new ArrayList<>());
			for(Pair<TimedArc, TimedArc> pair: p.getArcPairs()) {
				TimedArc arc1 = pair.first;
				TimedArc arc2 = pair.second;

				if ((arc1.getPurpose() == ArcPurpose.WAIT_IN || arc1.getPurpose() == ArcPurpose.DRIVE)
						&& (arc2.getPurpose() == ArcPurpose.WAIT_OUT
						&& !arc2.getToLocation().equals(p.getCommodity().getDestination()))) {
					if(!waitLocations.contains(arc1.getFromLocation())) {
						numOutsideTransfers++;
						ArrayList<Integer> time = new ArrayList<Integer>(outsideTransferList.get(p.getCommodity().getId()));
						time.add(arc1.getFromTime());
						waitLocations.add(arc1.getFromLocation());
					}
				}
			}
		}
		this.outsideTransferList = outsideTransferList;
	}

	private void calcInsideTransfers()
	{
		calcOutsideTransfers();
		List<Path<Passenger>> listPathPassenger = sol.getPassengerPaths();
		List<Path<Vehicle>> listPathVehicle = sol.getVehiclePaths();
		Map<Integer, HashMap<Integer, ArrayList<Integer>>> passengerMap = new HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>();
		Map<Integer, HashSet<Integer>> uniqueMap = new HashMap<Integer, HashSet<Integer>>();

		numInsideTransfersLB = 0;
		numInsideTransfersUB = 0;
		for(Path<Passenger> p: listPathPassenger)
		{
			uniqueMap.put(p.getCommodity().getId(), new HashSet<>());
			Location dest = p.getCommodity().getDestination();
			HashMap<Integer, ArrayList<Integer>> timeMap = new HashMap<Integer, ArrayList<Integer>>();

			for(TimedArc arc: p.getPath())
			{
				if(arc.getFromLocation().equals(dest))
				{
					break;
				}
				else if(arc.getPurpose()==ArcPurpose.DRIVE)
				{
					int currentTime = arc.getFromTime();			
					ArrayList<Integer> vehicleID = new ArrayList<Integer>();

					for(Path<Vehicle> v: listPathVehicle)
					{
						for(TimedArc vehicleArc: v.getPath()) {
							if(vehicleArc.getFromTime()==currentTime && vehicleArc.getPurpose().equals(ArcPurpose.DRIVE) &&vehicleArc.getRoad().equals(arc.getRoad())) {
								vehicleID.add(v.getCommodity().getId());
							}
						}
					}
					if(vehicleID.size()==1)
					{
						int passengerID = p.getCommodity().getId();
						HashSet<Integer> newList = new HashSet<Integer>(uniqueMap.get(passengerID));
						newList.add(vehicleID.get(0));
						uniqueMap.put(passengerID, newList);
					}
					else
					{
						timeMap.put(currentTime, vehicleID);
					}
				}
			}
			passengerMap.put(p.getCommodity().getId(), timeMap);
		}

		insideTransferListLB = new HashMap<Integer, HashSet<Integer>>();
		for(Integer j: uniqueMap.keySet()) {
			insideTransferListLB.put(j, new HashSet<Integer>(uniqueMap.get(j)));			
		}
		this.numInsideTransfersLB = calcDiff(uniqueMap);

		//More processing, although maybe not optimal
		for(Integer p: passengerMap.keySet())
		{
			//if there is a unique vehicleID present in a time slot, then remove the time slot
			for(Integer time: new HashMap<Integer, ArrayList<Integer>>(passengerMap.get(p)).keySet())
			{
				ArrayList<Integer> vehicleIDs = passengerMap.get(p).get(time);

				for(Integer vehicleID: vehicleIDs)
				{
					if(uniqueMap.get(p).contains(vehicleID))
					{
						passengerMap.get(p).remove(time);
						break;
					}
				}
			}

			//if there are still time slots left in which a different vehicle was used.
			for(ArrayList<Integer> time: passengerMap.get(p).values())
			{
				for(Integer vehicleID: time)
				{
					if(!uniqueMap.get(p).contains(vehicleID))
					{
						uniqueMap.get(p).add(vehicleID);
						break;
					}
				}
			}
		}
		this.insideTransferListUB = new HashMap<Integer, HashSet<Integer>>(uniqueMap);
		this.numInsideTransfersUB = calcDiff(uniqueMap);
	}

	private int calcDiff(Map<Integer, HashSet<Integer>> uniqueMap)
	{
		int numInsideTransfers = 0;

		for(Integer p: uniqueMap.keySet())
		{
			int numOutsideTransfersPassenger=outsideTransferList.get(p).size();

			numInsideTransfers += Math.max(((uniqueMap.get(p).size()-1)-numOutsideTransfersPassenger), 0);
		}

		return numInsideTransfers;
	}

}
