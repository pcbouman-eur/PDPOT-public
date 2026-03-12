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

import picocli.CommandLine;
import podrouting.util.cli.DynamicSubcommand;

@CommandLine.Command(name = "visualizer-gui",
        description = "Run a Java Swing GUI for visualizing solutions")
public class MainVisualizerGUI implements Runnable, DynamicSubcommand {
    @Override
    public void run() {
        Visualizer vis = new Visualizer();
        vis.initApp();
        try {
            // This is an infinite wait on purpose - the Visualizer GUI itself should call System.exit()
            synchronized(this) {
                wait();
            }
        } catch (InterruptedException ignored) {
            // The program will now stop.
        }
    }

}
