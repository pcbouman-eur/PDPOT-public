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
import com.fasterxml.jackson.annotation.JsonProperty;

import podrouting.data.Location;

public class TimedLocation implements Comparable<TimedLocation> {

	private final Location location;
	private final int time;
	
	private final int hashCache;
	
	@JsonCreator
	public TimedLocation(@JsonProperty("location") Location location, @JsonProperty("time") int time) {
		super();
		this.location = location;
		this.time = time;
		this.hashCache = computeHashCode();
	}

	public Location getLocation() {
		return location;
	}

	public int getTime() {
		return time;
	}

	@Override
	public int hashCode() {
		return hashCache;
	}
	
	private int computeHashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((location == null) ? 0 : location.hashCode());
		result = prime * result + time;
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
		TimedLocation other = (TimedLocation) obj;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
        return time == other.time;
    }

	@Override
	public String toString() {
		return location.getName()+"@"+time;
	}

	@Override
	public int compareTo(TimedLocation o) {
		return time - o.time;
	}
	
}
