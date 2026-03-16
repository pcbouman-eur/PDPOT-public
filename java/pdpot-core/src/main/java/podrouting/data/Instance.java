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
import java.util.*;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.alg.shortestpath.KShortestSimplePaths;
import org.jgrapht.graph.DirectedWeightedMultigraph;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedNetwork;
import podrouting.util.HighwayContraction;
import podrouting.util.IOUtils;

@JsonIgnoreProperties({"distanceMatrix","distanceMatrixRoundedUp"})
public class Instance {

	private DirectedWeightedMultigraph<Location,Road> network;

	private List<Passenger> passengers;
	private List<Vehicle> vehicles;

	private double platooningDiscountFactor = 0.05;
	private double transferOutsidePenalty = 2;
	private double drivingPenalty = 1;
	private double arriveEarlyPenalty = -0.1;
	private double rejectionPenalty = 100;
	private boolean strictStopping = true;
	private boolean allowInsideTransfers = true;
	private boolean strictStoppingAtDestination = false;

	private final Map<String,Object> metadata;

	public Instance() {
		this.network = new DirectedWeightedMultigraph<>(Road.class);
		this.passengers = new ArrayList<>();
		this.vehicles = new ArrayList<>();
		this.metadata = new TreeMap<>();
	}

	@JsonCreator
	public Instance(@JsonProperty("locations") List<Location> locations,
			@JsonProperty("roads") List<Road> roads,
			@JsonProperty("passengers") List<Passenger> passengers,
			@JsonProperty("vehicles") List<Vehicle> vehicles,
			@JsonProperty("transferOutsidePenalty") double transferPenalty,
			@JsonProperty("arriveEarlyPenalty") double arriveEarly,
			@JsonProperty("rejectionPenalty") double rejection,
			@JsonProperty("platooningDiscount") double platooningDiscount,
			@JsonProperty(value="metadata") Map<String,Object> metadata,
			@JsonProperty(value="strictStopping") Boolean strictStopping,
			@JsonProperty(value="drivingPenalty") Double drivingPenalty,
			@JsonProperty(value="allowInsideTransfers") Boolean allowInsideTransfers,
			@JsonProperty(value="strictStoppingAtDestination") Boolean strictStoppingAtDestination) {
		this.network = new DirectedWeightedMultigraph<>(Road.class);
		locations.forEach(network::addVertex);
		roads.forEach(r -> {
			network.addEdge(r.getOrigin(), r.getDestination(), r);
			network.setEdgeWeight(r, r.getDistance());
		});
		this.passengers = new ArrayList<>(passengers);
		this.vehicles = new ArrayList<>(vehicles);
		this.transferOutsidePenalty = transferPenalty;
		this.arriveEarlyPenalty = arriveEarly;
		this.rejectionPenalty = rejection;
		this.platooningDiscountFactor = platooningDiscount;
		this.strictStopping = strictStopping==null || strictStopping;
		this.drivingPenalty = drivingPenalty==null ? 1 : drivingPenalty;
		this.allowInsideTransfers = allowInsideTransfers==null || allowInsideTransfers;
		this.strictStoppingAtDestination = strictStoppingAtDestination != null && strictStoppingAtDestination;
		if (metadata == null) {
			this.metadata = new TreeMap<>();
		}
		else {
			this.metadata = new TreeMap<>(metadata);
		}
	}

    public Instance(Instance other) {
        this(other, true);
    }

	public Instance(Instance other, boolean copyMetadata) {
		this();

		other.getLocations().forEach(this::addLocation);
		other.getRoads().forEach(this::addRoad);
		other.getVehicles().forEach(this::addVehicle);
		other.getPassengers().forEach(this::addPassenger);

		this.platooningDiscountFactor = other.platooningDiscountFactor;
		this.transferOutsidePenalty = other.transferOutsidePenalty;
		this.arriveEarlyPenalty = other.arriveEarlyPenalty;
		this.rejectionPenalty = other.rejectionPenalty;
		this.allowInsideTransfers = other.allowInsideTransfers;
		this.drivingPenalty = other.drivingPenalty;
		this.strictStopping = other.strictStopping;
		this.strictStoppingAtDestination = other.strictStoppingAtDestination;
        if (copyMetadata) {
            this.metadata.putAll(other.metadata);
        }
	}

	public Instance(Instance other, List<Passenger> passengers, List<Vehicle> vehicles) {
		this();

		other.getLocations().forEach(this::addLocation);
		other.getRoads().forEach(this::addRoad);

		this.passengers = passengers;
		this.vehicles = vehicles;

		this.platooningDiscountFactor = other.platooningDiscountFactor;
		this.transferOutsidePenalty = other.transferOutsidePenalty;
		this.arriveEarlyPenalty = other.arriveEarlyPenalty;
		this.rejectionPenalty = other.rejectionPenalty;
		this.allowInsideTransfers = other.allowInsideTransfers;
		this.drivingPenalty = other.drivingPenalty;
		this.strictStopping = other.strictStopping;
		this.strictStoppingAtDestination = other.strictStoppingAtDestination;
		this.metadata.putAll(other.metadata);
	}

	public static Instance withSameNetworkAndCostsAs(Instance other) {
		Instance result = new Instance(other);
		result.passengers = new ArrayList<>();
		result.vehicles = new ArrayList<>();
		result.network = new DirectedWeightedMultigraph<>(Road.class);
		other.network.vertexSet()
		.forEach(result.network::addVertex);
		other.network.edgeSet()
		.forEach(e -> result.network.addEdge(e.getOrigin(), e.getDestination(), e));
		return result;
	}

	public static Instance addSink(Instance other) {
		//New sink 
		Location sink = new Location("sink", LocationType.PARKING, 0, 0);

		//Change vehicle destination to sink
		List<Vehicle> vehicles = new ArrayList<>();
		for(Vehicle v: other.vehicles) {
			vehicles.add(new Vehicle(v.getRange(), v.getId(), v.getCapacity(), v.getOrigin(), sink, v.isRepeatable(), v.getVehicleCost()));
		}
		Instance result = makeInstance(other, vehicles);
		result.network.addVertex(sink);
		for(Location loc: other.network.vertexSet()) {
			if(loc.getType()==LocationType.PARKING) {
				Road r = new Road(loc, sink, 0d);
				result.network.addEdge(loc, sink, r);
			}
		}
		return result;
	}

	public static Instance removeSink(Instance other, List<Vehicle> vehicles) {
		//Sink 
		Location sink = null;
		for(Location loc: other.getLocations()) {
			if("sink".equals(loc.getName())) {
				sink = loc;
			}
		}
        Instance result = makeInstance(other, vehicles);
		result.network.removeVertex(sink);
		return result;
	}

    private static Instance makeInstance(Instance other, List<Vehicle> vehicles) {
        Instance result = new Instance(other, other.passengers, vehicles);

        //Remove sink from network
        result.network = new DirectedWeightedMultigraph<>(Road.class);
        other.network.vertexSet()
                .forEach(result.network::addVertex);
        other.network.edgeSet()
                .forEach(e -> result.network.addEdge(e.getOrigin(), e.getDestination(), e));
        return result;
    }

	public static Instance readFromFile(File f) throws IOException {
        return IOUtils.readInstance(f);
	}

	public boolean addLocation(Location l) {
		return network.addVertex(l);
	}

	public boolean addLocations(Location... l) {
		return Arrays.stream(l).map(network::addVertex).reduce(false, (a,b) -> a||b, (a,b) -> a||b);
	}

	public Road addRoad(Location l1, Location l2) {
		Road road = network.addEdge(l1, l2);
		network.setEdgeWeight(road, road.getDistance());
		return road;
	}

	public Road addRoad(Location l1, Location l2, double distance) {
		Road r = new Road(l1,l2,distance);
		addRoad(r);
		return r;
	}

	public void addBiRoad(Location l1, Location l2) {
		Road r1 = new Road(l1, l2);
		network.addEdge(l1, l2, r1);
		Road r2 = new Road(l2, l1);
		network.addEdge(l2,l1, r2);
		network.setEdgeWeight(r1, r1.getDistance());
		network.setEdgeWeight(r2, r2.getDistance());
	}

	public void addBiRoad(Location l1, Location l2, String name) {
		Road r1 = new Road(l1, l2, l1.distanceTo(l2), name);
		Road r2 = new Road(l2, l1, l2.distanceTo(l1), name);
		network.addEdge(l1, l2, r1);
		network.addEdge(l2, l1, r2);
		network.setEdgeWeight(r1, r1.getDistance());
		network.setEdgeWeight(r2, r2.getDistance());
	}

	public void addBiRoad(Location l1, Location l2, double distance) {
		Road r = new Road(l1,l2,distance);
		addRoad(r);
		r = new Road(l2, l1, distance);
		addRoad(r);
	}

	public void addBiRoad(Location l1, Location l2, double distance, String name) {
		Road r = new Road(l1,l2,distance, name);
		addRoad(r);
		r = new Road(l2, l1, distance, name);
		addRoad(r);
	}

	public boolean addRoad(Road r) {
		boolean result = network.addEdge(r.getOrigin(), r.getDestination(), r);
		network.setEdgeWeight(r, r.getDistance());
		return result;
	}

	public void addPassengers(Passenger ... passengers) {
		for (Passenger p : passengers) {
			addPassenger(p);
		}
	}

	public void addPassenger(Passenger p) {
		if (!network.containsVertex(p.getOrigin()) || !network.containsVertex(p.getDestination())) {
			throw new IllegalArgumentException("Passenger locations are not in the network");
		}
		passengers.add(p);
	}

	public void addVehicles(Vehicle ... vehicles) {
		for (Vehicle v : vehicles) {
			addVehicle(v);
		}
	}

	public boolean removePassenger(Passenger p) {
		return passengers.remove(p);
	}

	public void addVehicle(Vehicle v) {
		if (!network.containsVertex(v.getOrigin()) || !network.containsVertex(v.getDestination())) {
			throw new IllegalArgumentException("Vehicle locations are not in the network");
		}
		vehicles.add(v);
	}

	public boolean removeVehicle(Vehicle v) {
		return vehicles.remove(v);
	}

	public List<Passenger> getPassengers() {
		return Collections.unmodifiableList(passengers);
	}

	@JsonIgnore
	public int getMinimumTime() {
		return 0;
	}

	@JsonIgnore
	public int getMaximumTime() {
		ShortestPathAlgorithm<Location, Road> sp = shortestPaths();
		int maxTime = 0;
		for (Passenger p : passengers) {
			for (Vehicle v: vehicles) {
				double time = p.getTimeEnd() + sp.getPathWeight(p.getDestination(), v.getDestination());
				int lastTime = TimedNetwork.roundDistance(time);
				maxTime = Math.max(maxTime, lastTime);
			}
		}
		return maxTime;
	}


	public List<Vehicle> getVehicles() {
		return Collections.unmodifiableList(vehicles);
	}

	public boolean containsRoad(Road e) {
		return network.containsEdge(e);
	}

	public boolean containsLocation(Location v) {
		return network.containsVertex(v);
	}

	public Set<Road> getRoads() {
		return network.edgeSet();
	}

	public Set<Road> getRoadsOf(Location vertex) {
		return network.edgesOf(vertex);
	}

	public Set<Road> getAllEdges(Location sourceVertex, Location targetVertex) {
		return network.getAllEdges(sourceVertex, targetVertex);
	}

	public double getEdgeWeight(Road e) {
		return network.getEdgeWeight(e);
	}

	public Set<Road> incomingEdgesOf(Location vertex) {
		return network.incomingEdgesOf(vertex);
	}

	public Set<Location> getLocations() {
		return network.vertexSet();
	}

	public void addMetadata(String key, Object value) {
		metadata.put(key, value);
	}

	public Map<String,Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	@Override
	public String toString() {
		return "Instance [network=" + network + ", passengers=" + passengers + ", vehicles=" + vehicles + "]";
	}

	public double getPlatooningDiscount() {
		return platooningDiscountFactor;
	}

	public double getTransferOutsidePenalty() {
		return transferOutsidePenalty;
	}

	public double getArriveEarlyPenalty() {
		return arriveEarlyPenalty ;
	}

	public double getRejectionPenalty() {
		return rejectionPenalty;
	}

	public double getDrivingPenalty() {
		return drivingPenalty;
	}

	public boolean isStrictStopping() {
		return strictStopping;
	}

	public void setStrictStopping(boolean strictStopping) {
		this.strictStopping = strictStopping;
	}

	public boolean isAllowInsideTransfers() {
		return allowInsideTransfers;
	}

	public void setAllowInsideTransfers(boolean allowInsideTransfers) {
		this.allowInsideTransfers = allowInsideTransfers;
	}

	public boolean isStrictStoppingAtDestination() {
		return strictStoppingAtDestination;
	}

	public void setStrictStoppingAtDestination(boolean strictStoppingAtDestination) {
		this.strictStoppingAtDestination = strictStoppingAtDestination;
	}

	public double getPlatooningDiscountFactor() {
		return platooningDiscountFactor;
	}

	public void setPlatooningDiscountFactor(double platooningDiscountFactor) {
		this.platooningDiscountFactor = platooningDiscountFactor;
	}

	public void setTransferOutsidePenalty(double transferOutsidePenalty) {
		this.transferOutsidePenalty = transferOutsidePenalty;
	}

	public void setDrivingPenalty(double drivingPenalty) {
		this.drivingPenalty = drivingPenalty;
	}

	public void setArriveEarlyPenalty(double arriveEarlyPenalty) {
		this.arriveEarlyPenalty = arriveEarlyPenalty;
	}

	public void setRejectionPenalty(double rejectionPenalty) {
		this.rejectionPenalty = rejectionPenalty;
	}

	public GraphPath<Location, Road> shortestPath(Location origin, Location destination) {
		return shortestPaths().getPath(origin, destination);
	}

	@JsonIgnore
	public Map<Location,Double> centrality() {
		return centrality(false);
	}

	@JsonIgnore
	public Map<Location,Double> centrality(boolean normalized) {
		BetweennessCentrality<Location,Road> bc = new BetweennessCentrality<>(network, normalized);
		return bc.getScores();
	}

	@JsonIgnore
	public ShortestPathAlgorithm<Location, Road> shortestPaths() {
		return new DijkstraShortestPath<>(network);
	}

	public KShortestPathAlgorithm<Location,Road> kShortestPaths(int k) {
		return new KShortestSimplePaths<>(network, k);
	}

	public double computeVehiclePathCost(Path<Vehicle> path) {
		double result = 0;
		double earlyVehiclePenalty = arriveEarlyPenalty * 0.001;
		Vehicle v = path.getCommodity();
		for (TimedArc ta : path.getPath()) {
			result += ta.getDistance() * drivingPenalty;
			if (ta.getToLocation().equals(v.getDestination())) {
				result += earlyVehiclePenalty;
			}
		}
		return result;
		/*		return path.getPath()
				.stream()
				.mapToDouble(ta -> ta.getDistance() * drivingPenalty)
				.sum();
		 */	}

	public double computePassengerPathCost(Path<Passenger> path) {
		double result = 0;
		Passenger p = path.getCommodity();
		TimedArc prev = null;
		for (TimedArc arc : path.getPath()) {
			if (arc.getPurpose() == ArcPurpose.WAIT_OUT) {
				if (arc.getToLocation().equals(p.getDestination())) {
					result += arriveEarlyPenalty;
				}
				else if (prev != null && prev.getPurpose() != ArcPurpose.WAIT_OUT) {
					result += transferOutsidePenalty;
				}
			}
			prev = arc;
		}
		return result;
	}

	@JsonIgnore
	public Map<Location,Map<Location,Integer>> getRoundedUpDistanceMatrix() {
		FloydWarshallShortestPaths<Location,Road> fwsp = new FloydWarshallShortestPaths<>(getRoundedUpNetwork());
		Map<Location,Map<Location,Integer>> result = new LinkedHashMap<>();
		for (Location loc : network.vertexSet()) {
			Map<Location,Integer> subMap = new LinkedHashMap<>();
			for (Location loc2 : network.vertexSet()) {
				//int dist = (int)Math.ceil(fwsp.getPathWeight(loc, loc2));
				int dist = TimedNetwork.roundDistance(fwsp.getPathWeight(loc, loc2));
				subMap.put(loc2, dist);
			}
			result.put(loc, subMap);
		}
		return result;
	}

	@JsonIgnore
	public ShortestPathAlgorithm<Location,Road> getRoundedUpShortestPaths() {
		return new DijkstraShortestPath<>(getRoundedUpNetwork());
	}

	@JsonIgnore
	public DirectedWeightedMultigraph<Location,Road> getNetwork() {
		DirectedWeightedMultigraph<Location,Road> result = new DirectedWeightedMultigraph<>(Road.class);
		network.vertexSet().forEach(result::addVertex);
		network.edgeSet().forEach(r -> {
			result.addEdge(r.getOrigin(), r.getDestination(), r);
			result.setEdgeWeight(r, r.getDistance());
		});
		return result;
	}

	private DirectedWeightedMultigraph<Location,Road> getRoundedUpNetwork() {
		DirectedWeightedMultigraph<Location,Road> result = new DirectedWeightedMultigraph<>(Road.class);
		network.vertexSet().forEach(result::addVertex);
		network.edgeSet().forEach(r -> {
			int rounded = r.getRoundedDistance();
			Road road = new Road(r.getOrigin(), r.getDestination(), r.getDistance(), r.getName()); //Use the original distance
			result.addEdge(r.getOrigin(), r.getDestination(), road);
			result.setEdgeWeight(road, rounded); //Use the rounded distance
		});
		return result;
	}

	public void contractHighwayNodes() {
		contractHighwayNodes(Double.POSITIVE_INFINITY);
	}

	public void contractHighwayNodes(double maxDist) {
		HighwayContraction hc = new HighwayContraction(network, maxDist);
		hc.run();
	}

	public void clearVehicles() {
		vehicles.clear();
	}

	@JsonIgnore
	public double getPassengersPerSeat() {
		double seats = vehicles.stream()
				.mapToInt(Vehicle::getCapacity)
				.sum();
		return passengers.size() / seats;
	}

	@JsonIgnore
	public double getPassengersPerVehicle() {
		return (1d * passengers.size()) / vehicles.size();
	}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Instance instance)) return false;
        return Double.compare(platooningDiscountFactor, instance.platooningDiscountFactor) == 0
                && Double.compare(transferOutsidePenalty, instance.transferOutsidePenalty) == 0
                && Double.compare(drivingPenalty, instance.drivingPenalty) == 0
                && Double.compare(arriveEarlyPenalty, instance.arriveEarlyPenalty) == 0
                && Double.compare(rejectionPenalty, instance.rejectionPenalty) == 0
                && strictStopping == instance.strictStopping
                && allowInsideTransfers == instance.allowInsideTransfers
                && strictStoppingAtDestination == instance.strictStoppingAtDestination
                && Objects.equals(network, instance.network)
                && Objects.equals(passengers, instance.passengers)
                //&& Objects.equals(vehicles, instance.vehicles)
                && Objects.equals(metadata, instance.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                network,
                passengers,
                //vehicles,
                platooningDiscountFactor,
                transferOutsidePenalty,
                drivingPenalty,
                arriveEarlyPenalty,
                rejectionPenalty,
                strictStopping,
                allowInsideTransfers,
                strictStoppingAtDestination,
                metadata
        );
    }
}