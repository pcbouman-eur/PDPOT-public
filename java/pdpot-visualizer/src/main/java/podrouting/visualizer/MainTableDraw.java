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
package podrouting.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import podrouting.data.Solution;
import podrouting.data.assign.AssignedSolution;
import podrouting.model.AssignmentModelTools;
import podrouting.util.cli.DynamicSubcommand;
import podrouting.util.cli.MainRecurseBase;
import podrouting.visualizer.draw.DrawConfig;
import podrouting.visualizer.draw.DrawContext;
import podrouting.visualizer.draw.DrawElement;
import podrouting.visualizer.draw.ElementLayout;
import podrouting.visualizer.draw.elements.*;
import podrouting.visualizer.draw.layout.HorizontalLayout;
import podrouting.visualizer.draw.layout.VerticalLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Command(name = "visualize-table", mixinStandardHelpOptions = true,
    description = "Render a solution file to an image as a modular table view")
public class MainTableDraw extends MainRecurseBase<ElementLayout> implements DynamicSubcommand {

    private static final Logger log = LoggerFactory.getLogger(MainTableDraw.class);

    @Option(names = {"-ho", "--horizontal"}, description = "Draw elements horizontally")
    boolean horizontal;

    @Option(names = {"-e", "--elements"},
            description = "Which draw elements to use, options are 'pt' (PassengerTable), 'vt' (VehicleTable),\n"
                        + "'pvt' (PassengerVehicleTable), 'rt' (RoadTable) and 'net' (NetworkElement)")
    String [] elements = { "pvt", "rt", "net"};

    @Override
    public ElementLayout getContext() {
        List<DrawElement> elementList= new ArrayList<>();
        for (String el : elements) {
            switch (el) {
                case "pt":
                    elementList.add(new PassengerTable());
                    break;
                case "vt":
                    elementList.add(new VehicleTable());
                    break;
                case "pvt":
                    elementList.add(new PassengerVehicleTable());
                    break;
                case "rt":
                    elementList.add(new RoadTable());
                    break;
                case "net":
                    elementList.add(new NetworkElement());
                    break;
                default:
                    log.error("The draw element {} is unknown. Skipping this element. ", el);
                    break;
            }
        }

        return horizontal ? new HorizontalLayout(elementList) : new VerticalLayout(elementList);
    }

    @Override
    public String determineOutputFileName(File inputFile) {
        String name = inputFile.getName();
        return name.substring(0, name.length() - 5) + ".png";
    }

    @Override
    public void processFile(File input, Supplier<File> outputSup, ElementLayout layout) throws IOException {
        log.info("Reading solution from file {}", input);
        Solution solution = Solution.readFromFile(input);
        Optional<AssignedSolution> opt = AssignmentModelTools.getAssignedSolution(solution);
        File output = outputSup.get();
        if (opt.isPresent()) {
            AssignedSolution assignedSolution = opt.get();
            if (!assignedSolution.crossValidate()) {
                log.error("Cross validation of the assigned solution failed. Aborting visualization");
                return;
            }
            DrawContext ctx = new DrawContext(assignedSolution, new DrawConfig());
            try {
                layout.writePNGImage(ctx, output);
            }
            catch (RuntimeException ex) {
                log.error("Unexpected exception while generating drawing", ex);
                return;
            }
            log.info("Output file written to {}", output);
        }
        else {
            log.warn("No solution obtained from file {}, no solution will be written", input);
        }
    }
}
