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
package podrouting.visualizer.draw;

public final class Bounds {

    public final int leftX, rightX, topY, bottomY;

    public Bounds(int leftX, int rightX, int topY, int bottomY) {
        this.leftX = leftX;
        this.rightX = rightX;
        this.topY = topY;
        this.bottomY = bottomY;
    }

    public int getWidth() {
        return Math.max(rightX, leftX) - Math.min(rightX, leftX);
    }

    public int getHeight() {
        return Math.max(bottomY, topY) - Math.min(bottomY, topY);
    }

    @Override
    public String toString() {
        return "Bounds{" +
                "leftX=" + leftX +
                ", rightX=" + rightX +
                ", topY=" + topY +
                ", bottomY=" + bottomY +
                '}';
    }

    public static Bounds emptyBounds() {
        return new Bounds(0,0,0,0);
    }

}
