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
package podrouting.tools.cli;

import static podrouting.util.IOUtils.SOLUTION_POSTFIX;
import static podrouting.util.IOUtils.INSTANCE_POSTFIX;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import podrouting.data.Instance;
import podrouting.data.Solution;
import podrouting.tools.organize.LabelMaker;
import podrouting.util.IOUtils;
import podrouting.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name="transform", description="Transform instance or solution files to instance files")
public class TransformMain implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(TransformMain.class);

    @CommandLine.Option(names = {"-i", "--input"}, description="The input directory to search for instances",
        required = true)
    private File inputDir;

    @CommandLine.Option(names = {"-o", "--output"}, description="The output directory to write the instances",
        required = true)
    private File outputDir;

    @CommandLine.Option(names = {"-s", "--solution"}, description="Whether to include solution files for output instances")
    private boolean includeSolutions;

    @CommandLine.Option(names = {"-r", "--rename"}, description="Whether to rename files using a labelling scheme")
    private boolean rename;

    @CommandLine.Option(names = {"-t", "--tag"}, description="Add metadata tags to processed files")
    private Map<String,String> tags;

    @CommandLine.Option(names = {"-a", "--adjust"}, description="Applies particular treatments to instance" +
            " files before writing them, effectively creating newly derived instances.")
    private List<Treatment> treatments;


    private void processInstance(Instance instance) {
        if (treatments == null || treatments.isEmpty()) {
            return;
        }
        for (Treatment t : treatments) {
            t.apply(instance);
        }
        String oldUUID = instance.getMetadata().get("uuid").toString();
        instance.addMetadata("derivedFrom", oldUUID);
        instance.addMetadata("uuid", UUID.randomUUID().toString());
        log.info("Applied treatments {} to instance with uuid {}", treatments, oldUUID);
    }

    @Override
    public Integer call() throws Exception {
        log.info("Collecting instances from directory {}", inputDir);
        List<Pair<Instance,String>> instances = collectInstances();
        int count = 0;
        int size = instances.size();
        log.info("Done collection. {} instances collected.", instances.size());
        if (tags != null && !tags.isEmpty()) {
            log.info("The metadata will be extended with the following properties: {}", tags);
        }
        else {
            log.info("No tags detected.");
        }
        outputDir.mkdirs();
        if (rename) {
            List<Instance> list = instances.stream().map(pair -> pair.first).collect(Collectors.toList());
            LabelMaker lm = new LabelMaker();
            Map<Instance,String> names = lm.nameInstances(list);
            for (Map.Entry<Instance, String> entry : names.entrySet()) {
                count++;
                File out = new File(outputDir, entry.getValue());
                addMetaData(entry.getKey());
                Instance i = entry.getKey();
                processInstance(i);
                IOUtils.writeInstance(i, out);
                log.info("Instance {}/{} written to renamed file {}", count, size, out);
            }
        }
        else {
            for (Pair<Instance,String> pair : instances) {
                count++;
                File out = new File(outputDir, pair.second + INSTANCE_POSTFIX);
                File parent = out.getParentFile();
                parent.mkdirs();
                addMetaData(pair.first);
                processInstance(pair.first);
                IOUtils.writeInstance(pair.first, out);
                log.info("Instance {}/{} written to file {}", count, size, out);
            }
        }
        log.info("Done.");
        return 0;
    }

    private void addMetaData(Instance i) {
        if (tags != null && !tags.isEmpty()) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                i.addMetadata(entry.getKey(), entry.getValue());
            }
        }
    }

    private List<Pair<Instance,String>> collectInstances() {
        List<Pair<Instance,String>> result = new ArrayList<>();
        collectInstances(inputDir, result, "");
        return result;
    }

    private void collectInstances(File dir, List<Pair<Instance,String>> result, String prefix) {
        for (File f : dir.listFiles()) {
            String name = f.getName();
            String nameLc = name.toLowerCase(Locale.ROOT);
            String namePrefix = "";
            Instance instance = null;
            if (f.isDirectory()) {
                collectInstances(f, result, prefix + name + File.separator);
            }
            else if (includeSolutions && nameLc.endsWith(SOLUTION_POSTFIX)) {
                try {
                    Solution sol = IOUtils.readSolution(f);
                    instance = sol.getInstance();
                    namePrefix = prefix + name.substring(0, name.length() - SOLUTION_POSTFIX.length());
                } catch (IOException ex) {
                    log.info("Could not read plausible solution file {}, error: {} {}",
                            f, ex.getClass().getSimpleName(), ex.getMessage());
                }
            }
            else if (nameLc.endsWith(INSTANCE_POSTFIX) && ! nameLc.endsWith(SOLUTION_POSTFIX)) {
                try {
                    instance = IOUtils.readInstance(f);
                    namePrefix = prefix + name.substring(0, name.length() - INSTANCE_POSTFIX.length());
                }
                catch (IOException ex) {
                    log.info("Could not read plausible instance file {}, error {} {}",
                            f, ex.getClass().getSimpleName(), ex.getMessage());
                }
            }
            if (instance != null) {
                result.add(Pair.of(instance, namePrefix));
            }
        }
    }


    public enum Treatment {

        FORBID_INSIDE_TRANSFERS {
            @Override
            public void apply(Instance instance) {
                instance.setAllowInsideTransfers(false);
                addTreatment(instance, this.name());
            }
        },
        FORBID_OUTSIDE_TRANSFERS {
            @Override
            public void apply(Instance instance) {
                int numPassengers = instance.getPassengers().size();
                double rejectionPenalty = Math.max(instance.getRejectionPenalty(),1e6);
                instance.setTransferOutsidePenalty(numPassengers * rejectionPenalty);
                addTreatment(instance, this.name());
            }
        };

        public static final String TREATMENT_METADATA_KEY = "treatment";

        public void addTreatment(Instance instance, String treatment) {
            Map<String,Object> metadata = instance.getMetadata();
            Set<String> treatments = new TreeSet<>();
            if (metadata.containsKey(TREATMENT_METADATA_KEY)) {
                String str = metadata.get(TREATMENT_METADATA_KEY).toString();
                treatments.addAll(Arrays.asList(str.split(",")));
            }
            treatments.add(treatment);
            String str = String.join(",", treatments);
            instance.addMetadata(TREATMENT_METADATA_KEY, str);
        }

        public abstract void apply(Instance instance);
    }

}
