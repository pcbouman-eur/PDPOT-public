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
package podrouting.data.timed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Road;
import podrouting.util.Pair;

public class TimedNetwork {

	public static double MINIMUM_DISTANCE = 1;
	public static double THRESHOLD = 1e-2; 
	
	private final Instance instance;
	private final DirectedWeightedMultigraph<TimedLocation, TimedArc> network;

	private TimedNetwork(Instance instance, DirectedWeightedMultigraph<TimedLocation, TimedArc> network) {
		this.instance = instance;
		this.network = network;
	}
	
	public TimedNetwork(Instance instance) {
		this.instance = instance;
		this.network = new DirectedWeightedMultigraph<>(TimedArc.class);
		init();
	}

	/**
	 * Rounds the distance up, with some threshold tolerance, and applies a minimum distance.
	 * This is necessary for time discretization and to enforce the time expanded network will
	 * remain a DAG.
	 * @param input the distance to round
	 * @return an integer that is the distance rounded up, with some threshold and minimum distance applied
	 */
	public static int roundDistance(double input) {
		return (int) Math.max(MINIMUM_DISTANCE, Math.ceil(input - THRESHOLD));
	}
	
	private void init() {

		int min = instance.getMinimumTime();
		int max = instance.getMaximumTime();
		for (Location l : instance.getLocations()) {
			TimedLocation prev = null;
			for (int time = min; time <= max; time++) {
				TimedLocation tl = new TimedLocation(l, time);
				network.addVertex(tl);

				if (l.getType() != LocationType.INTERSECTION && prev != null) {
					TimedArc arc = new TimedArc(prev, tl, ArcPurpose.WAIT_IN, 0);
					network.addEdge(prev, tl, arc);
					arc = new TimedArc(prev, tl, ArcPurpose.WAIT_OUT, 0);
					network.addEdge(prev, tl, arc);
				}
				prev = tl;
			}
		}

		for (Road r : instance.getRoads()) {
			int dist = roundDistance(r.getDistance());
			for (int time = min; time + dist <= max; time++) {
				TimedLocation origin = new TimedLocation(r.getOrigin(), time);
				TimedLocation destination = new TimedLocation(r.getDestination(), time+dist);
				TimedArc arc = new TimedArc(origin, destination, r);
				network.addEdge(origin, destination, arc);
			}
		}
	}

	public TimedLocation [] getTopologicalOrder() {
		TimedLocation [] result = new TimedLocation[network.vertexSet().size()];
		int index=0;
		TopologicalOrderIterator<TimedLocation,TimedArc> it = new TopologicalOrderIterator<>(network);
		while (it.hasNext()) {
			result[index++] = it.next();
		}
		return result;
	}
	
	public Instance getInstance() {
		return instance;
	}

	public DirectedWeightedMultigraph<TimedLocation, TimedArc> getNetwork() {
		return network;
	}

	public Set<TimedLocation> getTimedLocations() {
		return network.vertexSet();
	}

	public Set<TimedArc> getIncomingTimedArcs(TimedLocation vertex) {
		return network.incomingEdgesOf(vertex);
	}

	public Set<TimedArc> getOutgoingTimedArcs(TimedLocation vertex) {
		return network.outgoingEdgesOf(vertex);
	}

	public Set<TimedArc> getIncidentTimedArcs(TimedLocation vertex) {
		return network.edgesOf(vertex);
	}

	public Set<TimedArc> getAllArcs() {
		return network.edgeSet();
	}
	
	public Set<Pair<TimedArc,TimedArc>> getConsecutiveArcPairs() {
		Set<Pair<TimedArc,TimedArc>> set = new LinkedHashSet<>();
		for (TimedArc arc : network.edgeSet()) {
			for (TimedArc arc2 : network.outgoingEdgesOf(arc.getTo())) {
				Pair<TimedArc,TimedArc> pair = new Pair<>(arc,arc2);
				set.add(pair);
			}
		}
		return set;
	}

	public Set<TimedArc> getCommonArcs() {
		return network.edgeSet().stream()
				.filter(arc -> arc.getPurpose().equals(ArcPurpose.DRIVE) || arc.getPurpose().equals(ArcPurpose.WAIT_IN))
				.collect(Collectors.toSet());
	}

	public Set<TimedArc> getDrivingArcs() {
		return network.edgeSet().stream().filter(arc -> arc.getPurpose().equals(ArcPurpose.DRIVE))
				.collect(Collectors.toSet());
	}

	public Set<TimedArc> getWaitingInsideArcs() {
		return network.edgeSet().stream().filter(arc -> arc.getPurpose().equals(ArcPurpose.WAIT_IN))
				.collect(Collectors.toSet());
	}

	public Set<TimedArc> getWaitingOutsideArcs() {
		return network.edgeSet().stream().filter(arc -> arc.getPurpose().equals(ArcPurpose.WAIT_OUT))
				.collect(Collectors.toSet());
	}
	
	public Set<TimedLocation> getIsolatedLocations() {
		return network.vertexSet()
				      .stream()
				      .filter(v -> network.degreeOf(v) == 0)
				      .collect(Collectors.toSet());
	}

	public TimedNetwork filter(Iterable<TimedLocation> remove) {
		DirectedWeightedMultigraph<TimedLocation,TimedArc> reduced;
		reduced = new DirectedWeightedMultigraph<>(TimedArc.class);
		Graphs.addGraph(reduced, network);
		for (TimedLocation tl : remove) {
			reduced.removeVertex(tl);
		}
		return new TimedNetwork(instance, reduced);
	}

	public TimedNetwork filterArcs(Predicate<TimedArc> remove) {
		DirectedWeightedMultigraph<TimedLocation,TimedArc> reduced;
		reduced = new DirectedWeightedMultigraph<>(TimedArc.class);
		Graphs.addGraph(reduced, network);
		List<TimedArc> removeList = new ArrayList<>();
		for (TimedArc ta : reduced.edgeSet()) {
			if (remove.test(ta)) {
				removeList.add(ta);
			}
		}
		reduced.removeAllEdges(removeList);
		return new TimedNetwork(instance, reduced);
	}

	@Override
	public String toString() {
		return "TimedNetwork [instance=" + instance + ", network=" + network + "]";
	}

}
