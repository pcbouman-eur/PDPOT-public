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
package podrouting.util.cli;

import org.apache.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import podrouting.util.LogUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;

public abstract class MainRecurseBase<CTX> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MainRecurseBase.class);

    @Parameters(paramLabel = "FILE", description = "One or more input files to process", arity = "1..*")
    File[] files;

    @Option(names = {"-r", "-R", "--recursive"}, description = "Process recursively")
    boolean recursive;

    @Option(names = {"-o", "--output"}, paramLabel = "OUTPUT_DIR", description = "Output directory", required = true)
    File output;

    @Option(names = {"-f", "--force"}, description = "Overwrite existing files")
    boolean force;

    @Option(names= {"-l", "--logs"}, description="File to write the logs to")
    File logFile;

    @Option(names= {"-ll", "--level"}, description="Logging level")
    Level logLevel;

    public abstract CTX getContext();

    @Override
    public void run() {
        // Set up file logging if present
        if (logFile != null) {
            if (logLevel != null) {
                LogUtils.addFileAppender(logFile, org.apache.log4j.Level.toLevel(logLevel.toString()));
            }
            else {
                LogUtils.addFileAppender(logFile);
            }
        }
        if (logLevel != null) {
            LogManager.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(logLevel.toString()));
        }

        startRun();
        CTX ctx = getContext();
        int processed = 0;
        for (File f : files) {
            try {
                processed += process(f, output, true, ctx);
            }
            catch (IOException ex) {
                log.error("Error while processing files", ex);
            }
        }
        log.info("Finished. {} files were processed", processed);
        finishRun();
    }

    public final int process(File file, File outputDir, boolean root, CTX ctx) throws IOException {
        int count = 0;
        if (file.isDirectory()) {
            if (!root && !recursive) {
                return count;
            }
            String dirPath = file.getAbsolutePath();
            log.info("Processing files in directory {}", dirPath);
            for (File f : file.listFiles()) {
                File newOutput = outputDir;
                if (!root && !file.getName().isEmpty()) {
                    newOutput = new File(outputDir, file.getName());
                }
                count += process(f, newOutput, false, ctx);
            }
            log.info("Finished processing directory {}", dirPath);
        }
        else {
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                return count;
            }
            String outputName = determineOutputFileName(file);
            File outputFile = new File(outputDir, outputName);
            if (outputFile.exists() && !force) {
                log.warn("Skipping input file {} as output file {} already exists.", file, outputFile);
                return count;
            }
            processFile(file, createSupplier(outputFile), ctx);
            count++;
        }
        return count;
    }

    public String determineOutputFileName(File inputFile) {
        return inputFile.getName();
    }

    public void startRun() {
        // Left empty for the purpose that it can but doesn't have to be overridden in subclasses
    }
    public void finishRun() {
        // Left empty for the purpose that it can but doesn't have to be overridden in subclasses
    }

    public abstract void processFile(File file, Supplier<File> outputFile, CTX ctx) throws IOException;

    private Supplier<File> createSupplier(File outputFile) {
        return () -> {
            File outputDir = outputFile.getParentFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            return outputFile;
        };
    }

    public void setFiles(File... files) {
        this.files = files;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
