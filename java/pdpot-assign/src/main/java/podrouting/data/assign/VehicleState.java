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
package podrouting.data.assign;

import podrouting.data.Passenger;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.TimedArc;

import java.util.Collections;
import java.util.Set;

public class VehicleState {

    private final int timestep;
    private final Vehicle vehicle;
    private final TimedArc arc;
    private final Set<Passenger> start;
    private final Set<Passenger> end;

    public VehicleState(Vehicle v, TimedArc arc, int step, Set<Passenger> start, Set<Passenger> end) {
        if (arc.getPurpose() != ArcPurpose.WAIT_OUT) {
            throw new IllegalArgumentException("This constructor only supports wait-outside vehicle");
        }
        this.timestep = arc.getFrom().getTime();
        this.arc = arc;
        this.start = start;
        this.end = end;
        this.vehicle = v;
    }

    public int getTimestep() {
        return timestep;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public TimedArc getArc() {
        return arc;
    }

    public Set<Passenger> getStart() {
        return Collections.unmodifiableSet(start);
    }

    public Set<Passenger> getEnd() {
        return Collections.unmodifiableSet(end);
    }

}
