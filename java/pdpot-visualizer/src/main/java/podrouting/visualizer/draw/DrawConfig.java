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

public class DrawConfig {

    private int padding = 35;
    private int sectionSpacing = 30;

    private int timeMargin = 15;
    private int timeUnitWidth = 20;
    private int timeUnitGutter = 5;

    private int passengerHeight = 15;
    private int passengerSpacing = 5;

    private int vehicleHeight = 15;
    private int vehicleSpacing = 10;
    private int vehicleMargin = 90;
    private int vehicleLabelHeight = 0;

    private int arcSpacing = 5;

    private int networkHeight = 800;
    private int networkWidth = 800;

    private boolean skipRejected = true;
    private boolean sortPassengersByOd = true;
    private boolean debug = false;

    public int getPadding() {
        return padding;
    }

    public int getTimeMargin() {
        return timeMargin;
    }

    public int getPassengerHeight() {
        return passengerHeight;
    }

    public int getPassengerSpacing() {
        return passengerSpacing;
    }

    public int getVehicleLabelHeight() {
        return vehicleLabelHeight;
    }

    public int getVehicleHeight() {
        return vehicleHeight;
    }

    public int getVehicleSpacing() {
        return vehicleSpacing;
    }

    public int getArcSpacing() {
        return arcSpacing;
    }

    public int getSectionSpacing() {
        return sectionSpacing;
    }

    public int getTimeUnitWidth() {
        return timeUnitWidth;
    }

    public int getTimeUnitGutter() {
        return timeUnitGutter;
    }

    public int getVehicleMargin() {
        return vehicleMargin;
    }

    public int getNetworkHeight() {
        return networkHeight;
    }

    public int getNetworkWidth() {
        return networkWidth;
    }

    public boolean isSkipRejected() {
        return skipRejected;
    }

    public boolean isSortPassengersByOd() {
        return sortPassengersByOd;
    }

    public boolean isDebug() {
        return debug;
    }

    public int getDotSize() {
        return (int)Math.round(0.4 * Math.min(timeUnitWidth, passengerHeight));
    }
}
