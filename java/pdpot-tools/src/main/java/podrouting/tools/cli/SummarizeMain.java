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

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import podrouting.data.*;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "summarize", mixinStandardHelpOptions = true,
        description = "Compute summary statistics from instances and solutions")
public class SummarizeMain implements Callable<Integer> {

    public static Logger log = LoggerFactory.getLogger(SummarizeMain.class);

    @CommandLine.Option(names={"-h", "--hash"}, description="Perform hash based comparison on instances ignoring metadata")
    private boolean performHashing;

    @CommandLine.Option(names = {"-o", "--output"}, description="Output file to write report to")
    private File outputReport;

    @CommandLine.Parameters(description = "Directories or zip files to scan for instance and/or solution files", arity = "1..*")
    private List<File> inputs;

    private final Set<Instance> uniqueInstances = new LinkedHashSet<>();
    private final Set<IOUtils.Reference> jsonFiles = new LinkedHashSet<>();
    private final Set<String> uniqueInstanceUUIDs = new LinkedHashSet<>();
    private final Map<String,Set<String>> solversPerInstanceUUID = new LinkedHashMap<>();
    private final Map<String,Long> uniqueSolvers = new TreeMap<>();
    private final Map<Integer, Integer> vehicleCountToUniqueInstances = new LinkedHashMap<>();
    private final Map<Integer, Integer> passengerCountToUniqueInstances = new LinkedHashMap<>();
    private final Map<PurposeSequence,Long> passengerSequenceCounts = new LinkedHashMap<>();
    private final Map<PurposeSequence,Long> vehicleSequenceCounts = new LinkedHashMap<>();
    private final SortedMap<Integer,Integer> uniqueVehicleCapacities = new TreeMap<>();
    private final SortedMap<Integer,Integer> uniqueVehicleCounts = new TreeMap<>();
    private final SortedMap<Integer,Integer> uniquePassengerCounts = new TreeMap<>();
    private final SortedSet<TimeWindowPair> uniqueTimeWindows = new TreeSet<>();
    private int totalInstancesFound = 0;
    private int totalUniqueInstances = 0;
    private int instancesWithSingleVehicleOrigin = 0;
    private int instancesWithMultipleVehicleOrigins = 0;
    private int instancesWithSingleVehicleDestination = 0;
    private int instancesWithMultipleVehicleDestinations = 0;
    private int instancesWithSingleVehicleODPairs = 0;
    private int instancesWithMultipleVehicleODPairs = 0;


    @Override
    public Integer call() throws IOException {
        inputs.forEach(this::scanDirectory);
        processFiles();
        String report = printStatistics();
        log.info("Summary statistics report:\n\n{}", report);
        if (outputReport != null) {
            try {
                Files.writeString(outputReport.toPath(), report, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error writing summary statistics to file {}", outputReport, e);
            }
        }
        return 0;
    }

    private void scanDirectory(File directory) {
        int old = jsonFiles.size();
        if (IOUtils.isZipFile(directory)) {
            log.info("Scanning zip file {}", directory);
            try {
                jsonFiles.addAll(IOUtils.scanZipForJsonReferences(directory));
            }
            catch (IOException e) {
                log.error("Error while scanning zip file {}", directory, e);
            }
        }
        else if (!directory.exists() || !directory.isDirectory()) {
            log.warn("Directory does not exist or is not a directory: {}", directory);
            return;
        }
        log.info("Scanning directory {}", directory);
        try {
            jsonFiles.addAll(IOUtils.scanForFileReferences(directory));
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", directory, e);
        }
        int scanned = jsonFiles.size() - old;
        log.info("Scanned {} json files", scanned);
    }

    private void processFiles() {
        ProgressBarBuilder progressBarBuilder = new ProgressBarBuilder()
                .setTaskName("Processing files")
                .setStyle(ProgressBarStyle.ASCII);

        for (IOUtils.Reference ref : ProgressBar.wrap(jsonFiles, progressBarBuilder)) {
            String source = ref.source();
            try {
                if (ref.solution()) {
                    processSolution(ref.readSolution(), source);
                }
                else {
                    processInstance(ref.readInstance(), source);
                }
            } catch (IOException e) {
                log.warn("File {} is not a valid instance or solution JSON file", source);
            }
        }
    }

    private void processSolution(Solution solution, String sourceFile) {
        Instance instance = solution.getInstance();
        if (instance != null) {
            processInstance(instance, sourceFile);
            String uuid = instance.getMetadata().get("uuid").toString();
            String solver = solution.getMetadata().get("solver").toString();
            uniqueSolvers.merge(solver, 1L, Long::sum);
            solversPerInstanceUUID.computeIfAbsent(uuid, ignored -> new TreeSet<>()).add(solver);
        } else {
            log.warn("Solution file {} has null instance", sourceFile);
        }
        solution.getPassengerPaths().forEach(p -> countSequences(p, passengerSequenceCounts));
        solution.getVehiclePaths().forEach(p -> countSequences(p, vehicleSequenceCounts));
    }

    private void processInstance(Instance instance, String sourceFile) {
        String uuid = instance.getMetadata().get("uuid").toString();

        // Track total instances
        totalInstancesFound++;

        // Track unique instances
        if (uniqueInstanceUUIDs.add(uuid)) {
            totalUniqueInstances++;
            processUniqueInstance(instance);
        }

        if (performHashing) {
            Instance derived = new Instance(instance, false);
            uniqueInstances.add(derived);
        }
    }

    private void processUniqueInstance(Instance instance) {
        int vehicleCount = instance.getVehicles().size();
        int passengerCount = instance.getPassengers().size();

        // Track vehicle count distribution
        uniqueVehicleCounts.merge(vehicleCount, 1, Integer::sum);
        vehicleCountToUniqueInstances.merge(vehicleCount, 1, Integer::sum);

        // Track passenger count distribution
        uniquePassengerCounts.merge(passengerCount, 1, Integer::sum);
        passengerCountToUniqueInstances.merge(passengerCount, 1, Integer::sum);

        // Collect unique vehicle capacities
        Set<Integer> instanceCapacities = new HashSet<>();
        for (var vehicle : instance.getVehicles()) {
            instanceCapacities.add(vehicle.getCapacity());
        }
        for (int capacity : instanceCapacities) {
            uniqueVehicleCapacities.merge(capacity, 1, Integer::sum);
        }

        // Count unique origins and destinations
        Set<Location> origins = new HashSet<>();
        Set<Location> destinations = new HashSet<>();
        Set<ODPair> pairs = new HashSet<>();
        for (Vehicle v : instance.getVehicles()) {
            origins.add(v.getOrigin());
            destinations.add(v.getDestination());
            pairs.add(new ODPair(v.getOrigin(), v.getDestination()));
        }

        if (origins.size() == 1) {
            instancesWithSingleVehicleOrigin++;
        } else {
            instancesWithMultipleVehicleOrigins++;
        }

        if (destinations.size() == 1) {
            instancesWithSingleVehicleDestination++;
        } else {
            instancesWithMultipleVehicleDestinations++;
        }

        if (pairs.size() == 1) {
            instancesWithSingleVehicleODPairs++;
        }
        else {
            instancesWithMultipleVehicleODPairs++;
        }

        // Collect unique time windows
        for (Passenger p : instance.getPassengers()) {
            uniqueTimeWindows.add(new TimeWindowPair(p.getTimeStart(), p.getTimeEnd()));
        }
    }

    private String printStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Instance Statistics ===\n\n");

        sb.append("Total instances found: " + totalInstancesFound);
        sb.append("\nUnique instances (by UUID): " + totalUniqueInstances);
        if (performHashing) {
            sb.append("\nUnique instances (ignoring metadata): " + uniqueInstances.size());
        }

        sb.append("\n\nInstances with a single vehicle origin: " + instancesWithSingleVehicleOrigin);
        sb.append("\nInstances with multiple vehicle origins: " + instancesWithMultipleVehicleOrigins);

        sb.append("\n\nInstances with a single vehicle destination: " + instancesWithSingleVehicleDestination);
        sb.append("\nInstances with multiple vehicle destinations: " + instancesWithMultipleVehicleDestinations);

        sb.append("\n\nInstances with a single vehicle OD pair: " + instancesWithSingleVehicleODPairs);
        sb.append("\nInstances with multiple vehicle OD pairs: " + instancesWithMultipleVehicleODPairs);


        sb.append("\n\n=== Vehicle Capacity Statistics ===");
        for (var entry : uniqueVehicleCapacities.entrySet()) {
            int capacity = entry.getKey();
            int count = entry.getValue();
            sb.append("\nVehicle capacity " + capacity + " occurs in " + count + " instance(s)");
        }

        sb.append("\n\n=== Vehicle Count Statistics ===");
        for (var entry : uniqueVehicleCounts.entrySet()) {
            int count = entry.getKey();
            int instances = entry.getValue();
            sb.append("\nVehicle count " + count + ": " + instances + " instance(s)");
        }

        sb.append("\n\n=== Passenger Count Statistics ===");
        for (var entry : uniquePassengerCounts.entrySet()) {
            int count = entry.getKey();
            int instances = entry.getValue();
            sb.append("\nPassenger count " + count + ": " + instances + " instance(s)");
        }

        sb.append("\n\n=== Time Window Statistics ===");
        sb.append("\nUnique timeStart/timeEnd pairs: " + uniqueTimeWindows.size());
        for (TimeWindowPair tw : sortedTimeWindows(uniqueTimeWindows)) {
            sb.append("\n  timeStart=" + tw.timeStart + ", timeEnd=" + tw.timeEnd);
        }

        if (!solversPerInstanceUUID.isEmpty()) {
            sb.append("\n\n=== Solver Statistics ===");
            for (var entry : uniqueSolvers.entrySet()) {
                sb.append("\n "+entry.getKey()+" : " + entry.getValue()+" times");
            }
            Map<Integer,Long> counts = new TreeMap<>();
            for (var entry : solversPerInstanceUUID.entrySet()) {
                int count = entry.getValue().size();
                counts.merge(count, 1L,  Long::sum);
            }
            sb.append("\n\n=== Unique Solvers per UUID ===");
            for (var entry : counts.entrySet()) {
                sb.append("\n "+entry.getKey()+" solvers : " + entry.getValue()+ " uuids");
            }
        }

        if (!passengerSequenceCounts.isEmpty() || !vehicleSequenceCounts.isEmpty()) {
            sb.append("\n\n=== Arc Purpose Sequence Frequencies ===");
            for (var entry : passengerSequenceCounts.entrySet()) {
                sb.append("\n Passenger "+entry.getKey()+" : " + entry.getValue()+" times");
            }
            for (var entry : vehicleSequenceCounts.entrySet()) {
                sb.append("\n Vehicle "+entry.getKey()+" : " + entry.getValue()+" times");
            }
        }



        return sb.toString();
    }


    private List<TimeWindowPair> sortedTimeWindows(Set<TimeWindowPair> timeWindows) {
        return timeWindows.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    private record ODPair(Location origin, Location destination) {}

    private record TimeWindowPair(int timeStart, int timeEnd) implements Comparable<TimeWindowPair> {

        public static final Comparator<TimeWindowPair> NATURAL_ORDER =
                Comparator.comparing(TimeWindowPair::timeStart)
                        .thenComparing(TimeWindowPair::timeEnd);

        @Override
        public int compareTo(TimeWindowPair other) {
            return NATURAL_ORDER.compare(this, other);
        }
    }

    private void countSequences(Path<?> path, Map<PurposeSequence,Long> target) {
        LinkedList<ArcPurpose> q = new LinkedList<>();
        for (TimedArc ta : path.getPath()) {
            q.add(ta.getPurpose());
            if (q.size() == 3) {
                PurposeSequence pq = PurposeSequence.from(q);
                target.merge(pq, 1L, Long::sum);
                q.removeFirst();
                // Size is now two again
            }
            // Size is one or two
            if (q.size() == 2) {
                PurposeSequence pq = PurposeSequence.from(q);
                target.merge(pq, 1L, Long::sum);
            }
        }
    }

    private record PurposeSequence(ArcPurpose step1, ArcPurpose step2, ArcPurpose step3) {

        public static PurposeSequence from(List<ArcPurpose> lst) {
            if (lst.size() < 2 || lst.size() > 3) {
                throw new IllegalArgumentException("Only lists of size 2 or 3 are supported");
            }
            if (lst.size() == 2) {
                return new PurposeSequence(lst.get(0), lst.get(1), null);
            }
            return new PurposeSequence(lst.get(0), lst.get(1), lst.get(2));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (step1 != null) {
                sb.append(step1);
            }
            if (step2 != null) {
                sb.append(" -> ");
                sb.append(step2);

            }
            if (step3 != null) {
                sb.append(" -> ");
                sb.append(step3);
            }
            return sb.toString();
        }
    }

}