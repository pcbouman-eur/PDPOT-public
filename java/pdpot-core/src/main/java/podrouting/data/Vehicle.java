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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Vehicle {

	public final double range;
	private final int id;
	public final int capacity;
	public final Location origin, destination;
	public final boolean repeatable;
	public final double vehicleCost;
	private final int hashCache;
	
	public Vehicle(int id, int capacity, Location origin, Location destination) {
		this(Double.POSITIVE_INFINITY, id, capacity, origin, destination);
	}
	
	public Vehicle(double range, int id, int capacity, Location origin, Location destination) {
		this(range, id, capacity, origin, destination, false, 0d);
	}
	
	
	@JsonCreator
	public Vehicle(@JsonProperty(value="range",required=true) double range,
			       @JsonProperty(value="id",required=true) int id,
			       @JsonProperty(value="capacity",required=true) int capacity,
			       @JsonProperty(value="origin",required=true) Location origin,
			       @JsonProperty(value="destination",required=true) Location destination,
			       @JsonProperty("repeatable") Boolean repeatable,
			       @JsonProperty("vehicleCost") Double vehicleCost) {
		super();
		this.range = range;
                this.id = id;
		this.capacity = capacity;
		this.origin = origin;
		this.destination = destination;
		this.repeatable = repeatable != null && repeatable;
		this.vehicleCost = vehicleCost==null ? 0 : vehicleCost;
		this.hashCache = computeHash();
	}

	public double getRange() {
		return range;
	}
        
        public int getId() {
		return id;
	}

	public int getCapacity() {
		return capacity;
	}

	public Location getOrigin() {
		return origin;
	}

	public Location getDestination() {
		return destination;
	}

	public boolean isRepeatable() {
		return repeatable;
	}
	
	public double getVehicleCost() {
		return vehicleCost;
	}
	
    @Override
    public int hashCode() {
    	return hashCache;
    }
    
    private int computeHash() {
        int hash = 7;
        hash = 89 * hash + Long.hashCode(Double.doubleToLongBits(this.range));
        hash = 89 * hash + this.id;
        hash = 89 * hash + this.capacity;
        hash = 89 * hash + Objects.hashCode(this.origin);
        hash = 89 * hash + Objects.hashCode(this.destination);
        hash = 89 * hash + Boolean.hashCode(repeatable);
        hash = 89 * hash + Long.hashCode(Double.doubleToLongBits(this.vehicleCost));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
            if (this == obj) {
                    return true;
            }
            if (obj == null) {
                    return false;
            }
            if (getClass() != obj.getClass()) {
                    return false;
            }
            final Vehicle other = (Vehicle) obj;
            if (Double.doubleToLongBits(this.range) != Double.doubleToLongBits(other.range)) {
                    return false;
            }
            if (this.id != other.id) {
                    return false;
            }
            if (this.capacity != other.capacity) {
                    return false;
            }
            if (!Objects.equals(this.origin, other.origin)) {
                    return false;
            }
        return Objects.equals(this.destination, other.destination);
    }

    @Override
    public String toString() {
            return "Vehicle{" + "range=" + range + ", id=" + id + ", capacity=" + capacity + ", origin=" + origin + ", destination=" + destination + '}';
    }
}
