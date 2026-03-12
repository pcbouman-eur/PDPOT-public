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

public class PassengerState {

    private final int timestep;
    private final Passenger passenger;
    private final TimedArc arc;
    private final PassengerAction action;
    private final Vehicle start;
    private final Vehicle end;

    private PassengerState(Passenger p, TimedArc arc) {
        if (arc.getPurpose() != ArcPurpose.WAIT_OUT) {
            throw new IllegalArgumentException("This constructor only supports wait-outside vehicle");
        }
        this.timestep = arc.getFrom().getTime();
        this.passenger = p;
        this.arc = arc;
        this.start = null;
        this.end = null;
        if (arc.getFromLocation().equals(p.getOrigin())) {
            this.action = PassengerAction.WAIT_ORIGIN;
        }
        else if (arc.getToLocation().equals(p.getDestination())) {
            this.action = PassengerAction.AT_DESTINATION;
        }
        else {
            this.action = PassengerAction.WAIT_OUTSIDE;
        }
    }

    private PassengerState(Passenger p, TimedArc arc, int timestep, Vehicle startVehicle, Vehicle endVehicle) {
        this.timestep = timestep;
        this.passenger = p;
        this.arc = arc;
        this.start = startVehicle;
        this.end = endVehicle;
        if (startVehicle == null && endVehicle != null) {
            this.action = PassengerAction.ENTER_VEHICLE;
        }
        else if (startVehicle != null && endVehicle == null) {
            this.action = PassengerAction.EXIT_VEHICLE;
        }
        else {
            this.action = PassengerAction.IN_VEHICLE;
        }
    }

    public static PassengerState create(Passenger p, TimedArc arc, int timestep, Vehicle startVehicle, Vehicle endVehicle) {
        if (arc.getPurpose() == ArcPurpose.WAIT_OUT) {
            return new PassengerState(p, arc);
        }
        else {
            return new PassengerState(p, arc, timestep, startVehicle, endVehicle);
        }
    }

    public int getTimestep() {
        return timestep;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public TimedArc getArc() {
        return arc;
    }

    public PassengerAction getAction() {
        return action;
    }

    public Vehicle getStart() {
        return start;
    }

    public Vehicle getEnd() {
        return end;
    }

}
