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

import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.Serial;
import java.util.stream.Stream;

import javax.swing.JPanel;

import podrouting.data.Solution;
import podrouting.data.timed.Path;

public class SolutionPanel extends JPanel implements ComponentListener {

	@Serial
    private static final long serialVersionUID = -4135929452344736264L;

	private VisualizerCore core;
	private double maxTime;
	private double timeStep = 0.025;
	private int framesPerSecond = 60;

	public SolutionPanel() {
		super();
		this.core = new VisualizerCore();
		Thread t = new Thread(this::animate);
		t.setDaemon(true);
		t.start();
		addComponentListener(this);
	}

	public void setSolution(Solution sol) {
		core.setSolution(sol);
		core.setDimensions(getWidth(), getHeight());
		this.maxTime = Stream.concat(sol.getPassengerPaths().stream(), sol.getVehiclePaths().stream())
				.map(Path::getPath)
                .mapToInt(p -> p.isEmpty() ? 0 : p.get(p.size() - 1).getToTime())
                .max()
                .orElseGet(() -> 0);
		repaint();
	}

	private void animate() {
		while (true) {
			try {
				Thread.sleep((long) Math.ceil(1000d / framesPerSecond));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				return;
			}
			core.incrementCurrentTime(timeStep);
			if (core.getCurrentTime() > maxTime) {
				try {
					Thread.sleep(2000);
				}
				catch (InterruptedException ex) {
					ex.printStackTrace();
					return;
				}
				core.setCurrentTime(0);
			}
			repaint();
		}
	}

	@Override
	public void paintComponent(Graphics gr) {
		super.paintComponent(gr);
		core.drawFrame(gr);
	}


	@Override
	public void componentResized(ComponentEvent e) {
		if (core.getSolution() != null) {
			core.setDimensions(getWidth(), getHeight());
			repaint();
		}
	}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}

}
