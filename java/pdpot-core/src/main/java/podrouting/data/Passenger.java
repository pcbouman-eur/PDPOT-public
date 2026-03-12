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
import com.fasterxml.jackson.annotation.JsonProperty;

public class Passenger {

	private final int id;
	private final Location origin, destination;
	private final int timeStart, timeEnd;
	private final int hashCache;

	public Passenger(int id, Location origin, Location destination) {
		this(id, origin, destination, 0, Integer.MAX_VALUE);
	}

	@JsonCreator
	public Passenger(@JsonProperty("id") int id,
			         @JsonProperty("origin") Location origin,
			         @JsonProperty("destination") Location destination,
			         @JsonProperty("timeStart") int timeStart,
			         @JsonProperty("timeEnd") int timeEnd) {
		super();
		if (timeStart < 0 || timeStart > timeEnd) {
			throw new IllegalArgumentException("Time start should be non-negative and smaller than timeEnd");
		}
		this.id = id;
		this.origin = origin;
		this.destination = destination;
		this.timeStart = timeStart;
		this.timeEnd = timeEnd;
		this.hashCache = computeHash();
	}

	public int getId() {
		return id;
	}

	public Location getOrigin() {
		return origin;
	}

	public Location getDestination() {
		return destination;
	}

	public int getTimeStart() {
		return timeStart;
	}

	public int getTimeEnd() {
		return timeEnd;
	}


	
	@Override
	public int hashCode() {
		return hashCache;
	}
	
	private int computeHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destination == null) ? 0 : destination.hashCode());
		result = prime * result + id;
		result = prime * result + ((origin == null) ? 0 : origin.hashCode());
		result = prime * result + timeEnd;
		result = prime * result + timeStart;
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
		Passenger other = (Passenger) obj;
		if (destination == null) {
			if (other.destination != null)
				return false;
		} else if (!destination.equals(other.destination))
			return false;
		if (id != other.id)
			return false;
		if (origin == null) {
			if (other.origin != null)
				return false;
		} else if (!origin.equals(other.origin))
			return false;
		if (timeEnd != other.timeEnd)
			return false;
        return timeStart == other.timeStart;
    }

	@Override
	public String toString() {
		return "Passenger [id=" + id + ", origin=" + origin + ", destination=" + destination + ", timeStart="
				+ timeStart + ", timeEnd=" + timeEnd + "]";
	}

}
