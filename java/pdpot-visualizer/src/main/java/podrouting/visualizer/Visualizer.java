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

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.io.Serial;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import podrouting.data.Solution;

public class Visualizer extends JFrame {

	@Serial
    private static final long serialVersionUID = -4079069289498873770L;

	private SolutionPanel panel;

	Visualizer() {
		super();
		setSize(800,600);
		setTitle(null);
		init();
		setVisible(true);
	}

	private void init() {
		this.setLayout(new BorderLayout());

		panel = new SolutionPanel();
		this.add(panel,BorderLayout.CENTER);
	}

	void initApp() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JButton button = new JButton("Load Solution");
		button.addActionListener(al -> {
			JFileChooser chooser = new JFileChooser();
			FileFilter filter = new FileNameExtensionFilter("JSON solution files","json");
			chooser.setFileFilter(filter);
			int result = chooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				try {
					File f = chooser.getSelectedFile();
					ObjectMapper mapper = new ObjectMapper();
					Solution solution = mapper.readValue(f, Solution.class);
					setSolution(solution);
					setTitle(f.getName());
				}
				catch(IOException ex) {
					JOptionPane.showMessageDialog(this, "An exception occurred while reading the file: "+ex.getMessage(), "Exception", JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		JPanel bottom = new JPanel();
		bottom.add(button);
		add(bottom,BorderLayout.SOUTH);
        this.revalidate();
        this.repaint();
	}

	public void setSolution(Solution sol) {
		panel.setSolution(sol);
	}

	public void setTitle(String title) {
		if (title == null || title.trim().isEmpty()) {
			super.setTitle("Visualizer");
		}
		else {
			super.setTitle("Visualizer - "+title);
		}
	}

	public static void show(Solution sol, String title) {
		Visualizer vis = new Visualizer();
		vis.setSolution(sol);
		vis.setTitle(title);
		vis.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}

	public static void show(Solution sol) {
		show(sol,"");
	}

}
