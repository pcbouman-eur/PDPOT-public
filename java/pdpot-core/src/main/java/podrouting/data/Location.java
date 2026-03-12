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

import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.TimedArc;
import podrouting.data.timed.TimedLocation;

public class Location {

	private final String name;
	private final LocationType type;
	private final double x,y;
	private final int hashCache;
	
	public Location(String name) {
		this.x = Double.NaN;
		this.y = Double.NaN;
		this.type = LocationType.STOP;
		this.name = name;
		hashCache = computeHash();
	}
	
	public Location(String name, LocationType type) {
		this.x = Double.NaN;
		this.y = Double.NaN;
		this.type = type;
		this.name = name;
		hashCache = computeHash();
	}
	
	@JsonCreator
	public Location(@JsonProperty("name") String name,
			        @JsonProperty("type") LocationType type,
			        @JsonProperty("x") double x,
			        @JsonProperty("y") double y) {
		super();
		this.name = name;
		this.type = type;
		this.x = x;
		this.y = y;
		hashCache = computeHash();
	}
	
	public Location(LocationType type, double x, double y) {
		this("("+x+","+y+")", type, x, y);
	}

	public Location(String name, double x, double y) {
		this(name, LocationType.STOP, x, y);
	}

	
	public Location(double x, double y) {
		this(LocationType.STOP, x, y);
	}

	public String getName() {
		return name;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}
	
	public LocationType getType() {
		return type;
	}
	
	public double distanceTo(Location other) {
		double dx = this.x - other.x;
		double dy = this.y - other.y;
		return Math.sqrt(dx*dx + dy*dy);
	}

	@Override
	public int hashCode() {
		return hashCache;
	}
	
	private int computeHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + Double.hashCode(x);
        result = prime * result + Double.hashCode(y);
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
		Location other = (Location) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (type != other.type)
			return false;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		return Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
	}

	@Override
	public String toString() {
		return "Location [name=" + name + ", type=" + type + ", x=" + x + ", y=" + y + "]";
	}

	public TimedArc toWaitArc(int time, ArcPurpose purpose) {
		TimedLocation from = new TimedLocation(this,time);
		TimedLocation to = new TimedLocation(this,time+1);
		return new TimedArc(from,to,purpose,0);
	}

	public TimedLocation toTimedLocation(int time) {
		return new TimedLocation(this, time);
	}

	public Location withType(LocationType type) {
		return new Location(name, type, x, y);
	}
	
}
