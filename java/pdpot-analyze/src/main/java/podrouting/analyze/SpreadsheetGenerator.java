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
package podrouting.analyze;


import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.DoubleBinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Instance;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.model.AssignmentModelTools;
import podrouting.util.IOUtils;

public class SpreadsheetGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetGenerator.class);

	private static final Pattern INSTANCE_ID_PATTERN = Pattern.compile("instance-id_(\\d+)");

	private static Optional<String> extractInstanceId(String filename) {
		Matcher m = INSTANCE_ID_PATTERN.matcher(filename);
		if (m.find()) {
			return Optional.of(m.group(1));
		}
        log.info("No instance id found for file {}", filename);
		return Optional.empty();
	}

    public static void scrapZipFile(String prefix, File zipFile, Map<String,Solution> solutions) throws IOException {
        List<IOUtils.ZipData<Solution>> solutionData = IOUtils.readSolutionsFromZip(zipFile);
        for (IOUtils.ZipData<Solution> zipData : solutionData) {
            Solution sol = zipData.object();
            sol.addMetaData("solutionFolder", zipFile.getName()+":"+zipData.parent());
            extractInstanceId(zipData.filename()).ifPresent(instanceId ->
                    sol.getInstance().addMetadata("instanceId", instanceId)
            );
            solutions.put(prefix+zipData.path(),sol);
            log.info("Succesfully added solution data from file {} in zip file {} to dataset.",
                    zipData.path(), zipFile);
        }
    }

	public static void scrapeSolutions(String prefix, File inputDirectory, Map<String,Solution> solutions) throws IOException {
		File [] files = inputDirectory.listFiles();
		if (files == null) {
			throw new IllegalArgumentException("An existing directory should be provided, and "+inputDirectory+" is not.");
		}
		for (File file : files) {
			if (file.getName().endsWith(".json")) {
				try {
					String fname = prefix + file.getName();
					Solution sol = Solution.readFromFile(file);
					String folder = prefix;
					if (folder.endsWith("\\") || folder.endsWith("/")) {
						folder = folder.substring(0, folder.length()-1);
					}
					sol.addMetaData("solutionFolder", folder);
					extractInstanceId(file.getName()).ifPresent(instanceId ->
						sol.getInstance().addMetadata("instanceId", instanceId)
					);
					solutions.put(fname,sol);
                    log.info("Succesfully added solution data from file {} to dataset.", file);
				}
				catch (JsonParseException|JsonMappingException ex) {
                    log.warn("Skipping .json file {}. A {} exception occured. Message: {}",
                            file, ex.getClass().getSimpleName(), ex.getMessage());
				}
			}
			else if (file.isDirectory()) {
				String newPrefix;
				if (prefix.isEmpty()) {
					newPrefix = file.getName() + File.separator;
				}
				else {
					newPrefix = prefix + file.getName() + File.separator;
				}
				scrapeSolutions(newPrefix, file, solutions);
			}
		}
	}

	public static void generateSpreadsheet(List<File> inputs, File outputFile) throws IOException {
		Map<String,Solution> solutions = new LinkedHashMap<>();
        for (File input : inputs) {
            if (input.isDirectory()) {
                log.info("Scanning directory {} for solution files", input);
                scrapeSolutions("", input, solutions);
            }
            else if (input.isFile() && input.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                log.info("Scanning zip file {} for solution files", input);
                String prefix = inputs.size() == 1 ? "" : input+":";
                scrapZipFile(prefix, input, solutions);
            }
        }
		writeSpreadsheet(outputFile, solutions);
        log.info("Succesfully wrote data to output file {}", outputFile);
	}
	
	public static void writeSpreadsheet(File output, Map<String,Solution> solutions) throws IOException {
		MetadataMap data = new MetadataMap();
		for (Solution sol : solutions.values()) {
			data.addHeaders(sol);
		}
		
		
		try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = new FileOutputStream(output)) {
			Sheet s = wb.createSheet("Full Data");
			
			int rowIndex = 0;
			// Create Header
			Row row = s.createRow(rowIndex++);
			data.writeHeader(row);
			
			for (Entry<String,Solution> e : solutions.entrySet()) {
				String f = e.getKey();
				Solution sol = e.getValue();
				row = s.createRow(rowIndex++);
				data.writeData(row, f, sol);
			}

			Sheet s2 = wb.createSheet("Comparison");
			Sheet s3 = wb.createSheet("BestDiff");
			Sheet s4 = wb.createSheet("BestDiffRelative");
			writeCompareSheets(solutions, s2, s3, s4, "bestSolver");

			wb.write(os);
		}
	}

	private static String toInstanceId(Solution sol) {
		Map<String, Object> metadata = sol.getInstance().getMetadata();

		return metadata.getOrDefault("instanceId",
				metadata.getOrDefault("uuid", "NO_ID"))
				.toString();
	}

	public static String attemptSplit(String instanceId, int token) {
		String [] split = instanceId.split("_");
		if (split.length > token) {
			return split[token];
		}
		return "";
	}

	private static List<String> getCompareCommonHeaders() {
		List<String> header = new ArrayList<>();
		header.add("InstanceId");
		header.add("network");
		header.add("network-size");
		header.add("nodes");
		header.add("segments");
		header.add("passengers");
		header.add("vehicles");
		header.add("timeHorizon");
		header.add("avgVehicleCapacity");
		header.add("passengersPerSeat");
		header.add("passengersPerVehicle");
		return header;
	}

	private static List<String> getCompareSolverHeaders() {
		List<String> header = new ArrayList<>();
		header.add("objective");
		header.add("solveTime");
		header.add("rejections");
		header.add("drivingCost");
		header.add("platooningCost");
		header.add("outsideTransfers");
		header.add("insideTransfers");
		header.add("serviceFraction");
		header.add("allPassengerTransportLB");
		header.add("servicedPassengerTransportLB");
		header.add("totalDrivingDistance");
		header.add("allPassengerTransportRatio");
		header.add("servicedPassengerTransportRatio");
		header.add("servicedPassengersPerSeat");
		header.add("servicedPassengersPerVehicle");
		header.add("insideTransferRatio");
		header.add("servicedInsideTransferRatio");
		return header;
	}

	private static List<Object> getCompareCommonData(String instanceId, Instance i) {
		List<Object> data = new ArrayList<>();
		data.add(instanceId);
		data.add(attemptSplit(i.getMetadata().getOrDefault("instanceType","").toString(), 1));
		data.add(attemptSplit(i.getMetadata().getOrDefault("instanceType","").toString(), 2));
		data.add(i.getLocations().size());
		data.add(i.getRoads().size());
		data.add(i.getPassengers().size());
		data.add(i.getVehicles().size());
		data.add(i.getMaximumTime() - i.getMinimumTime());
		data.add(i.getVehicles().stream().mapToInt(Vehicle::getCapacity).summaryStatistics().getAverage());
		data.add(i.getPassengersPerSeat());
		data.add(i.getPassengersPerVehicle());
		return data;
	}

	private static List<Object> getCompareSolverData(Solution sol) {
		List<Object> data = new ArrayList<>();
		SolutionStatistics solStats = new SolutionStatistics(sol);
		int insideTransfers = AssignmentModelTools.getAssignmentValue(sol).orElse(-1);
		data.add(sol.getCosts());
		data.add(sol.getMetadata().get("solve-time"));
		data.add(solStats.getNumRejectedPassengers());
		data.add(solStats.getDrivingCost());
		data.add(solStats.getPlatooningDiscount());
		data.add(solStats.getNumOutsideTransfers());
		data.add(insideTransfers >= 0 ? insideTransfers : Double.NaN);
		data.add(solStats.getServiceFraction());
		data.add(solStats.getAllPassengerTransportLB());
		data.add(solStats.getServicedPassengerTransportLB());
		data.add(solStats.getTotalDrivingDistance());
		data.add(solStats.getAllPassengerTransportRatio());
		data.add(solStats.getServicedPassengerTransportRatio());
		data.add(solStats.getServicedPassengersPerSeat());
		data.add(solStats.getServicedPassengersPerVehicle());
		data.add(insideTransfers/sol.getInstance().getPassengers().size());
		data.add(insideTransfers/solStats.getServicedPassengers());
		return data;
	}

	private static List<String> getCompareSolverHeaders(String solver) {
		return getCompareSolverHeaders()
				.stream()
				.map(header -> header + "_" + solver)
				.collect(Collectors.toList());
	}

	private static String solutionToSolver(Solution sol) {
		return sol.getMetadata().getOrDefault("solutionFolder", "Base").toString();
	}

	private static Map<String,Map<String,Solution>> getCompareSolutionTable(Map<String,Solution> solutions,
																		   String bestSolverName) {
		Map<String,List<Solution>> solutionsPerInstance =
				solutions.values()
						.stream()
						.collect(Collectors.groupingBy(SpreadsheetGenerator::toInstanceId));

		Map<String,Map<String,Solution>> table = new LinkedHashMap<>();
		for (Entry<String,List<Solution>> sols : solutionsPerInstance.entrySet()) {
			Map<String,Solution> map = new LinkedHashMap<>();
			Solution best = null;
			for (Solution sol : sols.getValue()) {
				String solver = solutionToSolver(sol);
				map.put(solver, sol);
				if (best == null || best.getCosts() > sol.getCosts()) {
					best = sol;
				}
			}
			map.put(bestSolverName, best);
			table.put(sols.getKey(), map);
		}
		return table;
	}

	private static List<String> getSolvers(Map<String,Solution> solutions, String bestSolverName) {
		Set<String> solverNames = new TreeSet<>();
		for (Solution sol : solutions.values()) {
			solverNames.add(solutionToSolver(sol));
		}
		solverNames.add(bestSolverName);
		return new ArrayList<>(solverNames);
	}

	private static Map<String,Map<String,List<Object>>> getCompareSolutionData(Map<String,Map<String,Solution>> table) {
		Map<String,Map<String,List<Object>>> result = new LinkedHashMap<>();
		for (Entry<String,Map<String,Solution>> e1 : table.entrySet()) {
			Map<String,List<Object>> transformMap = new LinkedHashMap<>();
			for (Entry<String,Solution> e2 : e1.getValue().entrySet()) {
				transformMap.put(e2.getKey(), getCompareSolverData(e2.getValue()));
			}
			result.put(e1.getKey(), transformMap);
		}
		return result;
	}

	private static List<String> getInstanceIds(Map<String,?> table) {
		List<Integer> numeric = new ArrayList<>();
		List<String> nonNumeric = new ArrayList<>();
		for (String key : table.keySet()) {
			try {
				int number = Integer.parseInt(key);
				numeric.add(number);
			}
			catch (NumberFormatException ignored) {
				nonNumeric.add(key);
			}
		}
		Collections.sort(numeric);
		return Stream.concat(
				numeric.stream().map(Object::toString),
				nonNumeric.stream()
		).collect(Collectors.toList());
	}

	private static void writeCompareSheets(Map<String,Solution> solutions, Sheet compSheet, Sheet diffSheet,
										   Sheet relDiffSheet, String bestSolverName) {

		// TODO: there is of course a risk here in that different instances can have the same ID
		Map<String,Instance> iMap = new HashMap<>();
		for (Solution sol : solutions.values()) {
			iMap.put(toInstanceId(sol), sol.getInstance());
		}

		Map<String,Map<String,Solution>> table = getCompareSolutionTable(solutions, bestSolverName);
		Map<String,Map<String,List<Object>>> data = getCompareSolutionData(table);
		List<String> solvers = getSolvers(solutions, bestSolverName);
		writeCompareSheet(compSheet, data, iMap, solvers);
		writeDiffSheet(diffSheet, data, iMap, solvers, bestSolverName, Double::sum);
		writeDiffSheet(relDiffSheet, data, iMap, solvers, bestSolverName, (a, b) -> a/b);
	}

	private static List<Object> attemptDiff(List<Object> lhs, List<Object> rhs, DoubleBinaryOperator op) {
		int n = Math.max(lhs.size(), rhs.size());
		List<Object> result = new ArrayList<>(n);
		for (int i=0; i < n; i++) {
			if (i >= lhs.size() || i >= rhs.size()) {
				result.add(null);
				continue;
			}
			Object left = lhs.get(i);
			Object right = rhs.get(i);
			if (left instanceof Number && right instanceof Number) {
				Number a = (Number) left;
				Number b = (Number) right;
				double diff = op.applyAsDouble(a.doubleValue(), b.doubleValue());
				result.add(diff);
			}
		}
		return result;
	}

	private static void writeCompareSheet(Sheet s, Map<String,Map<String,List<Object>>> table,
										  Map<String,Instance> iMap, List<String> solvers) {
		List<String> header = getCompareCommonHeaders();

		for (String solver : solvers) {
			header.addAll(getCompareSolverHeaders(solver));
		}

		int perSolverCols = getCompareSolverHeaders().size();
		int rowIndex = 0;
		List<String> instanceIds = getInstanceIds(table);
		writeCellList(s.createRow(rowIndex++), header);
		for (String instanceId : instanceIds) {
            Instance i = iMap.get(instanceId);
            List<Object> data = new ArrayList<>(getCompareCommonData(instanceId, i));
			Map<String,List<Object>> subMap = table.get(instanceId);
			for (String solver : solvers) {
				if (subMap.containsKey(solver)) {
					data.addAll(subMap.get(solver));
				}
				else {
					for (int t=0; t < perSolverCols; t++) {
						data.add(null);
					}
				}
			}
			writeCellList(s.createRow(rowIndex++), data);
		}
	}

	private static void writeDiffSheet(Sheet s, Map<String,Map<String,List<Object>>> table,
									   Map<String,Instance> iMap, List<String> solvers,
									   String bestSolver, DoubleBinaryOperator op) {
		List<String> header = getCompareCommonHeaders();

		for (String solver : solvers) {
			if (!solver.equals(bestSolver)) {
				header.addAll(getCompareSolverHeaders(solver));
			}
		}

		int perSolverCols = getCompareSolverHeaders().size();
		int rowIndex = 0;
		List<String> instanceIds = getInstanceIds(table);
		writeCellList(s.createRow(rowIndex++), header);
		for (String instanceId : instanceIds) {
            Instance i = iMap.get(instanceId);
            List<Object> data = new ArrayList<>(getCompareCommonData(instanceId, i));
			Map<String,List<Object>> subMap = table.get(instanceId);
			for (String solver : solvers) {
				if (!solver.equals(bestSolver)) {
					if (subMap.containsKey(solver)) {
						List<Object> cur = subMap.get(solver);
						List<Object> best = subMap.get(bestSolver);
						data.addAll(attemptDiff(cur, best, op));
					} else {
						for (int t = 0; t < perSolverCols; t++) {
							data.add(null);
						}
					}
				}
			}
			writeCellList(s.createRow(rowIndex++), data);
		}
	}

	private static void writeCellList(Row row, List<?> objects) {
		writeCells(row, objects.toArray());
	}

	private static void writeCells(Row row, Object... objects) {
		for (int i=0; i < objects.length; i++) {
			Cell c = row.createCell(i);
			Object o = objects[i];
			if (o instanceof Number) {
				Number n = (Number) o;
				c.setCellValue(n.doubleValue());
			}
			else if (o instanceof OptionalDouble) {
				((OptionalDouble)o).ifPresent(c::setCellValue);
			}
			else if (o instanceof OptionalInt) {
				((OptionalInt)o).ifPresent(c::setCellValue);
			}
			else if (o instanceof OptionalLong) {
				((OptionalLong)o).ifPresent(c::setCellValue);
			}
			else if (o != null) {
				c.setCellValue(o.toString());
			}
		}
	}
	
	public static class MetadataMap {
		private final Set<String> instanceKeys = new TreeSet<>();
		private final Set<String> solutionKeys = new TreeSet<>();
		
		public void addHeaders(Solution sol)
		{
			instanceKeys.addAll(sol.getInstance().getMetadata().keySet());
			solutionKeys.addAll(sol.getMetadata().keySet());
		}
		
		public void writeHeader(Row row)
		{
			List<String> header = new ArrayList<>();
			header.add("filename");
			header.addAll(instanceKeys);
			header.addAll(solutionKeys);
			
			header.addAll(Arrays.asList(
					"network", "networkSize", "nodes", "segments", "passengers", "vehicles", "avgVehicleCap",
					"passengersPerSeat", "passengersPerVehicle", "allowInsideTransfers", "arriveEarlyPenalty",
					"drivingPenalty", "platooningDiscount", "platooningDiscountFactor", "rejectionPenalty",
					"transferOutsidePenalty", "strictStopping", "strictStoppingAtDestination", "objective", "feasible",
					"drivingCostObj", "earlyPenaltyObj", "platooningDiscountObj", "rejectionPenaltyObj",
					"transferOutsidePenaltyObj", "numOutsideTransfers", "numInsideTransfers",
					"serviceFraction",
					"servicedPassengersPerSeat", "servicedPassengersPerVehicle",
					"allPassengerTransportLB", "servicedPassengerTransportLB", "totalDrivingDistance",
					"allPassengerTransportRatio" ,"servicedPassengerTransportRatio",
					"insideTransferRatio", "servicedInsideTransferRatio"
					));

			writeCells(row, header.toArray());
		}
		
		public void writeData(Row row, String filename, Solution sol) {
			SolutionStatistics solStats = new SolutionStatistics(sol);
			Instance i = sol.getInstance();
			List<Object> data = new ArrayList<>();
			
			data.add(filename);
			for (String key : instanceKeys) {
				data.add(i.getMetadata().getOrDefault(key, ""));
			}
			for (String key : solutionKeys) {
				data.add(sol.getMetadata().getOrDefault(key, ""));
			}

			int insideTransfers = AssignmentModelTools.getAssignmentValue(sol).orElse(-1);
			data.addAll(Arrays.asList(
					attemptSplit(filename, 1), attemptSplit(filename, 2),
					i.getLocations().size(), i.getRoads().size(), i.getPassengers().size(), i.getVehicles().size(), 
					i.getVehicles().stream().mapToInt(v -> v.capacity).average().orElse(0),
					i.getPassengersPerSeat(),
					i.getPassengersPerVehicle(),
					i.isAllowInsideTransfers(),
					i.getArriveEarlyPenalty(),
					i.getDrivingPenalty(),
					i.getPlatooningDiscount(),
					i.getPlatooningDiscountFactor(),
					i.getRejectionPenalty(),
					i.getTransferOutsidePenalty(),
					i.isStrictStopping(),
					i.isStrictStoppingAtDestination(),
					sol.getCosts(),
					sol.checkFeasibility(),
					solStats.getDrivingCost(),
					solStats.getEarlyPenalty(),
					solStats.getPlatooningDiscount(),
					solStats.getRejectionPenalty(),
					solStats.getTransferOutsidePenalty(),
					solStats.getNumOutsideTransfers(),
					insideTransfers >= 0 ? insideTransfers : Double.NaN,
					solStats.getServiceFraction(),
					solStats.getServicedPassengersPerSeat(),
					solStats.getServicedPassengersPerVehicle(),
					solStats.getAllPassengerTransportLB(),
					solStats.getServicedPassengerTransportLB(),
					solStats.getTotalDrivingDistance(),
					solStats.getAllPassengerTransportRatio(),
					solStats.getServicedPassengerTransportRatio(),
                    insideTransfers >= 0 ? insideTransfers / i.getPassengers().size() : Double.NaN,
                    insideTransfers >= 0 ? insideTransfers / solStats.getServicedPassengers() : Double.NaN
			));
			writeCells(row, data.toArray());
		}
		
	}
	
}
