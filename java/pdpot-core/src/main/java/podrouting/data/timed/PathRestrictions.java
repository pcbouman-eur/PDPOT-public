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

import java.util.LinkedHashSet;
import java.util.Set;

public class PathRestrictions {

	private final Set<TimedArc> forbidden;
	private final Set<TimedArc> enforced;
	
	public PathRestrictions() {
		this.forbidden = new LinkedHashSet<>();
		this.enforced = new LinkedHashSet<>();
	}

	public void enforceArc(TimedArc arc) {
		enforced.add(arc);
	}
	
	public void forbidArc(TimedArc arc) {
		forbidden.add(arc);
	}
	
	public void clearArc(TimedArc arc) {
		enforced.remove(arc);
		forbidden.remove(arc);
	}
	
	public boolean available(Path<?> path) {
		return path.getPath()
				    .stream()
				    .allMatch(this::available);
	}
	
	public boolean available(TimedArc arc) {
		if (forbidden.contains(arc)) {
			return false;
		}
		if (enforced.contains(arc)) {
			return true;
		}
		// If the arc is not enforced itself, but overlaps
		// with some enforced arc, this one is forbidden
		for (TimedArc other : enforced) {
			if (other.overlaps(arc)) {
				return false;
			}
		}
		return true;
	}
	
	public boolean hasInteralOverlap() {
		for (TimedArc ta1 : enforced) {
			for (TimedArc ta2 : enforced) {
				if (ta1 != ta2 && ta1.overlaps(ta2)) {
					return true;
				}
			}
		}
		return false;
	}
	
}
