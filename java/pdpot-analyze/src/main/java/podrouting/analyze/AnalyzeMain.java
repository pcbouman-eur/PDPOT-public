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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import podrouting.model.AssignmentModelORTools;
import podrouting.model.ORToolsSolverChoice;
import podrouting.util.cli.DynamicSubcommand;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "spreadsheet", mixinStandardHelpOptions = true,
        description = "Create an analysis spreadsheet from solution files.")
public class AnalyzeMain implements Callable<Integer>, DynamicSubcommand {

    @CommandLine.Option(names = {"-i", "--input"},
            description = "One or more directories or zip files containing .json solution files",
            required = true)
    private List<File> inputDir;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output .xlsx to write the analysis to")
    private File outputFile;

    @CommandLine.Option(names = {"-s", "--solver"}, description = "Solver to be used by OR-tools. " +
            "Default: ${DEFAULT-VALUE}. Possible options: ${COMPLETION-CANDIDATES}. " +
            "Not all options may be available on your system.", defaultValue="SCIP")
    private ORToolsSolverChoice solver;

    @Override
    public Integer call() throws Exception {
        AssignmentModelORTools.setSolver(solver);
        SpreadsheetGenerator.generateSpreadsheet(inputDir, outputFile);
        return 0;
    }
}
