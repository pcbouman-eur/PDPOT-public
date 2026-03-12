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

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 Color Scheme designed by Paul Tol
 Website: https://personal.sron.nl/~pault/
 Python code:  https://personal.sron.nl/~pault/data/tol_colors.py
 */

public class TolColorScheme {

    private static final Color [] COLORS = new Color [] {Color.decode("#E8ECFB"),
            Color.decode("#D9CCE3"),
            Color.decode("#D1BBD7"),
            Color.decode("#CAACCB"),
            Color.decode("#BA8DB4"),
            Color.decode("#AE76A3"),
            Color.decode("#AA6F9E"),
            Color.decode("#994F88"),
            Color.decode("#882E72"),
            Color.decode("#1965B0"),
            Color.decode("#437DBF"),
            Color.decode("#5289C7"),
            Color.decode("#6195CF"),
            Color.decode("#7BAFDE"),
            Color.decode("#4EB265"),
            Color.decode("#90C987"),
            Color.decode("#CAE0AB"),
            Color.decode("#F7F056"),
            Color.decode("#F7CB45"),
            Color.decode("#F6C141"),
            Color.decode("#F4A736"),
            Color.decode("#F1932D"),
            Color.decode("#EE8026"),
            Color.decode("#E8601C"),
            Color.decode("#E65518"),
            Color.decode("#DC050C"),
            Color.decode("#A5170E"),
            Color.decode("#72190E"),
            Color.decode("#42150A")};

    private static final int [][] INDICES = {{9}, {9, 25}, {9, 17, 25}, {9, 14, 17, 25}, {9, 13, 14, 17,
            25}, {9, 13, 14, 16, 17, 25}, {8, 9, 13, 14, 16, 17, 25}, {8,
            9, 13, 14, 16, 17, 22, 25}, {8, 9, 13, 14, 16, 17, 22, 25, 27},
            {8, 9, 13, 14, 16, 17, 20, 23, 25, 27}, {8, 9, 11, 13, 14, 16,
            17, 20, 23, 25, 27}, {2, 5, 8, 9, 11, 13, 14, 16, 17, 20, 23,
            25}, {2, 5, 8, 9, 11, 13, 14, 15, 16, 17, 20, 23, 25}, {2, 5,
            8, 9, 11, 13, 14, 15, 16, 17, 19, 21, 23, 25}, {2, 5, 8, 9, 11,
            13, 14, 15, 16, 17, 19, 21, 23, 25, 27}, {2, 4, 6, 8, 9, 11,
            13, 14, 15, 16, 17, 19, 21, 23, 25, 27}, {2, 4, 6, 7, 8, 9, 11,
            13, 14, 15, 16, 17, 19, 21, 23, 25, 27}, {2, 4, 6, 7, 8, 9, 11,
            13, 14, 15, 16, 17, 19, 21, 23, 25, 26, 27}, {1, 3, 4, 6, 7, 8,
            9, 11, 13, 14, 15, 16, 17, 19, 21, 23, 25, 26, 27}, {1, 3, 4,
            6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 19, 21, 23, 25, 26,
            27}, {1, 3, 4, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 18, 20,
            22, 24, 25, 26, 27}, {1, 3, 4, 6, 7, 8, 9, 10, 12, 13, 14, 15,
            16, 17, 18, 20, 22, 24, 25, 26, 27, 28}, {0, 1, 3, 4, 6, 7, 8,
            9, 10, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 25, 26, 27, 28}};

    public static <E> Map<E,Color> getColorMap(List<E> list) {
        Map<E,Color> result = new HashMap<>();
        int n = list.size() - 1;
        if (n >= 0 && n < INDICES.length) {
            int [] idxs = INDICES[n];
            int i = 0;
            for (E element : list) {
                result.put(element, COLORS[idxs[i++]]);
            }
        }
        else if (n >= INDICES.length) {
            int m = INDICES.length-1;
            int [] idxs = INDICES[m];
            int i = 0;
            for (E element : list) {
                result.put(element, COLORS[idxs[i%m]]);
                i++;
            }

        }
        return result;
    }

}
