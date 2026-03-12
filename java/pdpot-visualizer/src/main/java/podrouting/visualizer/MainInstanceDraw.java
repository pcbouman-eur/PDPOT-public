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
import picocli.CommandLine;
import podrouting.data.Instance;
import podrouting.util.cli.DynamicSubcommand;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "visualize-instance", mixinStandardHelpOptions = true,
    description = "Draw an instance file and write it as an image")
public class MainInstanceDraw implements Callable<Integer>, DynamicSubcommand {

    private static final Logger log = LoggerFactory.getLogger(MainInstanceDraw.class);

    @CommandLine.Option(names={"-i", "--input"}, description="Input .json file containing an instance to draw",
            required = true)
    private File input;

    @CommandLine.Option(names={"-o", "--output"}, description="")
    private File output;

    @CommandLine.Option(names = {"-iw", "--width"}, paramLabel = "WIDTH", defaultValue = "1920",
            description = "Vertical resolution of the image (default: ${DEFAULT-VALUE})")
    private int width;

    @CommandLine.Option(names = {"-ih", "--height"}, paramLabel = "HEIGHT", defaultValue = "1080",
            description = "Horizontal resolution of the image (default: ${DEFAULT-VALUE})")
    private int height;

    @CommandLine.Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "PNG",
            description = "Format used to draw the image (default: ${DEFAULT-VALUE})")
    private String format;

    @Override
    public Integer call() throws Exception {
        log.info("Reading input file {}", input);
        Instance i = Instance.readFromFile(input);
        VisualizerCore core = new VisualizerCore();
        core.setInstance(i);
        core.setDrawNetwork(true);
        core.setDimensions(width, height);
        core.setDrawTimeStamp(false);
        log.info("Writing image file {} with format {}", output, format);
        core.writeToFile(format, output);
        log.info("Image file written.");
        return 0;
    }
}
