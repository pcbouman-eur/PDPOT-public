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

import hu.webarticum.treeprinter.SimpleTreeNode;
import hu.webarticum.treeprinter.printer.listing.ListingTreePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Instance;
import podrouting.util.IOUtils;
import podrouting.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InstanceTree {

    private static final Logger log = LoggerFactory.getLogger(InstanceTree.class);

    private final boolean isLeaf;
    private final String label;

    // In case this is a leaf
    private List<Pair<File, Instance>> instances;

    // In case this is an internal node
    private Map<Object,InstanceTree> subTrees;
    private InstanceProperty property;
    private List<InstanceProperty> remainingProperties;

    public InstanceTree(List<InstanceProperty> properties) {
        this(null, properties);
    }

    private InstanceTree(String label, List<InstanceProperty> properties) {
        this.label = label;
        if (properties.isEmpty()) {
            // This is a leaf
            this.isLeaf = true;
            this.instances = new ArrayList<>();
        }
        else {
            this.isLeaf = false;
            this.subTrees = new LinkedHashMap<>();
            this.property = properties.get(0);
            this.remainingProperties = new ArrayList<>(properties.subList(1,properties.size()));
        }
    }

    public void addInstanceFile(File instanceFile) throws IOException {
        Instance instance = IOUtils.readInstance(instanceFile);
        addInstance(Pair.of(instanceFile, instance));
    }

    public void addInstance(Pair<File,Instance> instancePair) {
        if (isLeaf) {
            instances.add(instancePair);
        }
        else {
            Pair<?,String> pair = property.classifyInstance(instancePair.second, this);
            InstanceTree tree = subTrees.computeIfAbsent(pair.first,
                    ignored -> new InstanceTree(pair.second, remainingProperties));
            tree.addInstance(instancePair);
        }
    }

    public void writeTree(File outputDir, LabelMaker lm, boolean writeSummaries) throws IOException {
        if (isLeaf) {
            Map<Instance,String> filenames = Collections.emptyMap();
            if (lm != null) {
                filenames = lm.nameInstances(instances.stream().map(p -> p.second).collect(Collectors.toList()));
            }

            outputDir.mkdirs();

            int digits = (int)Math.floor(Math.log10(instances.size()))+1;
            String formatString = "%0"+digits+"d";

            int instanceCount = 0;
            for (Pair<File,Instance> instancePair : instances) {
                String instanceLabel = String.format(formatString, instanceCount);
                String filename = filenames.getOrDefault(instancePair.second,
                        instanceLabel + "_" + instancePair.first.getName());
                File outputFile = new File(outputDir, filename);
                IOUtils.writeInstance(instancePair.second, outputFile);
                instanceCount++;
            }
        }
        else {
            for (InstanceTree subTree : subTrees.values()) {
                if (subTrees.size() == 1) {
                    // If there is only one subtree, skip creating extra directories
                    subTree.writeTree(outputDir, lm, writeSummaries);
                    if (writeSummaries) {
                        writeSummary(outputDir, label, subTree.streamInstances());
                    }
                }
                else {
                    File subdir = new File(outputDir, subTree.label);
                    subTree.writeTree(subdir, lm, writeSummaries);
                    if (writeSummaries) {
                        writeSummary(subdir, subTree.label, subTree.streamInstances());
                    }
                }
            }
        }
    }

    public void writeSummary(File outputDir, String label, Stream<Instance> instances) throws IOException {
        if (!isLeaf && property.supportsSummary()) {
            File outputFile = new File(outputDir, property.getSummaryFileName());
            log.info("Writing summary for {} to {}", label, outputFile);
            property.writeSummaryFile(outputFile, instances);
        }
    }

    public Stream<Instance> streamInstances() {
        if (isLeaf) {
            return instances.stream().map(p -> p.second);
        }
        return subTrees.values()
                .stream()
                .flatMap(InstanceTree::streamInstances);
    }

    public int getInstanceCount() {
        if (isLeaf) {
            return instances.size();
        }
        int result = 0;
        for (InstanceTree tree : subTrees.values()) {
            result += tree.getInstanceCount();
        }
        return result;
    }

    public String getOverview() {
        SimpleTreeNode tree = constructTree(null);
        return ListingTreePrinter.builder().ascii().build().stringify(tree);
    }

    private SimpleTreeNode constructTree(SimpleTreeNode node) {
        SimpleTreeNode child;
        if (node != null) {
            String treeLabel = label + " (" + getInstanceCount() + " instances)";
            child = new SimpleTreeNode(treeLabel);
        }
        else {
            child = new SimpleTreeNode(". ("+getInstanceCount()+" instances)");
        }
        if (!isLeaf) {
            for (InstanceTree tree : subTrees.values()) {
                tree.constructTree(child);
            }
        }
        if (node != null) {
            node.addChild(child);
        }
        return child;
    }

}
