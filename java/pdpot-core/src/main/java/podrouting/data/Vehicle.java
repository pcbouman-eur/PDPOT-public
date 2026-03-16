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
    public boolean equals(Object o) {
        if (!(o instanceof Vehicle vehicle)) return false;
        return Double.compare(range, vehicle.range) == 0
                && id == vehicle.id
                && capacity == vehicle.capacity
                && repeatable == vehicle.repeatable
                && Double.compare(vehicleCost, vehicle.vehicleCost) == 0
                && Objects.equals(origin, vehicle.origin)
                && Objects.equals(destination, vehicle.destination);
    }

    private int computeHash() {
        return Objects.hash(range, id, capacity, origin, destination, repeatable, vehicleCost);
    }

    @Override
    public int hashCode() {
    	return hashCache;
    }



    @Override
    public String toString() {
            return "Vehicle{" + "range=" + range + ", id=" + id + ", capacity=" + capacity + ", origin=" + origin + ", destination=" + destination + '}';
    }
}
