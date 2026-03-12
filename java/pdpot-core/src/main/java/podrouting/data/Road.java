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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;
import podrouting.data.timed.TimedNetwork;

import java.util.Objects;

public class Road {

	private final String name;
	private final Location origin, destination;
	private final double distance;
	
	public Road(Location origin, Location destination) {
		this(origin, destination, origin.distanceTo(destination));
	}
	
	public Road(Location origin, Location destination, double distance) {
		this(origin, destination, distance, null);
	}
	
	@JsonCreator
	public Road(@JsonProperty("origin") Location origin,
			    @JsonProperty("destination") Location destination,
			    @JsonProperty("distance") double distance,
			    @JsonProperty("name") String name) {
		super();
		this.origin = origin;
		this.destination = destination;
		this.distance = distance;
		if (name == null) {
			this.name = origin.getName() + "--" + destination.getName();
		}
		else {
			this.name = name;
		}
	}

	public String getName() {
		return name;
	}
	
	public Location getOrigin() {
		return origin;
	}

	public Location getDestination() {
		return destination;
	}

	public double getDistance() {
		return distance;
	}
	
	@JsonIgnore
	public int getRoundedDistance() {
		return TimedNetwork.roundDistance(distance);
	}


	@Override
	public boolean equals(Object o) {
		// TODO: I had to adjust equals to ignore the distance for the drawing code
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Road road = (Road) o;
		return Objects.equals(name, road.name) && Objects.equals(origin, road.origin) && Objects.equals(destination, road.destination);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, origin, destination);
	}

	@Override
	public String toString() {
		return "Road [origin=" + origin + ", destination=" + destination + ", distance=" + distance + "]";
	}

	public TimedArc toTimedArc(int beginTime) {
		int endTime = beginTime + (int) Math.ceil(getRoundedDistance());
		TimedLocation o = new TimedLocation(origin,beginTime);
		TimedLocation d = new TimedLocation(destination,endTime);
		return new TimedArc(o,d,this);
	}
	
}
