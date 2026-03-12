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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import podrouting.data.Location;
import podrouting.data.Road;

@JsonIgnoreProperties(value= {"toLocation","fromLocation","fromTime","toTime", "toWaitInside"})
public class TimedArc implements Comparable<TimedArc> {

	private final Road road;
	private final TimedLocation from;
	private final TimedLocation to;
	private final ArcPurpose purpose;
	private final double distance;
	
	private final int hashCache;
	
	public TimedArc(TimedLocation from, TimedLocation to) {
		this(from, to, ArcPurpose.DRIVE, from.getLocation().distanceTo(to.getLocation()), null);
	}
	
	public TimedArc(TimedLocation from, TimedLocation to, Road road) {
		this(from, to, ArcPurpose.DRIVE, road.getDistance(), road);
	}

	public TimedArc(TimedLocation from, TimedLocation to, ArcPurpose purpose, double dist) {
		this(from, to, purpose, dist, null);
	}
	
	@JsonCreator
	public TimedArc(@JsonProperty("from") TimedLocation from, @JsonProperty("to") TimedLocation to,
			@JsonProperty("purpose") ArcPurpose purpose, @JsonProperty("distance") double dist, @JsonProperty("road") Road road) {
		super();
		this.from = from;
		this.to = to;
		this.purpose = purpose;
		this.distance = dist;
		this.road = road;
		this.hashCache = computeHashCode();
	}
	
	public TimedLocation getFrom() {
		return from;
	}

	public TimedLocation getTo() {
		return to;
	}
	
	public ArcPurpose getPurpose() {
		return purpose;
	}
	
	public Location getFromLocation() {
		return from.getLocation();
	}
	
	public Location getToLocation() {
		return to.getLocation();
	}
	
	public int getFromTime() {
		return from.getTime();
	}
	
	public int getToTime() {
		return to.getTime();
	}
	
	public double getDistance() {
		return distance;
	}
	
	public Road getRoad() {
		return road;
	}

	public TimedArc toWaitInside() {
		if (purpose == ArcPurpose.DRIVE) {
			throw new IllegalStateException("A DRIVE arc can not be converted");
		}
		if (purpose == ArcPurpose.WAIT_IN) {
			return this;
		}
		return new TimedArc(from, to, ArcPurpose.WAIT_IN, distance, road);
	}
	
	public TimedArc toWaitOutside() {
		if (purpose == ArcPurpose.DRIVE) {
			throw new IllegalStateException("A DRIVE arc can not be converted");
		}
		if (purpose == ArcPurpose.WAIT_OUT) {
			return this;
		}
		return new TimedArc(from, to, ArcPurpose.WAIT_OUT, distance, road);
	}

	@JsonIgnore
	public double getHalfwayTime() {
		double duration = getToTime() - getFromTime();
		return getFromTime() + (duration/2);
	}

	public boolean overlaps(TimedArc other) {
		return other.getFromTime() < getToTime() && other.getToTime() > getFromTime();
	}
	
	@Override
	public String toString() {
		return "("+from+","+to+"["+purpose+"])";
		//return "TimedArc [from=" + from + ", to=" + to + ", purpose=" + purpose + "]";
	}

	@Override
	public int hashCode() {
		return hashCache;
	}
	
	private int computeHashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(distance);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + ((purpose == null) ? 0 : purpose.hashCode());
		result = prime * result + ((road == null) ? 0 : road.hashCode());
		result = prime * result + ((to == null) ? 0 : to.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimedArc other = (TimedArc) obj;
		if (Double.doubleToLongBits(distance) != Double.doubleToLongBits(other.distance))
			return false;
		if (from == null) {
			if (other.from != null)
				return false;
		} else if (!from.equals(other.from))
			return false;
		if (purpose != other.purpose)
			return false;
		if (road == null) {
			if (other.road != null)
				return false;
		} else if (!road.equals(other.road))
			return false;
		if (to == null) {
            return other.to == null;
		} else return to.equals(other.to);
    }

	@Override
	public int compareTo(TimedArc other) {
		if (getFromTime() != other.getFromTime()) {
			return getFromTime() - other.getFromTime();
		}
		if (getToTime() != other.getToTime()) {
			return getToTime() - other.getToTime();
		}
		return (int)Math.signum(distance - other.getDistance());
	}
	
}
