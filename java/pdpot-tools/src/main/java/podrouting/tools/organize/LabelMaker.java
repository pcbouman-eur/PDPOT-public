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
package podrouting.tools.organize;

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.Passenger;
import podrouting.data.Vehicle;
import podrouting.util.IOUtils;
import podrouting.util.Pair;

import java.util.*;

public class LabelMaker {

    private static final String doubleFormat = "%.3e";

    private final Set<String> exclude = new HashSet<>();
    private final Set<String> include = new HashSet<>();

    public LabelMaker() {
    }

    public LabelMaker(Collection<String> exclude, Collection<String> include) {
        if (include != null) {
            this.include.addAll(include);
        }
        if (exclude != null) {
            this.exclude.addAll(exclude);
        }
    }

    public Map<Instance, String> nameInstances(Collection<Instance> instances) {
        int digits = (int)Math.floor(Math.log10(instances.size()))+1;
        String formatString = "instance_%0"+digits+"d";

        Map<Instance, Map<String,String>> labelMap = new LinkedHashMap<>();
        Map<String,Set<String>> trackUnique = new LinkedHashMap<>();
        for (Instance i : instances) {
            Map<String,String> labels = instanceToLabels(i);
            labelMap.put(i, labels);
            for (Map.Entry<String,String> entry : labels.entrySet()) {
                Set<String> unique = trackUnique.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>());
                unique.add(entry.getValue());
            }
        }

        Map<Instance, String> instanceNames = new LinkedHashMap<>();
        List<Instance> sortedInstances = new ArrayList<>(instances);
        sortedInstances.sort(labelMapComparator(labelMap));
        int index = 0;
        for (Instance i : sortedInstances) {
            Map<String,String> labels = labelMap.get(i);
            StringBuilder name = new StringBuilder();
            name.append(String.format(Locale.ROOT, formatString, ++index));
            for (Map.Entry<String,Set<String>> entry : trackUnique.entrySet()) {
                String key = entry.getKey();
                if (include.contains(key)
                    || (!exclude.contains(key) && entry.getValue().size() > 1)) {
                    name.append("-");
                    name.append(key);
                    name.append("_");
                    name.append(labels.get(key));
                }
            }
            name.append(IOUtils.INSTANCE_POSTFIX);
            instanceNames.put(i, name.toString());
        }
        return instanceNames;
    }

    private Comparator<Instance> labelMapComparator(Map<Instance,Map<String,String>> labelMap) {
        // Start with a trivial identity comparator
        Comparator<Instance> cmp = Comparator.comparingInt(instance -> 0);
        Collection<String> keys = labelMap.values().stream()
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("The label map cannot be empty"))
                .keySet();
        for (String key : keys) {
            // Expand the comparator on a key-by-key basis
            // TODO: it would be nice to have to have a number-sensitive comparator for the strings here
            cmp = cmp.thenComparing(instance -> labelMap.get(instance).get(key));
        }
        return cmp;
    }

    public static Map<String,String> instanceToLabels(Instance instance) {
        Map<String,String> result = new LinkedHashMap<>();

        Set<Pair<Location, Location>> odPairSet = new HashSet<>();
        Set<Pair<Integer, Integer>> timeWindowSet = new HashSet<>();
        for (Passenger p : instance.getPassengers()) {
            odPairSet.add(Pair.of(p.getOrigin(), p.getDestination()));
            timeWindowSet.add(Pair.of(p.getTimeStart(), p.getTimeEnd()));
        }

        result.put("locs", ""+instance.getLocations().size());
        result.put("roads", ""+instance.getRoads().size());
        result.put("pax", ""+instance.getPassengers().size());
        result.put("veh", ""+instance.getVehicles().size());
        result.put("tdiff", ""+(instance.getMaximumTime() - instance.getMinimumTime()));
        result.put("maxSeat", ""+instance.getVehicles().stream().mapToInt(Vehicle::getCapacity).max().orElse(0));
        result.put("odPairs", ""+odPairSet.size());
        result.put("tws", ""+timeWindowSet.size());

        result.put("aep", String.format(Locale.ROOT, doubleFormat, instance.getArriveEarlyPenalty()));
        result.put("dp", String.format(Locale.ROOT, doubleFormat, instance.getDrivingPenalty()));
        result.put("pd", String.format(Locale.ROOT, doubleFormat, instance.getPlatooningDiscount()));
        result.put("pdf", String.format(Locale.ROOT, doubleFormat, instance.getPlatooningDiscountFactor()));
        result.put("rp", String.format(Locale.ROOT, doubleFormat, instance.getRejectionPenalty()));
        result.put("top", String.format(Locale.ROOT, doubleFormat, instance.getTransferOutsidePenalty()));

        boolean inside = instance.isAllowInsideTransfers();
        boolean strict = instance.isStrictStopping();
        boolean strictDest = instance.isStrictStoppingAtDestination();
        String label = (inside ? "i" : "") + (strict ? "s" : "") + (strictDest ? "d" : "");
        result.put("type", label);

        return result;
    }

}
