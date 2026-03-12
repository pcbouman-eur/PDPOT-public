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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.ToDoubleFunction;


import podrouting.data.LocationType;
import podrouting.data.Passenger;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.PathRestrictions;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;
import podrouting.data.timed.TimedNetwork;

public class PathFinder {

	private final boolean strictStopping;
	private final boolean strictStoppingAtDestination;
	
	private final TimedNetwork network;
	private final TimedLocation [] locs;
	private final TimedLocation origin;
    private final TimedLocation destination;

	private final Map<TimedLocation,Node> nodeMap;

	private ToDoubleFunction<TimedArc> arcCost, arcDual;
	private ToDoubleFunction<Pair<TimedArc,TimedArc>> pairCost, pairDual;

	private PathRestrictions restrictions;

	public PathFinder(TimedNetwork nw, TimedLocation origin, TimedLocation destination) {
		this.network = nw;
		this.locs = nw.getTopologicalOrder();
		if (locs.length == 0) {
			throw new IllegalArgumentException("A timed network should not be empty!");
		}
		this.origin = origin;
		this.destination = destination;

		if (!locs[0].equals(origin) || !locs[locs.length-1].equals(destination)) {
			throw new IllegalArgumentException("The origin should be the first node and the "
					+ "destination the last node in the topological order of the network");
		}

		this.nodeMap = new HashMap<>();
		for (TimedLocation tl : locs) {
			nodeMap.put(tl, new Node());
		}

		this.strictStopping = nw.getInstance().isStrictStopping();
		this.strictStoppingAtDestination = nw.getInstance().isStrictStoppingAtDestination();
	}

	public void setCosts(ToDoubleFunction<TimedArc> arcCosts, ToDoubleFunction<Pair<TimedArc,TimedArc>> pairCosts) {
		this.arcCost = arcCosts;
		this.pairCost = pairCosts;
	}

	public void setDuals(ToDoubleFunction<TimedArc> arcDual, ToDoubleFunction<Pair<TimedArc,TimedArc>> pairDual) {
		this.arcDual = arcDual;
		this.pairDual = pairDual;
	}

	public double getCost(TimedArc arc) {
		return arcCost.applyAsDouble(arc);
	}

	public double getCost(Pair<TimedArc,TimedArc> pair) {
		return pairCost.applyAsDouble(pair);
	}

	public double getDual(TimedArc arc) {
		return arcDual.applyAsDouble(arc);
	}

	public double getDual(Pair<TimedArc,TimedArc> pair) {
		return pairDual.applyAsDouble(pair);
	}

	private <E> void computeNodeMap(E commodity) {
		// We use the nodeMap to keep track of states per node
		// So at the start of the algorithm we have to reset it
		nodeMap.values().forEach(Node::reset);
		nodeMap.get(origin).addState(null, 0, null);
		// We iterate over the locations in their topological order
		// as locs is created according to the topological order of
		// the locations in the timed network
		for (TimedLocation cur : locs) {
			Node node = nodeMap.get(cur);
			// We consider every incoming TimedArc which has previously
			// been expanded to this location, and the costs associated
			// with that arc.
			for (Entry<TimedArc,Double> state : node.stateSet()) {
				TimedArc prev = state.getKey();
				double oldCost = state.getValue();
				// We consider every outgoing arc from this location for expansion.
				for (TimedArc out : network.getOutgoingTimedArcs(cur)) {
					if (restrictions != null && !restrictions.available(out)) {
						// We do not expand over arcs that are unavailable in this branch
						continue;
					}
					//System.out.println(commodity.toString() +": " + commodity.getClass().equals(Passenger.class));

					//if(commodity.getClass().equals(Passenger.class)) {
					if (commodity instanceof Passenger) {
						//Only relevant for passengers
						if(prev==null && out.getPurpose()==ArcPurpose.DRIVE ) {
							//Not allowed for a passenger to leave immediately from the origin
							continue;
						}
						if(out.getPurpose().equals(ArcPurpose.DRIVE)  && out.getTo().equals(destination)) {
							//The last arc to the destination is not allowed to be a driving arc
							continue;
						}
						// Do not allow a DRIVE directly after a WAIT_OUT
						if (out != null && out.getPurpose().equals(ArcPurpose.DRIVE) && prev.getPurpose().equals(ArcPurpose.WAIT_OUT)) {
							continue;
						}
						if (strictStoppingAtDestination && out.getFromLocation().equals(destination.getLocation()) && out.getPurpose().equals(ArcPurpose.DRIVE)) {
							//When passenger arrives at its destination, it cannot drive away anymore
							continue;
						}
					}

					if (commodity instanceof Vehicle) {
						// In case of strict, do not allow two consecutive WAIT_IN's at a stop
						if (strictStopping
								&& cur.getLocation().getType() == LocationType.STOP
								&& out.getPurpose() == ArcPurpose.WAIT_IN
								&& prev != null
								&& prev.getPurpose() == ArcPurpose.WAIT_IN) {
							continue;
						}
					}

					// Add the cost of the new arc
					double newCost = oldCost + arcCost.applyAsDouble(out);
					if (arcDual != null) {
						// Subtract the dual cost of that single arc
						newCost -= arcDual.applyAsDouble(out);
					}
					// If we reached this location using an arc (i.e. it is not the source)
					// We must also consider the pair costs and pair based dual
					if (prev != null) {
						Pair<TimedArc,TimedArc> pair = Pair.of(prev, out);
						newCost += pairCost.applyAsDouble(pair);
						if (pairDual != null) {
							newCost -= pairDual.applyAsDouble(pair);
						}
					}
					// Now that we have computed the new costs and have reached a new
					// location, we can add this new state, along with the arc that
					// was used to reach it to the states of the new location.
					Node destNode = nodeMap.get(out.getTo());
					destNode.addState(out, newCost, prev);
				}
			}
		}
	}

	public <E> Pair<Path<E>,Double> findPath(E commodity) {
		computeNodeMap(commodity);
		// After all locations have been processed, we can compute the path with
		// the least costs to the destination node
		Path<E> path = constructPath(commodity);
		double cost = nodeMap.get(destination).getBestCost();
		if (Double.isFinite(cost)) {
			return Pair.of(path, cost);
		}
		else {
			return null;
		}
	}
	
	public <E> List<Pair<Path<E>, Double>> findPaths(E commodity, int numPaths) {
		computeNodeMap(commodity);
		// After all locations have been processed, we can compute the paths with
		// the least costs to the destination node
		return constructPaths(commodity, numPaths);
	}

	private <E> Path<E> constructPath(E commodity, Pair<TimedArc,TimedArc> last) {
		List<TimedArc> arcs = new ArrayList<>();
		Pair<TimedArc,TimedArc> cur = last;
		while (cur != null) {
			arcs.add(cur.first);
			if (cur.second != null) {
				cur = Pair.of(cur.second, nodeMap.get(cur.first.getFrom()).getPrev(cur.second));
			}
			else {
				cur = null;
			}
		}
		Collections.reverse(arcs);
		return new Path<>(commodity, arcs);
	}

	private <E> Path<E> constructPath(E commodity) {
		return constructPath(commodity, nodeMap.get(destination).getBest());
	}

	private <E> List<Pair<Path<E>, Double>> constructPaths(E commodity, int numPaths) {
		List<Pair<Path<E>, Double>> pathList = new ArrayList<>();
		for (Pair<Pair<TimedArc, TimedArc>, Double> pair: nodeMap.get(destination).sortPaths(numPaths)) {
			Path<E> path = constructPath(commodity, pair.first);
			double cost = pair.second;
			if (Double.isFinite(cost)) {
				pathList.add(Pair.of(path, cost));
			}
		}
		return pathList;
	}

	private static class Node {
		private final Map<TimedArc,Double> costs = new LinkedHashMap<>();
		private final Map<TimedArc,TimedArc> prevs = new LinkedHashMap<>();

		public void reset() {
			costs.clear();
			prevs.clear();
		}

		public void addState(TimedArc state, double cost, TimedArc prev) {
			Double curCost = costs.get(state);
			if (curCost == null || curCost > cost) {
				costs.put(state, cost);
				prevs.put(state,prev);
			}
		}

		public Set<Entry<TimedArc,Double>> stateSet() {
			return costs.entrySet();
		}

		public double getBestCost() {
			return costs.values()
					.stream()
					.mapToDouble(d -> d)
					.min()
					.orElse(Double.POSITIVE_INFINITY);
		}

		public Pair<TimedArc,TimedArc> getBest() {
			double best = Double.POSITIVE_INFINITY;
			TimedArc res = null;
			for (Entry<TimedArc,Double> e : costs.entrySet()) {
				if (e.getValue() < best) {
					best = e.getValue();
					res = e.getKey();
				}
			}
			return Pair.of(res, prevs.get(res));
		}

		public TimedArc getPrev(TimedArc arc) {
			return prevs.get(arc);
		}

		public PriorityQueue<Pair<Pair<TimedArc, TimedArc>,Double>> sortPaths(int numPaths) {
			Comparator<Pair<Pair<TimedArc, TimedArc>,Double>> comp = (p1, p2) -> p2.second.compareTo(p1.second);
			PriorityQueue<Pair<Pair<TimedArc, TimedArc>,Double>> queue = new PriorityQueue<>(numPaths+1, comp);

			for (Entry<TimedArc,Double> e : costs.entrySet()) {
				queue.add(Pair.of(Pair.of(e.getKey(), prevs.get(e.getKey())), e.getValue()));
				trimToSize(queue, numPaths);
			}
			return queue;
		}

		private static void trimToSize(PriorityQueue<?> q, int maxSize) {
			while (q.size() > maxSize) {
				q.remove();
			}
		}
	}

	public void setRestrictions(PathRestrictions pres) {
		this.restrictions = pres;
	}

	/*
	public void setDefaultVehicleArcCosts(Vehicle v) {
		setCosts(TimedArc::getDistance, pair -> 0d);
	}
	 */
	public void setDefaultVehicleArcCosts(Vehicle v) {
		double earlyVehiclePenalty = 0.001 * network.getInstance().getArriveEarlyPenalty();
		double drivingPenalty = network.getInstance().getDrivingPenalty();
		setCosts(arc -> {
			double drivingCosts = arc.getDistance()*drivingPenalty;
			if (arc.getToLocation().equals(v.getDestination())) {
				return drivingCosts+earlyVehiclePenalty;
			}
			return drivingCosts;
		}
		, pair -> 0d);
	}

	public void setDefaultArcCosts(Passenger p) {
		double arriveEarlyPenalty = network.getInstance().getArriveEarlyPenalty();
		double transferOutsidePenalty = network.getInstance().getTransferOutsidePenalty();
		setCosts(arc -> {
			if (arc.getPurpose() == ArcPurpose.WAIT_OUT && arc.getToLocation().equals(p.getDestination())) {
				return arriveEarlyPenalty;
			}
			return 0d;
		}, pair -> {
			if (pair.first.getPurpose() != ArcPurpose.WAIT_OUT && pair.second.getPurpose() == ArcPurpose.WAIT_OUT
					&& !pair.second.getToLocation().equals(p.getDestination())) {
				return transferOutsidePenalty;
			}
			return 0d;
		});
	}

}
