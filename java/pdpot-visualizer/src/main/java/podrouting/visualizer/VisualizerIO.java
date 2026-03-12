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

import podrouting.data.Instance;
import podrouting.data.Solution;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public final class VisualizerIO {


    public static int DEFAULT_WIDTH = 800;
    public static int DEFAULT_HEIGHT = 800;


    private VisualizerIO() {}

    public static void openNetworkImage(Instance i) throws IOException
    {
        Desktop d = Desktop.getDesktop();
        File f = File.createTempFile("network-image", ".png");
        writeNetworkPNGImage(i, f);
        d.open(f);
    }

    public static void writeNetworkPNGImage(Instance i, File output) throws IOException
    {
        writeNetworkPNGImage(i, DEFAULT_WIDTH, DEFAULT_HEIGHT, output);
    }

    public static void writeNetworkPNGImage(Instance i, int width, int height, File output) throws IOException
    {
        writeNetworkImage(i, width, height, "png", output);
    }

    public static void writeNetworkImage(Instance i, int width, int height, String format, File output) throws IOException
    {
        VisualizerCore vc = new VisualizerCore();
        vc.setDimensions(width, height);
        vc.setInstance(i);
        vc.writeNetworkImage(format, output);
    }

    public static void openSolutionImage(Solution s) throws IOException
    {
        Desktop d = Desktop.getDesktop();
        File f = File.createTempFile("solution-image", ".png");
        writeSolutionPNGImage(s, f);
        d.open(f);
    }

    public static void writeSolutionPNGImage(Solution s, File output) throws IOException
    {
        writeSolutionPNGImage(s, DEFAULT_WIDTH, DEFAULT_HEIGHT, output);
    }

    public static void writeSolutionPNGImage(Solution s, int width, int height, File output) throws IOException
    {
        writeSolutionImage(s, width, height, "png", output);
    }

    public static void writeSolutionImage(Solution s, int width, int height, String format, File output) throws IOException
    {
        VisualizerCore vc = new VisualizerCore();
        vc.setDimensions(width, height);
        vc.setSolution(s);
        vc.writeToFile(format, output);
    }


}
