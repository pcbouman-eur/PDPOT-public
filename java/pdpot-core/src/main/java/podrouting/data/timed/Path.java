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
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import podrouting.util.Pair;

@JsonIgnoreProperties(value= {"arcPairs", "first"})
public class Path<E> {

	private final E commodity;
	private final List<TimedArc> arcs;
	private final int cachedHash;
	
	@JsonCreator
	public Path(@JsonProperty("commodity") E commodity, @JsonProperty("path") List<TimedArc> arcs) {
		this.commodity = commodity;
		this.arcs = arcs;
		this.cachedHash = computeHash();
	}
	
	public E getCommodity() {
		return commodity;
	}
	
	@JsonIgnore
	public TimedArc getFirst() {
		if (arcs.isEmpty()) {
			return null;
		}
		return arcs.get(0);
	}
	
	@JsonIgnore
	public TimedArc getLast() {
		if (arcs.isEmpty()) {
			return null;
		}
		return arcs.get(arcs.size()-1);
	}
	
	public List<TimedArc> getPath() {
		return Collections.unmodifiableList(arcs);
	}
	
	public double sumArcs(ToDoubleFunction<TimedArc> f) {
		return arcs.stream()
				   .mapToDouble(f)
				   .sum();
	}
	
	public List<Pair<TimedArc,TimedArc>> getArcPairs() {
		if (arcs.isEmpty()) {
			return Collections.emptyList();
		}
		List<Pair<TimedArc,TimedArc>> result = new ArrayList<>(arcs.size()-1);
		for (int t=1; t < arcs.size(); t++) {
			result.add(new Pair<>(arcs.get(t-1),arcs.get(t)));
		}
		return result;
	}
	
	public double sumArcPairs(ToDoubleFunction<Pair<TimedArc,TimedArc>> f) {
		if (arcs.isEmpty()) {
			return 0d;
		}
		double result = 0;
		for (int t=1; t < arcs.size(); t++) {
			result += f.applyAsDouble(new Pair<>(arcs.get(t-1),arcs.get(t)));
		}
		return result;
		
		
		
	}

	
	
	@Override
	public int hashCode() {
		return cachedHash;
	}
	
	private int computeHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((arcs == null) ? 0 : arcs.hashCode());
		result = prime * result + ((commodity == null) ? 0 : commodity.hashCode());
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
		Path<?> other = (Path<?>) obj;
		if (arcs == null) {
			if (other.arcs != null)
				return false;
		} else if (!arcs.equals(other.arcs))
			return false;
		if (commodity == null) {
            return other.commodity == null;
		} else return commodity.equals(other.commodity);
    }

	@Override
	public String toString() {
		return "Path [commodity=" + commodity + ", arcs=" + arcs + "]";
	}
}
