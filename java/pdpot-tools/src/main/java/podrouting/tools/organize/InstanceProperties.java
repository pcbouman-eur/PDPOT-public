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

import org.jgrapht.graph.DirectedWeightedMultigraph;
import podrouting.data.*;
import podrouting.util.Pair;
import podrouting.visualizer.VisualizerIO;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class InstanceProperties {

    public static List<InstanceProperty> getPropertyList(List<String> props) {
        Map<String, InstanceProperty> properties = getProperties();
        List<InstanceProperty> result = new ArrayList<>(props.size());
        for (String propName : props) {
            InstanceProperty property;
            if (propName.startsWith("@")) {
                property = new MetadataProperty(propName.substring(1));
            }
            else {
                property = properties.get(propName);
            }
            if (property == null) {
                throw new IllegalArgumentException("Property name '"+propName+"' is unknown. " +
                        "Supported options are "+properties.keySet());
            }
            result.add(property);
        }
        return result;
    }

    private static Map<String,InstanceProperty> getProperties() {
        Map<String, InstanceProperty> result = new TreeMap<>();
        result.put("network", new NetworkProperty());
        result.put("networksize", new NetworkSizeProperty());
        result.put("topology", new TopologyProperty());
        result.put("demand", new DemandProperty());
        result.put("demandsize", new DemandSizeProperty());
        result.put("supply", new SupplyProperty());
        result.put("supplysize", new SupplySizeProperty());
        result.put("ds", new DemandSupplyProperty());
        result.put("dssize", new DemandSupplySizeProperty());
        return result;
    }

    private static class NodeCount {
        private final Map<InstanceTree, Map<Object,Integer>> counts = new LinkedHashMap<>();

        public int getCount(InstanceTree node, Object object) {
            Map<Object,Integer> subMap = counts.computeIfAbsent(node, ignored -> new LinkedHashMap<>());
            return subMap.computeIfAbsent(object, ignored -> subMap.size());
        }
    }


    public static class NetworkProperty implements InstanceProperty {

        private final NodeCount counts = new NodeCount();

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            DirectedWeightedMultigraph<Location, Road> network = instance.getNetwork();
            int count = counts.getCount(node, network);
            return Pair.of(network, "network_"+count);
        }

        @Override
        public boolean supportsSummary() {
            return true;
        }

        @Override
        public String getSummaryFileName() {
            return "network.png";
        }

        @Override
        public void writeSummaryFile(File output, Stream<Instance> instances) throws IOException {
            Instance i = instances.findAny().get();
            VisualizerIO.writeNetworkPNGImage(i, output);
        }
    }

    public static class NetworkSizeProperty implements InstanceProperty {

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            DirectedWeightedMultigraph<Location, Road> network = instance.getNetwork();
            Pair<Integer,Integer> pair = Pair.of(network.vertexSet().size(), network.edgeSet().size());
            String label = "n"+pair.first+"_e"+pair.second;
            return Pair.of(pair, label);
        }
    }

    public static class TopologyProperty implements InstanceProperty {
        private final NodeCount counts = new NodeCount();

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            DirectedWeightedMultigraph<Location, Road> network = NetworkTopology.withParkingType(instance);
            int count = counts.getCount(node, network);
            return Pair.of(network, "topology_"+count);
        }

        @Override
        public boolean supportsSummary() {
            return true;
        }

        @Override
        public String getSummaryFileName() {
            return "topology.png";
        }

        @Override
        public void writeSummaryFile(File output, Stream<Instance> instances) throws IOException {
            Instance instance = instances.findAny().get();
            VisualizerIO.writeNetworkPNGImage(instance, output);
        }
    }

    public static class DemandProperty implements InstanceProperty {

        private final NodeCount counts = new NodeCount();

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            List<Passenger> passengers = instance.getPassengers();
            int count = counts.getCount(node, passengers);
            return Pair.of(passengers, "ds_"+count);
        }
    }

    public static class DemandSizeProperty implements InstanceProperty {
        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            int count = instance.getPassengers().size();
            return Pair.of(count, "p_"+count);
        }
    }

    public static class SupplyProperty implements InstanceProperty {
        private final NodeCount counts = new NodeCount();

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            List<Vehicle> vehicles = instance.getVehicles();
            int count = counts.getCount(node, vehicles);
            return Pair.of(vehicles, "s_"+count);
        }
    }

    public static class SupplySizeProperty implements InstanceProperty {
        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            int count = instance.getVehicles().size();
            return Pair.of(count, "v_"+count);
        }
    }

    public static class DemandSupplyProperty implements InstanceProperty {

        private final NodeCount counts = new NodeCount();

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            Pair<List<Passenger>, List<Vehicle>> pair = Pair.of(instance.getPassengers(), instance.getVehicles());
            int count = counts.getCount(node, pair);
            return Pair.of(pair, "ds_"+count);
        }
    }


    public static class DemandSupplySizeProperty implements InstanceProperty {

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            int passengers = instance.getPassengers().size();
            int vehicles = instance.getVehicles().size();
            Pair<Integer,Integer> pair = Pair.of(passengers, vehicles);
            String label = "p"+passengers+"_v"+vehicles;
            return Pair.of(pair, label);
        }
    }

    public static class MetadataProperty implements InstanceProperty {

        private final String key;

        public MetadataProperty(String key) {
            this.key = key;
        }

        @Override
        public Pair<?, String> classifyInstance(Instance instance, InstanceTree node) {
            Object obj = instance.getMetadata().get(key);
            if (obj == null) {
                obj = "BLANK";
            }
            String str = obj.toString();
            return Pair.of(obj, str);
        }
    }
}
