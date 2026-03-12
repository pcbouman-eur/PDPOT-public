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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Passenger;
import podrouting.data.Road;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.util.SolutionHelper;

public class VisualizerCore {

	private int width, height;

	private Instance instance;
	private Solution solution;
	private double currentTime;
	
	private SolutionHelper helper;

	private final double defaultTranslate = 10;

	private double translateX = 0;
	private double translateY = 0;
	private double scale;

	private final float roadWidth = 1f;
	private final Color roadColor = Color.LIGHT_GRAY;
	private final float highwayWidth = 5f;
	private final Color highwayColor = Color.DARK_GRAY;
	private final int locationRadius = 3;
	private final int intersectionRadius = 1;
	private final Color locationColor = Color.BLUE;
	private final int vehicleSize = 8;
	private final Color vehicleColor = Color.BLACK;
	private final int passengerSize = 5;
	private final Color passengerInactiveColor = new Color(94, 0, 2);
	private final Color passengerColor = Color.RED;
	private final Color passengerDestColor = Color.GREEN;
	private final Color passengerDestInactiveColor = new Color(0, 76, 0);
	private final Color passengerDestLink = new Color(175,175,175,150);
	private final int shiftAmount = 5;

	private final Color timestampColor = Color.BLACK;
	private final DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
	
	private final boolean drawInactive = true;
	private final boolean drawFinished = true;
	
	private boolean drawNetwork = true;
	private boolean drawVehicles = true;
	private boolean drawPassengers = true;
	private boolean drawDestinationLines = true;
	private boolean drawTimeStamp = true;
	
	public void setInstance(Instance i) {
		this.instance = i;
		computeScale();
	}
	
	public void setSolution(Solution sol) {
		this.instance = sol.getInstance();
		this.solution = sol;
		this.currentTime = 0;
		this.helper = new SolutionHelper(sol, drawInactive, drawFinished, false);
		computeScale();
	}

	public void setDimensions(int w, int h) {
		this.width = w;
		this.height = h;
		if (instance != null) {
			computeScale();
		}
	}

	public Solution getSolution() {
		return solution;
	}

	public double getCurrentTime() {
		return currentTime;
	}

	public void setCurrentTime(double time) {
		this.currentTime = time;
	}

	public void incrementCurrentTime(double amount) {
		this.currentTime += amount;
	}

	private int shiftFactor(Passenger p, TimedArc ta) {
		List<Passenger> lst;
		if (ta.getPurpose() == ArcPurpose.WAIT_OUT) {
			if (ta.getFromLocation().equals(p.getDestination())) {
				lst = helper.getPassengersArrived(ta);
			}
			else {
				lst = helper.getPassengersWaiting(ta);
			}
		}
		else {
			lst = helper.getPassengers(ta);
			if (lst == null || !lst.contains(p)) {
				throw new IllegalArgumentException("Passenger "+p.getId()+" does not use the given arc");
			}
		}
		return lst.indexOf(p);
	}

	private int shiftFactor(Vehicle v, TimedArc ta) {
		List<Vehicle> lst = helper.getVehicles(ta);
		if (lst == null || !lst.contains(v)) {
			throw new IllegalArgumentException("Vehicle "+v.getId()+" does not use the given arc");
		}
		return lst.indexOf(v);
	}

	private void computeScale() {
		double minX, maxX, minY, maxY;
		minX = Double.POSITIVE_INFINITY;
		maxX = Double.NEGATIVE_INFINITY;
		minY = Double.POSITIVE_INFINITY;
		maxY = Double.NEGATIVE_INFINITY;
		for (Location loc : instance.getLocations()) {
			minX = Math.min(minX, loc.getX());
			maxX = Math.max(maxX, loc.getX());
			minY = Math.min(minY, loc.getY());
			maxY = Math.max(maxY, loc.getY());
		}
		
		double baseTranslate = defaultTranslate;
		if (helper != null) {
			baseTranslate = defaultTranslate + helper.getMaxListLength() * shiftAmount;
		}
		
		double scaleX = (width - 2d * baseTranslate) / (maxX - minX);
		double scaleY = (height - 2d * baseTranslate) / (maxY - minY);
		scale = Math.min(scaleX, scaleY);
		
		double xSlack = width - 2d * baseTranslate - scale * (maxX-minX);
		double ySlack = height - 2d * baseTranslate - scale * (maxY-minY);
		this.translateX = xSlack/2 + baseTranslate - scale * minX;
		this.translateY = ySlack/2 + baseTranslate - scale * minY;
	}

	private int scaleXInt(double x) {
		return (int) Math.round(translateX + (scale * x));
	}

	private int scaleYInt(double y) {
		return (int) Math.round(translateY + (scale * y));
	}

	public void writeNetworkImage(String formatName, File output) throws IOException {
		BufferedImage bi = new BufferedImage(20+width,20+height,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setBackground(Color.WHITE);
		g.clearRect(0, 0, width+20, height+20);
		g.setTransform(AffineTransform.getTranslateInstance(10, 10));
		drawNetwork(g);
		ImageIO.write(bi, formatName, output);
	}
	
	public void drawNetwork(Graphics2D g) {
		// Draw roads
		for (Road r : instance.getRoads()) {
			Location o = r.getOrigin();
			Location d = r.getDestination();
			if((o.getType().equals(LocationType.INTERSECTION))&d.getType().equals(LocationType.INTERSECTION)) {
				g.setStroke(new BasicStroke(highwayWidth));
				g.setColor(highwayColor);
				g.drawLine(scaleXInt(o.getX()), scaleYInt(o.getY()), scaleXInt(d.getX()), scaleYInt(d.getY()));
			}
			else {
				g.setStroke(new BasicStroke(roadWidth));
				g.setColor(roadColor);
				g.drawLine(scaleXInt(o.getX()), scaleYInt(o.getY()), scaleXInt(d.getX()), scaleYInt(d.getY()));
			}
		}

		// Draw locations
		g.setStroke(new BasicStroke());
		for (Location loc : instance.getLocations()) {
			if(!loc.getType().equals(LocationType.INTERSECTION)) {
				int x = scaleXInt(loc.getX()) - locationRadius;
				int y = scaleYInt(loc.getY()) - locationRadius;
				g.setColor(locationColor);
				g.drawOval(x, y, Math.round(2 * locationRadius), Math.round(2 * locationRadius));
			}
			else {
				// Draw intersections?
				int x = scaleXInt(loc.getX()) - intersectionRadius;
				int y = scaleYInt(loc.getY()) - intersectionRadius;
				g.setColor(roadColor);
				g.drawRect(x, y, Math.round(2 * intersectionRadius), Math.round(2 * intersectionRadius));
			}
		}		
	}

	public void drawInstance(Graphics gr) {
		Graphics2D g = (Graphics2D) gr;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Stroke stroke = g.getStroke();
		Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f, new float[]{2f, 2f}, 0);


		if (drawNetwork) {
			drawNetwork(g);
		}

		if (drawVehicles) {
			Map<Location, List<Vehicle>> vehiclesPerLocation = instance.getVehicles()
					.stream()
					.collect(Collectors.groupingBy(Vehicle::getOrigin));

			// Draw vehicles
			for (Map.Entry<Location, List<Vehicle>> entry : vehiclesPerLocation.entrySet()) {
				Location loc = entry.getKey();
				int oX = scaleXInt(loc.getX());
				int oY = scaleYInt(loc.getY());
				int shiftIndex = 1;
				for (Vehicle vehicle : entry.getValue()) {
					int shift = shiftAmount * shiftIndex;
					g.setColor(vehicleColor);
					g.setStroke(stroke);
					g.fillRect(oX - vehicleSize - shift, oY - vehicleSize - shift,
							2 * vehicleSize, 2 * vehicleSize);

					if (drawDestinationLines) {
						drawDestinationLine(g, dashed, oX, oY, shift, vehicle.getDestination());
					}

					shiftIndex++;
				}
			}
		}

		if (drawPassengers) {
			Map<Location, List<Passenger>> passengersPerLocation = instance.getPassengers()
					.stream()
					.collect(Collectors.groupingBy(Passenger::getOrigin));

			// Draw passengers
			for (Map.Entry<Location, List<Passenger>> entry : passengersPerLocation.entrySet()) {
				Location loc = entry.getKey();
				int oX = scaleXInt(loc.getX());
				int oY = scaleYInt(loc.getY());
				int shiftIndex = 1;
				for (Passenger passenger : entry.getValue()) {
					int shift = shiftIndex * shiftAmount;
					g.setColor(passengerColor);
					g.setStroke(stroke);
					g.fillOval(oX - passengerSize - shift, oY - passengerSize - shift,
							2 * passengerSize, 2 * passengerSize);

					if (drawDestinationLines) {
						drawDestinationLine(g, dashed, oX, oY, shift, passenger.getDestination());
					}

					shiftIndex++;
				}
			}
		}
	}

	private void drawDestinationLine(Graphics2D g, Stroke dashed, int oX, int oY, int shift, Location destination) {
		int dX = scaleXInt(destination.getX());
		int dY = scaleYInt(destination.getY());
		g.setStroke(dashed);
		g.setColor(passengerDestLink);
		g.drawLine(oX - shift, oY - shift, dX, dY);
	}

	public void drawFrame(Graphics gr) {
		if (solution == null) {
			return;
		}

		Graphics2D g = (Graphics2D) gr;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (drawNetwork) {
			drawNetwork(g);
		}

		if (drawVehicles) {
			// Draw vehicles
			g.setColor(vehicleColor);
			for (Path<Vehicle> vehiclePath : solution.getVehiclePaths()) {
				TimedArc ta = currentArc(vehiclePath);
				if (ta == null) {
					continue;
				}
				int sh = shiftAmount * shiftFactor(vehiclePath.getCommodity(), ta);
				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				g.fillRect(p.x - vehicleSize - sh, p.y - vehicleSize - sh, 2 * vehicleSize, 2 * vehicleSize);
			}
		}

		// Draw Passengers
		if (drawPassengers) {
			Stroke stroke = g.getStroke();
			Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f, new float [] {2f, 2f}, 0);
			for (Path<Passenger> passengerPath : solution.getPassengerPaths()) {
				Passenger pas = passengerPath.getCommodity();
				if (passengerPath.getPath().isEmpty()) {
					continue;
				}
				TimedArc ta = currentArc(passengerPath);
				if (ta == null) {
					continue;
				}
	
				int sh = shiftAmount * (1 + shiftFactor(pas, ta));
				int shX = sh;
				int shY = sh;
				
				if (ta.getFromTime() < pas.getTimeStart()) {
					g.setColor(passengerInactiveColor);
				}
				else {
					g.setColor(passengerColor);
				}
				if (ta.getPurpose() == ArcPurpose.WAIT_OUT) {
					if (ta.getToLocation().equals(pas.getDestination())) {
						shX = -shX;
						if (ta.getToTime() >= pas.getTimeEnd()) {
							g.setColor(passengerDestInactiveColor);
						}
						else {
							g.setColor(passengerDestColor);
						}
					}
					else {
						shY = -shY;
					}
				}
				
				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				g.fillOval(p.x - passengerSize - shX, p.y - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
				if (drawDestinationLines) {
					// Draw destination lines
					if (!ta.getToLocation().equals(pas.getDestination())) {
						Location dest = pas.getDestination();
						g.setStroke(dashed);
						g.setColor(passengerDestLink);
						g.drawLine(p.x - shX, p.y - shY, scaleXInt(dest.getX()), scaleYInt(dest.getY()));
						g.setStroke(stroke);
					}
				}
			}
		}
		
		// Draw Time Stamp
		if (drawTimeStamp)
		{
			FontMetrics metrics = g.getFontMetrics();
			String str = format.format(currentTime);
			int strHeight = metrics.getHeight();
			int strWidth = metrics.stringWidth(str);
			g.setColor(timestampColor);
			g.drawString(str, (int)Math.floor(width - defaultTranslate - strWidth), (int)Math.ceil(defaultTranslate + strHeight));
		}		
	}

	private Point computePoint(TimedArc ta) {
		double convexComb = (currentTime - ta.getFromTime()) / (ta.getToTime() - ta.getFromTime());
		convexComb = Math.min(convexComb, 1);
		double originX = ta.getFromLocation().getX();
		double destinationX = ta.getToLocation().getX();
		double originY = ta.getFromLocation().getY();
		double destinationY = ta.getToLocation().getY();
		int x = scaleXInt(originX + convexComb * (destinationX - originX));
		int y = scaleYInt(originY + convexComb * (destinationY - originY));
		return new Point(x, y);
	}

	private TimedArc currentArc(Path<?> commodity) {
		List<TimedArc> path = helper.getPath(commodity.getCommodity());
		if (path.isEmpty()) {
			return null;
		}
		
		if (drawFinished && currentTime >= path.get(path.size()-1).getToTime()) {
			return path.get(path.size()-1);
		}
		
		for (TimedArc ta : path) {						
			if ((ta.getFromTime() <= currentTime && currentTime < ta.getToTime())) {
				return ta;
			}
		}
		
		return null;
	}

	public void setDrawNetwork(boolean drawNetwork) {
		this.drawNetwork = drawNetwork;
	}

	public void setDrawVehicles(boolean drawVehicles) {
		this.drawVehicles = drawVehicles;
	}

	public void setDrawPassengers(boolean drawPassengers) {
		this.drawPassengers = drawPassengers;
	}

	public void setDrawDestinationLines(boolean drawDestinationLines) {
		this.drawDestinationLines = drawDestinationLines;
	}

	public void setDrawTimeStamp(boolean drawTimeStamp) {
		this.drawTimeStamp = drawTimeStamp;
	}
	
	public void writeToFile(String formatName, File out) throws IOException {
		BufferedImage bi = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		if (solution != null) {
			drawFrame(g);
		}
		else {
			drawInstance(g);
		}
		ImageIO.write(bi, formatName, out);
	}
	
}
