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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Passenger;
import podrouting.data.Vehicle;
import podrouting.data.timed.TimedArc;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class SeatAssignment {

    private static final Logger log = LoggerFactory.getLogger(SeatAssignment.class);
    private static final boolean DEFAULT_STRICT_MODE = true;

    private final Vehicle vehicle;
    private Map<TimedArc,Map<Passenger,Integer>> startAssignment;
    private Map<TimedArc,Map<Passenger,Integer>> endAssignment;
    private final boolean strict = DEFAULT_STRICT_MODE;

    public SeatAssignment(Vehicle vehicle, Map<TimedArc,Set<Passenger>> start, Map<TimedArc,Set<Passenger>> end,
                          BiPredicate<Passenger,TimedArc> willDisembark) {
        this.vehicle = vehicle;
        computeAssignment(start, end, willDisembark);
    }

    public int getSeatIndex(TimedArc arc, Passenger p, double time) {
        // TODO: the defaultValue is now 0, which sometimes seems to happen when the first person enters
        if (time < arc.getHalfwayTime()) {
            return startAssignment.getOrDefault(arc, Collections.emptyMap()).getOrDefault(p, 0);
        }
        else {
            return endAssignment.getOrDefault(arc, Collections.emptyMap()).getOrDefault(p, 0);
        }
    }

    private void computeAssignment(Map<TimedArc,Set<Passenger>> start,
                               Map<TimedArc,Set<Passenger>> end,
                               BiPredicate<Passenger,TimedArc> willDisembark) {
        startAssignment = new HashMap<>();
        endAssignment = new HashMap<>();
        Set<Passenger> current = new HashSet<>();
        Passenger [] seats = new Passenger[vehicle.getCapacity()+2];
        Map<Passenger,Integer> assignmentMap = new HashMap<>();

        List<TimedArc> arcs = start.keySet()
                                   .stream()
                                   .sorted(Comparator.comparing(TimedArc::getFromTime))
                                   .toList();

        for (TimedArc arc : arcs) {
            Set<Passenger> arcSetStart = start.get(arc);
            if (log.isTraceEnabled()) {
                log.trace("Processing start of arc {}, passenger set from assignment: {}", arc, simplify(arcSetStart));
            }
            // Remove all passengers who have actually arrived if this is a WAIT_IN arc
            if (log.isTraceEnabled()) {
                log.trace("Passenger set after removing arrivals: {}", simplify(arcSetStart));
            }
            process(seats, assignmentMap, current, arcSetStart, arc);
            Map<Passenger,Integer> copy = new HashMap<>(assignmentMap);
            startAssignment.put(arc, copy);
            Set<Passenger> arcSetEnd = end.get(arc);
            if (log.isTraceEnabled()) {
                log.trace("Processing end of arc {}, passenger set from assignment: {}", arc, simplify(arcSetEnd));
            }
            process(seats, assignmentMap, arcSetStart, arcSetEnd, arc);
            copy = new HashMap<>(assignmentMap);
            endAssignment.put(arc, copy);
            current = arcSetEnd;
            if (log.isTraceEnabled()) {
                log.trace("Current set after processing both halves of arc {}: {}", arc, simplify(current));
            }
        }
    }

    private void process(Passenger [] seats, Map<Passenger,Integer> assignmentMap, Set<Passenger> current,
                         Set<Passenger> newSet, TimedArc arc) {
        Set<Passenger> leaving, entering;
        if (current == null && newSet == null) {
            return;
        }
        else if (current == null) {
            leaving = Collections.emptySet();
            entering = newSet;
        }
        else if (newSet == null) {
            leaving = current;
            entering = Collections.emptySet();
        }
        else {
            leaving = current.stream()
                    .filter(p -> !newSet.contains(p))
                    .collect(Collectors.toSet());
            entering = newSet.stream()
                    .filter(p -> !current.contains(p))
                    .collect(Collectors.toSet());
        }
        if (log.isTraceEnabled()) {
            log.trace("Processing arc {} for vehicle {}, current: {}, leaving: {}, entering: {}", arc, vehicle.getId(),
                    ids(current), ids(leaving), ids(entering));
            log.trace("Seat assignment before processing: {}", simplify(assignmentMap));
        }
        int curSize = current == null ? 0 : current.size();
        for (Passenger p : leaving) {
            int idx = assignmentMap.remove(p);
            seats[idx] = null;
        }
        if (strict && curSize + entering.size() - leaving.size() > vehicle.getCapacity()) {
            throw new IllegalStateException("Solution seems to violate vehicle "+vehicle.getId()+
                    "'s capacity of "+vehicle.getCapacity()+ ". Current "+ids(current)+", entering: "
                    +ids(entering)+", leaving: "+ids(leaving) + " on arc "+arc+".");
        }
        int index = 0;
        for (Passenger p : entering) {
            while (seats[index] != null) {
                index++;
            }
            if (strict && index >= vehicle.getCapacity()) {
                throw new IllegalStateException("Could not find an empty seat, it seems this solution violates capacity.");
            }
            assignmentMap.put(p,index);
            seats[index] = p;
        }
        if (log.isTraceEnabled()) {
            log.trace("Seat assignment after processing: {}", simplify(assignmentMap));
        }
    }

    private static List<Integer> ids(Collection<Passenger> pax) {
        if (pax == null) {
            return Collections.emptyList();
        }
        return pax.stream().map(Passenger::getId).sorted().collect(Collectors.toList());
    }

    private static Map<Integer,Integer> simplify(Map<Passenger,Integer> map) {
        Map<Integer,Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Passenger,Integer> e : map.entrySet()) {
            result.put(e.getKey().getId(), e.getValue());
        }
        return result;
    }

    private static List<Integer> simplify(Set<Passenger> pax) {
        if (pax == null) {
            return Collections.emptyList();
        }
        return pax.stream()
                  .map(Passenger::getId)
                  .sorted()
                  .collect(Collectors.toList());
    }

}
