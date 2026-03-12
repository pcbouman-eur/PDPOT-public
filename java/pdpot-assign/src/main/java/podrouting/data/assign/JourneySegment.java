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
import podrouting.data.timed.TimedArc;

import java.util.ArrayList;
import java.util.List;

public class JourneySegment {

    private final SegmentType type;
    private final Vehicle vehicleStart;
    private final Vehicle vehicleEnd;
    private final double fromTime;
    private final double toTime;
    private final List<TimedArc> arcs;

    private JourneySegment(SegmentType type, Vehicle vehicle1, Vehicle vehicle2, List<TimedArc> arcs, double start, double end) {
        if (arcs.isEmpty()) {
            throw new IllegalArgumentException("At least one arc is needed");
        }
        this.fromTime = start;
        this.toTime = end;
        this.type = type;
        this.vehicleStart = vehicle1;
        this.vehicleEnd = vehicle2;
        this.arcs = new ArrayList<>(arcs);
    }

    public JourneySegment(Passenger p, List<TimedArc> arcs, Vehicle v, double start, double end) {
        this(p, arcs, v, v, start, end);
    }

    public JourneySegment(Passenger p, List<TimedArc> arcs, Vehicle v1, Vehicle v2, double start, double end) {
        this(determineType(p, arcs, v1, v2), v1, v2, arcs, start, end);
    }

    public SegmentType getType() {
        return type;
    }

    public Vehicle getVehicleStart() {
        return vehicleStart;
    }

    public Vehicle getVehicleEnd() {
        return vehicleEnd;
    }

    public double getFromTime() {
        return fromTime;
    }

    public double getToTime() {
        return toTime;
    }

    public List<TimedArc> getArcs() {
        return arcs;
    }

    public boolean isStartInVehicle() {
        return vehicleStart != null;
    }

    public boolean isEndInVehicle() {
        return vehicleEnd != null;
    }

    @Override
    public String toString() {
        return "JourneySegment{" +
                "type=" + type +
                ", vehicleStart=" + (vehicleStart == null ? "null" : vehicleStart.getId()) +
                ", vehicleEnd=" + (vehicleEnd == null ? "null" : vehicleEnd.getId()) +
                ", fromTime=" + fromTime +
                ", toTime=" + toTime +
                ", arcs=" + arcs +
                '}';
    }

    private static SegmentType determineType(Passenger p, List<TimedArc> arcs, Vehicle v1, Vehicle v2) {
        if (arcs == null || arcs.isEmpty()) {
            throw new IllegalArgumentException("At least one arc is needed");
        }
        if (v1 == null && v2 == null) {
            if (p.getOrigin().equals(arcs.get(0).getFromLocation())) {
                return SegmentType.AT_ORIGIN;
            }
            if (p.getDestination().equals(arcs.get(arcs.size()-1).getToLocation())) {
                return SegmentType.AT_DESTINATION;
            }
            return SegmentType.WAITING;
        }
        if  (v1 == v2) {
            return SegmentType.IN_VEHICLE;
        }
        if (v1 != null && v2 != null) {
            return SegmentType.TRANSFER_VEHICLE;
        }
        if (v1 != null) {
            return SegmentType.EXIT_VEHICLE;
        }
        return SegmentType.ENTER_VEHICLE;
    }

    public enum SegmentType {
        AT_ORIGIN,
        WAITING,
        AT_DESTINATION,
        IN_VEHICLE,
        ENTER_VEHICLE,
        EXIT_VEHICLE,
        TRANSFER_VEHICLE
    }

}
