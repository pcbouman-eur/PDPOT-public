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

import static podrouting.util.IOUtils.INSTANCE_POSTFIX;
import static podrouting.util.IOUtils.SOLUTION_POSTFIX;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import podrouting.data.Instance;
import podrouting.tools.organize.*;
import podrouting.util.IOUtils;

import java.io.File;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "organize", mixinStandardHelpOptions = true,
    description = "Organize instance files based on some properties")
public class OrganizeMain implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(OrganizeMain.class);

    @CommandLine.Parameters(description = "A list of property names")
    private List<String> propertyNames;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input directory containing .json instance files",
            required = true)
    private File inputDir;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory to write the organized tree to")
    private File outputDir;

    @CommandLine.Option(names = {"-t", "--tree"}, description = "Write an overview of the instance tree to a file")
    private File treeFile;

    @CommandLine.Option(names = {"-s", "--summary"}, description = "Write property-dependent summaries")
    private boolean writeSummaries;

    @CommandLine.Option(names = {"-r", "--rename"}, description = "Rename files using a consistent labelling procedure")
    private boolean rename;

    private static Optional<Instance> checkFile(File f) {
        String filename = f.getName().toLowerCase();
        if (!filename.endsWith(INSTANCE_POSTFIX)
                || filename.endsWith(SOLUTION_POSTFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(IOUtils.readInstance(f));
        } catch (IOException ex) {
            log.info("File {} was a candidate instance but could not be read correctly: {}", f, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Integer call() throws Exception {

        List<InstanceProperty> props = InstanceProperties.getPropertyList(propertyNames);
        InstanceTree tree = new InstanceTree(props);
        FileTracker filetracker = new FileTracker();

        long processedCount = 0, duplicateCount = 0;

        Set<File> visited = new HashSet<>();
        Deque<File> queue = new LinkedList<>();
        queue.add(inputDir);
        while (!queue.isEmpty()) {
            File dir = queue.pop();
            File canonical = dir.getCanonicalFile();
            if (!dir.isDirectory() || visited.contains(canonical)) {
                continue;
            }
            visited.add(canonical);
            for (File f : dir.listFiles()) {
                if (f.isFile() && checkFile(f).isPresent()) {
                    if (filetracker.addFile(f)) {
                        tree.addInstanceFile(f);
                        processedCount++;
                    }
                    else {
                        log.info("File {} is a duplicate of an earlier processed file", f);
                        duplicateCount++;
                    }
                } else if (f.isDirectory()) {
                    queue.add(f);
                }
            }
        }

        log.info("{} files processed, {} duplicate files ignored", processedCount, duplicateCount);

        if (outputDir != null) {
            // TODO: consider configuration of the labelmaker
            tree.writeTree(outputDir, rename ? new LabelMaker() : null, writeSummaries);
            log.info("Output written to {}", outputDir);
        }

        String overviewTree = tree.getOverview();
        log.info("Tree overview\n{}", overviewTree);
        if (treeFile != null) {
            try (PrintWriter pw = new PrintWriter(treeFile)) {
                pw.println(overviewTree);
            }
        }
        return 0;
    }

}