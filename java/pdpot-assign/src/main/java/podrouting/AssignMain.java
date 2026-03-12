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
package podrouting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import podrouting.data.Solution;
import podrouting.data.assign.AssignedSolution;
import podrouting.model.AssignmentModelTools;
import podrouting.util.cli.DynamicSubcommand;
import podrouting.util.cli.MainContextFreeRecurseBase;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

@Command(name = "assign", mixinStandardHelpOptions = true,
            description = "Run the assignment model on a regular solution file")
public class AssignMain extends MainContextFreeRecurseBase implements DynamicSubcommand {

    private static final Logger log = LoggerFactory.getLogger(AssignMain.class);

    public void processFile(File input, Supplier<File> outputSup) throws IOException {
        log.info("Reading solution from file {}", input);
        Solution solution = Solution.readFromFile(input);
        Optional<AssignedSolution> opt = AssignmentModelTools.getAssignedSolution(solution);
        if (opt.isPresent()) {
            File output = outputSup.get();
            AssignedSolution assignedSolution = opt.get();
            ObjectMapper om = new ObjectMapper();
            try {
                om.writeValue(output, assignedSolution);
                log.info("Successfully written assigned solution to output file {}", output);
            }
            catch (IOException ex) {
                log.error("Error writing assigned solution to output file {}", output);
                throw ex;
            }
        }
        else {
            log.warn("No solution obtained from file {}, no solution will be written", input);
        }
    }

    public static void main(String [] args) {
        int exitCode = new CommandLine(new AssignMain()).execute(args);
        System.exit(exitCode);
    }

}
