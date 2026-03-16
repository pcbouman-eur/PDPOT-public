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

import com.squareup.gifencoder.GifEncoder;
import com.squareup.gifencoder.Image;
import com.squareup.gifencoder.ImageOptions;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import podrouting.data.Solution;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.Path;
import podrouting.model.AssignmentModelTools;
import podrouting.util.cli.DynamicSubcommand;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(name = "visualize-gif", mixinStandardHelpOptions = true,
                    description = "Create an animated gif file with a visualization of a solution file")
public class MainGifRender implements Callable<Integer>, DynamicSubcommand {

    private static final Logger log = LoggerFactory.getLogger(MainGifRender.class);

    @CommandLine.Option(names={"-i", "--input"}, description="Input .json file containing a solution to animate",
            required = true)
    private File input;

    @CommandLine.Option(names={"-o", "--output"}, description="Output file name for animated gif", required = true)
    private File output;


    @CommandLine.Option(names = {"-s", "--skip"}, description = "Whether to skip the first time step")
    boolean skipFirst;

    @CommandLine.Option(names = {"-vw", "--width"}, paramLabel = "WIDTH",
            description = "Vertical resolution of gif file. Default: ${DEFAULT-VALUE}")
    int width = 500;

    @CommandLine.Option(names = {"-vh", "--height"}, paramLabel = "HEIGHT",
            description = "Horizontal resolution of the gif file. Default: ${DEFAULT-VALUE}")
    int height = 500;


    @CommandLine.Option(names = {"-d", "--delay"}, paramLabel = "DELAY",
            description = "Delay between video frames in ms. Default: ${DEFAULT-VALUE}")
    int frameDelay = 60;

    @CommandLine.Option(names = {"-t", "--timestep"}, paramLabel = "STEP",
            description = "The time step that passes in each frame. Default: ${DEFAULT-VALUE}")
    double timestep = 0.1;

    public static void main(String [] args) throws IOException {
        File input = new File("test.solution.json");
        File output = new File("test.gif");
        MainGifRender mgr = new MainGifRender();
        mgr.input = input;
        mgr.output = output;
        mgr.call();
    }

    public Integer call() throws IOException {
        log.info("Reading solution from file {}", input);
        Solution solution = Solution.readFromFile(input);
        MainGifRender.writeGif(solution,output, !skipFirst, width, height, frameDelay, timestep);
        log.info("Animated .gif file written to {}", output);
        return 0;
    }

    public static void writeGif(Solution sol, File output, boolean firstTimestep, int w, int h, int delay, double timeStep) throws IOException {
        // TODO: add maxtime function to Solution??
        int maxTime = Stream.concat(sol.getPassengerPaths().stream(), sol.getVehiclePaths().stream())
                .map(Path::getPath)
                .mapToInt(p -> p.isEmpty() ? 0 : p.get(p.size() - 1).getToTime())
                .max()
                .orElseGet(() -> 0);

        sol = Solution.removeSink(sol);

        VisualizerCoreColorful core = new VisualizerCoreColorful();
        core.setSolution(sol);
        core.setDimensions(w, h);
        AssignedSolution aSol = AssignmentModelTools.getAssignedSolution(sol).get();
        core.setAssignedSolution(aSol);

        long frames = (long)Math.ceil((1 + maxTime - core.getCurrentTime())/timeStep);
        ProgressBarBuilder progressBarBuilder = new ProgressBarBuilder()
                .setTaskName("Rendering animated gif")
                .setStyle(ProgressBarStyle.ASCII)
                .setInitialMax(frames);

        log.info("Writing animated gif file {}", output);
        try (FileOutputStream out = new FileOutputStream(output); ProgressBar bar = progressBarBuilder.build()) {
            GifEncoder enc = new GifEncoder(out, w, h, 0);
            ImageOptions options = new ImageOptions();
            BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            Graphics g = bi.getGraphics();
            if(firstTimestep) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                core.drawStartFrame(g);
                int[] flat = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
                Image image = com.squareup.gifencoder.Image.fromRgb(flat, w);
                enc.addImage(image, options);
            }
            do {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                core.drawFrame(g);
                int[] flat = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
                Image image = com.squareup.gifencoder.Image.fromRgb(flat, w);
                enc.addImage(image, options);
                core.incrementCurrentTime(timeStep);
                bar.step();
            } while (core.getCurrentTime() <= maxTime + 1);
            g.dispose();
            enc.finishEncoding();
        }
    }

}
