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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
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

    public static void scrapeZipFile(String prefix, File zipFile, Map<String,Solution> solutions) throws IOException {
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
        long scrapeTime = System.currentTimeMillis();
        for (File input : inputs) {
            if (input.isDirectory()) {
                log.info("Scanning directory {} for solution files", input);
                scrapeSolutions("", input, solutions);
            }
            else if (input.isFile() && input.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                log.info("Scanning zip file {} for solution files", input);
                String prefix = inputs.size() == 1 ? "" : input+":";
                scrapeZipFile(prefix, input, solutions);
            }
        }
        scrapeTime = System.currentTimeMillis() - scrapeTime;
        log.info("Scanning process completed. {} solutions were found. This took {}ms", solutions.size(), scrapeTime);
		long writeTime = System.currentTimeMillis();
        writeSpreadsheet(outputFile, solutions);
        writeTime = System.currentTimeMillis() - writeTime;
        log.info("Succesfully wrote data to output file {}. This took {} ms", outputFile, writeTime);
	}
	
	public static void writeSpreadsheet(File output, Map<String,Solution> solutions) throws IOException {
		MetadataMap data = new MetadataMap();
		for (Solution sol : solutions.values()) {
			data.addHeaders(sol);
		}
		
		log.info("Creating Spreadsheet file {}", output.getAbsolutePath());
		try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = new FileOutputStream(output)) {
			Sheet s = wb.createSheet("Full Data");
			
			int rowIndex = 0;
			// Create Header
			Row row = s.createRow(rowIndex++);
			data.writeHeader(row);

            ProgressBarBuilder progressBarBuilder = new ProgressBarBuilder()
                    .setTaskName("Writing solution rows")
					.setConsumer(new DelegatingProgressBarConsumer(log::info))
                    .setStyle(ProgressBarStyle.ASCII);
			for (Entry<String,Solution> e : ProgressBar.wrap(solutions.entrySet(), progressBarBuilder)) {
				String f = e.getKey();
				Solution sol = e.getValue();
				row = s.createRow(rowIndex++);
				data.writeData(row, f, sol);
			}

			wb.write(os);
		}
	}

	public static String attemptSplit(String instanceId, int token) {
		String [] split = instanceId.split("_");
		if (split.length > token) {
			return split[token];
		}
		return "";
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

			double insideTransfers = AssignmentModelTools.getAssignmentValue(sol).orElse(-1);
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
                    insideTransfers >= 0 ? insideTransfers*1d / i.getPassengers().size() : Double.NaN,
                    insideTransfers >= 0 ? insideTransfers*1d / solStats.getServicedPassengers() : Double.NaN
			));
			writeCells(row, data.toArray());
		}
		
	}
	
}
