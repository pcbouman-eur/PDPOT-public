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

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import podrouting.data.Solution;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.Path;
import podrouting.model.AssignmentModelTools;
import podrouting.util.cli.MainContextFreeRecurseBase;
import podrouting.util.cli.DynamicSubcommand;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Command(name = "visualize-video", mixinStandardHelpOptions = true,
            description = "Animate a solution file and render this to a video file")
public class MainVideoRender extends MainContextFreeRecurseBase implements DynamicSubcommand {

    private static final Logger log = LoggerFactory.getLogger(MainVideoRender.class);

    @Option(names = {"-s", "--skip"}, description = "Whether to skip the first time step")
    boolean skipFirst;

    @Option(names = {"-vw", "--width"}, paramLabel = "WIDTH", description = "Vertical resolution of the video")
    int width = 1920;

    @Option(names = {"-vh", "--height"}, paramLabel = "HEIGHT", description = "Horizontal resolution of the video")
    int height = 1080;


    @Option(names = {"-fr", "--framerate"}, paramLabel = "RATE", description = "Framerate of the video")
    int framerate = 60;

    @Option(names = {"-t", "--timestep"}, paramLabel = "STEP", description = "The time step that passes in each frame")
    double timestep = 0.01;

    @Override
    public String determineOutputFileName(File inputFile) {
        String name = inputFile.getName();
        return name.substring(0, name.length() - 5) + ".mp4";
    }

    public void processFile(File input, Supplier<File> outputSup) throws IOException {
        File output = outputSup.get();
        log.info("Reading solution from file {}", input);
        Solution solution = Solution.readFromFile(input);
        MainVideoRender.writeMP4(solution,output, true, !skipFirst, width, height, framerate, timestep);
        log.info("Video written to {}", output);
    }

    public static void writeMP4(Solution sol, File output, boolean colorful, boolean firstTimestep, int w, int h, int frameRate, double timeStep) throws FrameRecorder.Exception {
        // TODO: add maxtime function to Solution??
        int maxTime = Stream.concat(sol.getPassengerPaths().stream(), sol.getVehiclePaths().stream())
                .map(Path::getPath)
                .mapToInt(p -> p.isEmpty() ? 0 : p.get(p.size() - 1).getToTime())
                .max()
                .orElseGet(() -> 0);

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(output, w, h);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            recorder.setFrameRate(frameRate);
            recorder.setFormat("mp4");
            recorder.setVideoCodecName("h264");
            recorder.setVideoQuality(0.1);
            recorder.start();
            sol = Solution.removeSink(sol);

            if(colorful) {
                VisualizerCoreColorful core = new VisualizerCoreColorful();
                core.setSolution(sol);
                core.setDimensions(w, h);
                AssignedSolution aSol = AssignmentModelTools.getAssignedSolution(sol).get();
                core.setAssignedSolution(aSol);

                BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                //Java2DFrameConverter converter = new Java2DFrameConverter();
                Frame frame = converter.convert(bi);
                Graphics g = bi.getGraphics();
                if(firstTimestep) {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    core.drawStartFrame(g);
                    Java2DFrameConverter.copy(bi, frame);
                    recorder.record(frame, avutil.AV_PIX_FMT_ARGB);
                }
                do {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    core.drawFrame(g);
                    Java2DFrameConverter.copy(bi, frame);
                    recorder.record(frame, avutil.AV_PIX_FMT_ARGB);
                    core.incrementCurrentTime(timeStep);
                } while (core.getCurrentTime() <= maxTime + frameRate*timeStep);
                recorder.stop();
            }
            else {
                VisualizerCore core = new VisualizerCore();
                core.setSolution(sol);
                core.setDimensions(w, h);
                BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                //Java2DFrameConverter converter = new Java2DFrameConverter();
                Frame frame = converter.convert(bi);
                Graphics g = bi.getGraphics();
                do {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    core.drawFrame(g);
                    Java2DFrameConverter.copy(bi, frame);
                    recorder.record(frame, avutil.AV_PIX_FMT_ARGB);
                    core.incrementCurrentTime(timeStep);
                } while (core.getCurrentTime() <= maxTime + frameRate*timeStep);
                recorder.stop();
            }
        }
    }

}
