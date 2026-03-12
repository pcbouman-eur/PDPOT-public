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
package podrouting.visualizer;

import podrouting.data.Road;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;

import java.util.*;

public class RoadIndexTable {

    private int minTime;
    private int maxTime;
    private Map<Road,List<TableNode>> table;
    private Map<Road,Integer> roadIndices;

    private static final Comparator<Road> ROAD_COMPARATOR =
            Comparator.comparing((Road r) -> r.getOrigin().getName())
                      .thenComparing((Road r) -> r.getDestination().getName());

    public RoadIndexTable(Solution sol) {
        this.minTime = sol.getInstance().getMinimumTime();
        this.maxTime = sol.getInstance().getMaximumTime();
        this.table = new HashMap<>();
        this.roadIndices = new TreeMap<>(ROAD_COMPARATOR);
        init(sol);
    }

    private void init(Solution sol) {
        List<VehicleArcPair> pairs = new ArrayList<>();
        for (Path<Vehicle> path : sol.getVehiclePaths()) {
            Vehicle v = path.getCommodity();
            for (TimedArc ta : path.getPath()) {
                if (ta.getRoad() != null) {
                    pairs.add(new VehicleArcPair(v, ta));
                }
            }
        }
        Collections.sort(pairs);
        for (VehicleArcPair pair : pairs) {
            Vehicle v = pair.getVehicle();
            TimedArc ta = pair.getArc();
            List<TableNode> list = getList(ta);
            int index = getAvailableIndex(ta);
            for (int t=ta.getFromTime(); t<ta.getToTime(); t++) {
                TableNode node = list.get(t-minTime);
                node.addVehicle(v, index);
            }
        }
    }

    private int getAvailableIndex(TimedArc ta) {
        int index = 0;
        List<TableNode> list = getList(ta);
        for (int t=ta.getFromTime(); t<=ta.getToTime(); t++) {
            TableNode node = list.get(t-minTime);
            index = Math.max(index, node.getAvailableIndex());
        }
        return index;
    }

    private List<TableNode> getList(TimedArc arc) {
        Road r = arc.getRoad();
        List<TableNode> list = table.get(r);
        if (list != null) {
            return list;
        }
        list = new ArrayList<>();
        for (int t=minTime; t <= maxTime; t++) {
            list.add(new TableNode(t));
        }
        table.put(r, list);
        roadIndices.put(r, roadIndices.size());
        return list;
    }

    public List<Road> getRelevantRoads() {
        return new ArrayList<>(roadIndices.keySet());
    }

    public int getMaxVehicleIndex(Road r) {
        int result = 0;
        List<TableNode> list = table.get(r);
        if (list != null) {
            for (TableNode tn : list) {
                result = Math.max(result, tn.getMaxIndex());
            }
        }
        return result;
    }

    public int getVehicleIndex(Vehicle v, TimedArc ta) {
        Road r = ta.getRoad();
        List<TableNode> list = table.get(r);
        if (list == null) {
            throw new IllegalArgumentException("Invalid road");
        }
        TableNode node = list.get(ta.getFromTime() - minTime);
        return node.getVehicleIndex(v);
    }

    public int getRoadCount() {
        return roadIndices.size();
    }

    public int getVehicleRows() {
        return roadIndices.keySet()
                          .stream()
                          .mapToInt(r -> getMaxVehicleIndex(r) + 1)
                          .sum();
    }

    private static class TableNode {
        private int time;
        private Map<Vehicle,Integer> vehicleIndex;
        private Map<Integer,Vehicle> indexVehicle;

        public TableNode(int time) {
            this.time = time;
            this.vehicleIndex = new HashMap<>();
            this.indexVehicle = new HashMap<>();
        }

        public void addVehicle(Vehicle v, int index) {
            if (indexVehicle.containsKey(index)) {
                throw new IllegalArgumentException("Index "+index+" is already taken");
            }
            if (vehicleIndex.containsKey(v)) {
                throw new IllegalArgumentException("The vehicle "+v.getId()+" is already assigned to an index");
            }
            indexVehicle.put(index, v);
            vehicleIndex.put(v, index);
        }

        public int getAvailableIndex() {
            for (int i=0; i <= indexVehicle.size(); i++) {
                if (!indexVehicle.containsKey(i)) {
                    return i;
                }
            }
            throw new AssertionError("There should always be an index in the range [0,size+1]");
        }

        public int getMaxIndex() {
            if (indexVehicle.isEmpty()) {
                return 0;
            }
            return Collections.max(indexVehicle.keySet());
        }

        public int getVehicleIndex(Vehicle v) {
            return vehicleIndex.get(v);
        }
    }

    private static final class VehicleArcPair implements Comparable<VehicleArcPair> {
        private final Vehicle vehicle;
        private final TimedArc arc;

        private static final Comparator<VehicleArcPair> COMPARATOR =
                Comparator.comparing((VehicleArcPair vap) -> vap.arc.getFromTime())
                          .thenComparing((VehicleArcPair vap) -> vap.arc.getToTime(), Comparator.reverseOrder())
                          .thenComparing((VehicleArcPair vap) -> vap.vehicle.getId());


        public VehicleArcPair(Vehicle v, TimedArc ta) {
            this.vehicle = v;
            this.arc = ta;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public TimedArc getArc() {
            return arc;
        }

        @Override
        public int compareTo(VehicleArcPair o) {
            return COMPARATOR.compare(this, o);
        }
    }
}
