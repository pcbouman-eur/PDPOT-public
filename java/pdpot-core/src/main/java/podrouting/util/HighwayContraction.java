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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jgrapht.graph.DirectedWeightedMultigraph;

import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Road;

public class HighwayContraction {

	private final double maxDistance;
	
	private final DirectedWeightedMultigraph<Location,Road> network;
	
	public HighwayContraction(DirectedWeightedMultigraph<Location,Road> network) {
		this(network, Double.POSITIVE_INFINITY);
	}
	
	public HighwayContraction(DirectedWeightedMultigraph<Location,Road> network, double maxDistance) {
		this.network = network;
		this.maxDistance = maxDistance;
	}

	private void addRoad(Location from, Location to, double distance) {
		Road r = new Road(from, to, distance);
		network.addEdge(from, to, r);
	}


	public void run() 
	{
		//find all candidate nodes
		Set<Location> candidateSet = new LinkedHashSet<Location>();	
		for(Location loc: network.vertexSet())
		{
			if(loc.getType()==LocationType.INTERSECTION)
			{
				candidateSet.add(loc);
			}
		}
		
		//Remove end points of the highway network OR contract highway nodes
		boolean update = true;
		while (update) 
		{
			update = false;
			for (Location loc: candidateSet) 
			{			
				if(endPoint(loc)) 
				{ 
					network.removeVertex(loc);
					candidateSet.remove(loc);
					update = true;
					break;
				}
				else if(consecutiveEdgesOneDirection(loc, maxDistance))
				{		
					network.removeVertex(loc);
					candidateSet.remove(loc);
					update = true;
					break;
				}
				else if(consecutiveEdgesTwoDirections(loc, maxDistance))
				{
					network.removeVertex(loc);
					candidateSet.remove(loc);
					update = true;
					break;
				}
				else if(consecutiveEdgesAsymmetric(loc, maxDistance))
				{
					network.removeVertex(loc);
					candidateSet.remove(loc);
					update = true;
					break;
				}
			}
		}
	}

	private boolean consecutiveEdgesAsymmetric(Location loc, double maxDistance)
	{
		if(network.inDegreeOf(loc)==1 && network.outDegreeOf(loc)==2)
		{
			Iterator<Road> roadInIter = network.incomingEdgesOf(loc).iterator();
			Iterator<Road> roadOutIter = network.outgoingEdgesOf(loc).iterator();

			Road roadIn1 = roadInIter.next();
			Road roadOut1 = roadOutIter.next();
			Road roadOut2 = roadOutIter.next();

			Location roadIn1Source = network.getEdgeSource(roadIn1);
			Location roadOut1Target = network.getEdgeTarget(roadOut1);
			Location roadOut2Target = network.getEdgeTarget(roadOut2);

			//Check if new Source and Target are also highways
			if(loc.getType()==roadIn1Source.getType() && loc.getType()==roadOut1Target.getType() &&
					loc.getType()==roadOut2Target.getType())
			{
				if(roadIn1Source.equals(roadOut1Target) && !roadIn1Source.equals(roadOut2Target))
				{
					double totalDistance = roadIn1.getDistance()+roadOut2.getDistance();

					if(totalDistance<maxDistance)
					{
						addRoad(roadIn1Source, roadOut2Target, totalDistance);
						return true;
					}
				}
				else if(roadIn1Source.equals(roadOut2Target) && !roadIn1Source.equals(roadOut1Target))
				{
					double totalDistance = roadIn1.getDistance()+roadOut1.getDistance();

					if(totalDistance<maxDistance)
					{
						addRoad(roadIn1Source, roadOut1Target, totalDistance);
						return true;
					}
				}
			}
		}
		else if(network.inDegreeOf(loc)==2 && network.outDegreeOf(loc)==1)
		{
			Iterator<Road> roadInIter = network.incomingEdgesOf(loc).iterator();
			Iterator<Road> roadOutIter = network.outgoingEdgesOf(loc).iterator();

			Road roadIn1 = roadInIter.next();
			Road roadIn2 = roadInIter.next();
			Road roadOut1 = roadOutIter.next();

			Location roadIn1Source = network.getEdgeSource(roadIn1);
			Location roadIn2Source = network.getEdgeSource(roadIn2);
			Location roadOut1Target = network.getEdgeTarget(roadOut1);

			//Check if new Source and Target are also highways
			if(loc.getType()==roadIn1Source.getType() && loc.getType()==roadOut1Target.getType() &&
					loc.getType()==roadIn2Source.getType())
			{
				if(roadOut1Target.equals(roadIn1Source) && !roadOut1Target.equals(roadIn2Source))
				{
					double totalDistance = roadOut1.getDistance()+roadIn2.getDistance();

					if(totalDistance<maxDistance)
					{
						addRoad(roadOut1Target, roadIn2Source, totalDistance);
						return true;
					}
				}
				else if(roadOut1Target.equals(roadIn2Source) && !roadOut1Target.equals(roadIn1Source))
				{
					double totalDistance = roadOut1.getDistance()+roadIn1.getDistance();

					if(totalDistance<maxDistance)
					{
						addRoad(roadOut1Target, roadIn1Source, totalDistance);
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean consecutiveEdgesTwoDirections(Location loc, double maxDistance)
	{
		if(network.inDegreeOf(loc)==2 && network.outDegreeOf(loc)==2)
		{
			Iterator<Road> roadInIter = network.incomingEdgesOf(loc).iterator();
			Iterator<Road> roadOutIter = network.outgoingEdgesOf(loc).iterator();

			Road roadIn1 = roadInIter.next();
			Road roadIn2 = roadInIter.next();
			Road roadOut1 = roadOutIter.next();
			Road roadOut2 = roadOutIter.next();

			Location roadIn1Source = network.getEdgeSource(roadIn1);
			Location roadIn2Source = network.getEdgeSource(roadIn2);
			Location roadOut1Target = network.getEdgeTarget(roadOut1);
			Location roadOut2Target = network.getEdgeTarget(roadOut2);

			//Check if new Source and Target are also highways
			if(loc.getType()==roadIn1Source.getType() && loc.getType()==roadIn2Source.getType() &&
					loc.getType()==roadOut1Target.getType() && loc.getType()==roadOut2Target.getType())
			{
				if(roadIn1Source.equals(roadOut1Target) && roadIn2Source.equals(roadOut2Target))
				{
					double totalDistance1 = roadIn1.getDistance()+roadOut2.getDistance();
					double totalDistance2 = roadIn2.getDistance()+roadOut1.getDistance();

					if(totalDistance1<maxDistance && totalDistance2<maxDistance)
					{
						addRoad(roadIn1Source, roadOut2Target, totalDistance1);
						addRoad(roadIn2Source, roadOut1Target, totalDistance2);						
						return true;
					}
				}
				else if(roadIn2Source.equals(roadOut1Target) && roadIn1Source.equals(roadOut2Target))
				{
					double totalDistance1 = roadIn1.getDistance()+roadOut1.getDistance();
					double totalDistance2 = roadIn2.getDistance()+roadOut2.getDistance();

					if(totalDistance1<maxDistance && totalDistance2<maxDistance)
					{
						addRoad(roadIn1Source, roadOut1Target, totalDistance1);
						addRoad(roadIn2Source, roadOut2Target, totalDistance2);						
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean consecutiveEdgesOneDirection(Location loc, double maxDistance)
	{
		if(network.inDegreeOf(loc)==1 && network.outDegreeOf(loc)==1)
		{
			Road roadIn = network.incomingEdgesOf(loc).iterator().next();
			Road roadOut = network.outgoingEdgesOf(loc).iterator().next();

			Location roadInSource = network.getEdgeSource(roadIn);
			Location roadOutTarget = network.getEdgeTarget(roadOut);

			//Check if new Source and Target are also highways
			if(loc.getType()==roadInSource.getType() && loc.getType()==roadOutTarget.getType())
			{
				//Source and target are different nodes
				if(!roadInSource.equals(roadOutTarget))
				{			
					//Do not exceed a certain distance
					double totalDistance = roadIn.getDistance()+roadOut.getDistance();
					if(totalDistance<maxDistance)
					{
						addRoad(roadInSource, roadOutTarget, totalDistance);
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean endPoint(Location loc)
	{
		if((network.inDegreeOf(loc)==0 && network.outDegreeOf(loc)==1 )||(network.inDegreeOf(loc)==1 && network.outDegreeOf(loc)==0 ))
		{
			return true;
		}
		else if((network.inDegreeOf(loc)==1) && network.outDegreeOf(loc)==1)
		{
			Set<Road> neighbourEdges = network.edgesOf(loc);
			Iterator<Road> iter = neighbourEdges.iterator();
			Road road1 = iter.next();
			Road road2 = iter.next();

            return network.getEdgeSource(road1).equals(network.getEdgeTarget(road2)) && network.getEdgeSource(road2).equals(network.getEdgeTarget(road1));
		}
		return false;
	}
}
