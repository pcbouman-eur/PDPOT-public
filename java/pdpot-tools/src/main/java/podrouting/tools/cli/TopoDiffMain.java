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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import podrouting.tools.organize.NetworkTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import podrouting.data.Instance;
import podrouting.util.IOUtils;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "topodiff", mixinStandardHelpOptions = true,
        description = "Compares the topology of the network of two instances")
public class TopoDiffMain implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(TopoDiffMain.class);

    @CommandLine.Parameters(index="0", description="The first file to compare")
    private File instance1File;

    @CommandLine.Parameters(index="1", description="The second file to compare")
    private File instance2File;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output .json file with topology differences")
    private File outputJson;

    @CommandLine.Option(names = {"-s", "--summary"}, description = "Only outputs the summary")
    private boolean summaryOnly;


    @Override
    public Integer call() throws Exception {
        log.info("Reading instance files {} and {}", instance1File, instance2File);
        Instance instance1 = IOUtils.readInstance(instance1File);
        Instance instance2 = IOUtils.readInstance(instance2File);

        log.info("Computing topological difference");
        NetworkTopology.TopologyDifference diff = NetworkTopology.computeDiff(instance1, instance2);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (!summaryOnly) {
            log.info("Detailed differences:\n{}", mapper.writeValueAsString(diff));
        }
        if (outputJson != null) {
            mapper.writeValue(outputJson, diff);
        }

        log.info("Difference summary:\n{}", diff.getSummary());

        return 0;
    }
}
