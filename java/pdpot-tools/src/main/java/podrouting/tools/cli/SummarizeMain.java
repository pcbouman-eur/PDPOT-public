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
import podrouting.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "summarize", mixinStandardHelpOptions = true,
        description = "Compute summary statistics from instances and solutions")
public class SummarizeMain implements Callable<Integer> {

    public static Logger log = LoggerFactory.getLogger(SummarizeMain.class);

    @CommandLine.Option(names = {"-o", "--output"}, description="Output file to write report to")
    private File outputReport;

    @CommandLine.Parameters(description = "Directories to scan for instance and/or solution files", arity = "1..*")
    private List<File> directories;

    private final Set<File> jsonFiles = new LinkedHashSet<>();
    private final Set<String> uniqueInstanceUUIDs = new LinkedHashSet<>();
    private final Map<Integer, Integer> vehicleCountToUniqueInstances = new LinkedHashMap<>();
    private final Map<Integer, Integer> passengerCountToUniqueInstances = new LinkedHashMap<>();
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
    public Integer call() {
        directories.forEach(this::scanDirectory);
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
        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("Directory does not exist or is not a directory: {}", directory);
            return;
        }
        log.info("Scanning directory {}", directory);
        try {
            Files.walk(Paths.get(directory.toURI()))
                .filter(path -> path.toString().endsWith(".json"))
                .map(Path::toFile)
                .forEach(jsonFiles::add);
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
        for (File file : ProgressBar.wrap(jsonFiles, progressBarBuilder)) {
            try {
                Instance instance = IOUtils.readInstance(file);
                processInstance(instance, file.getAbsolutePath());
            } catch (IOException e) {
                try {
                    Solution solution = IOUtils.readSolution(file);
                    Instance instance = solution.getInstance();
                    if (instance != null) {
                        processInstance(instance, file.getAbsolutePath());
                    } else {
                        log.warn("Solution file {} has null instance", file.getAbsolutePath());
                    }
                } catch (IOException e2) {
                    log.warn("File {} is not a valid instance or solution JSON file", file.getAbsolutePath());
                }
            }
        }
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

}