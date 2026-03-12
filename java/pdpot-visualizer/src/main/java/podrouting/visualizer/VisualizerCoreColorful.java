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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import podrouting.data.Instance;
import podrouting.data.Location;
import podrouting.data.LocationType;
import podrouting.data.Passenger;
import podrouting.data.Road;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.util.SolutionHelper;

public class VisualizerCoreColorful {

	private int width, height;

	private Instance instance;
	private Solution solution;
	private AssignedSolution assignedSolution;
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
	private final Color passengerStartColor = Color.WHITE;
	private final Color passengerEndColor = Color.BLACK;
	private final Color passengerBorderColor = Color.BLACK;
	private final Color passengerNotServedColor = Color.LIGHT_GRAY;
	private final Color passengerColor = Color.RED;
	private final Color passengerDestColor = Color.GREEN;
	private final Color passengerDestInactiveColor = new Color(0, 76, 0);
	private final Color passengerDestLink = new Color(175,175,175,150);
	private final int shiftAmount = 7;
	private final int shiftAmountInactive = 5;

	private final Color timestampColor = Color.BLACK;
	private final DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

	private final boolean drawInactive = true;
	private final boolean drawFinished = true;

	private boolean drawNetwork = true;
	private boolean drawVehicles = true;
	private boolean drawPassengers = true;
	private final boolean drawPassengersNotServed = true;
	private boolean drawDestinationLines = true;
	private boolean drawTimeStamp = true;

	private Map<Vehicle, Color> vehicleColorMap;
	private Map<Passenger, Color> passengerColorMap;
	private Map<Location, List<Passenger>> passengerNotServedMap;
	private Map<Passenger, Vehicle> firstVehicleMap;
	private Map<Vehicle, Map<TimedArc, List<Passenger>>> vehiclePassengerMap;
	
	

	public void setInstance(Instance i) {
		this.instance = i;
		computeScale();
	}

	public void setSolution(Solution sol) {
		this.instance = sol.getInstance();
		this.solution = sol;
		this.currentTime = 0;
		this.helper = new SolutionHelper(sol, drawInactive, drawFinished, true);

		//Vehicle rainbow colors
		this.vehicleColorMap = new LinkedHashMap<>();
		//List<Color> colors = createRainbowColor(solution.getVehiclePaths().size());
		List<Vehicle> vehicleList = new ArrayList<>();
		for(Path<Vehicle> path: solution.getVehiclePaths()) {
			vehicleList.add(path.getCommodity());
		}
		vehicleColorMap = TolColorScheme.getColorMap(vehicleList);

		//Passenger start colors
		this.passengerColorMap = new LinkedHashMap<>();
		for(Path<Passenger> path: solution.getPassengerPaths()) {
			passengerColorMap.put(path.getCommodity(), passengerStartColor);
		}

		//Passenger not served
		this.passengerNotServedMap = new LinkedHashMap<>();
		for(Passenger p: instance.getPassengers()) {
			if(passengerColorMap.containsKey(p)) {
				continue;
			}
			if(!passengerNotServedMap.containsKey(p.getOrigin())) {
				passengerNotServedMap.put(p.getOrigin(), new ArrayList<>());
			}
			passengerNotServedMap.get(p.getOrigin()).add(p);
		}

		//Initialize first vehicle color
		firstVehicleMap = new LinkedHashMap<>();
		for(Path<Passenger> path: solution.getPassengerPaths()) {
			firstVehicleMap.put(path.getCommodity(), null);
		}

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
				throw new IllegalArgumentException("Passenger "+p.getId()+" does not use the arc " + ta);
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
			if(d.getName().equals("sink")) {
				continue;
			}

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
			if(loc.getName().equals("sink")) {
				continue;
			}
			if(loc.getType().equals(LocationType.PARKING)) {
				int x = scaleXInt(loc.getX()) - locationRadius;
				int y = scaleYInt(loc.getY()) - locationRadius;
				g.setColor(locationColor);
				g.drawRect(x, y, Math.round(2 * locationRadius), Math.round(2 * locationRadius));
			}
			else if(loc.getType().equals(LocationType.STOP)) {
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

	public void drawStartFrame(Graphics gr) {
		if (solution == null) {
			return;
		}

		Graphics2D g = (Graphics2D) gr;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (drawNetwork) {
			drawNetwork(g);
		}
		Map<Location, Integer> startVehicle = new LinkedHashMap<>();
		if (drawVehicles) {
			// Draw vehicles
			for (Path<Vehicle> vehiclePath : solution.getVehiclePaths()) {
				g.setColor(vehicleColorMap.get(vehiclePath.getCommodity()));
				TimedArc ta = currentArc(vehiclePath);
				if (ta == null) {
					continue;
				}
				if(!startVehicle.containsKey(ta.getFromLocation())) {
					startVehicle.put(ta.getFromLocation(), 0);
				}
				int sh = shiftAmount * startVehicle.replace(ta.getFromLocation(), startVehicle.get(ta.getFromLocation())+1);
				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				g.fillRect(p.x - vehicleSize - sh, p.y - vehicleSize - sh, 2 * vehicleSize, 2 * vehicleSize);

			}
		}

		Map<Location, Integer> startPassenger = new LinkedHashMap<>();
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

				if(!startPassenger.containsKey(ta.getFromLocation())) {
					startPassenger.put(ta.getFromLocation(), 1);
				}
				int sh = shiftAmountInactive * startPassenger.replace(ta.getFromLocation(), startPassenger.get(ta.getFromLocation())+1);

				int shX = sh;
				int shY = sh;

				g.setColor(passengerColorMap.get(pas));
				shY = -shY;
				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				int px = p.x;
				int py = p.y;

				g.fillOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
				g.setColor(passengerBorderColor);
				g.drawOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
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

		if (drawPassengersNotServed) {
			Stroke stroke = g.getStroke();
			Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f, new float [] {2f, 2f}, 0);
			for(Entry<Location, List<Passenger>> entry: passengerNotServedMap.entrySet()) {
				Location origin = entry.getKey();
				List<Passenger> list = entry.getValue();
				for(int i = 0; i < list.size(); i++) {
					Passenger pas = list.get(i);
					int sh = shiftAmountInactive * (1+i);
					int shX = sh;
					int shY = sh;
					shX = -shX;
					shY = -shY;

					Point p = new Point(scaleXInt(origin.getX()), scaleYInt(origin.getY()));
					int px = p.x;
					int py = p.y;

					g.setColor(passengerNotServedColor);
					g.fillOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
					g.setColor(passengerBorderColor);
					g.drawOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
					if (drawDestinationLines) {
						// Draw destination lines
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
			for (Path<Vehicle> vehiclePath : solution.getVehiclePaths()) {
				g.setColor(vehicleColorMap.get(vehiclePath.getCommodity()));
				TimedArc ta = currentArc(vehiclePath); //Same result: TimedArc ta2 = assignedSolution.getTimedArcForVehicle(vehiclePath.getCommodity(), currentTime);

				if (ta == null) {
					continue;
				}
				int sh = shiftAmount * assignedSolution.getVehicleIndex(ta, vehiclePath.getCommodity()); 
				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				g.fillRect(p.x - vehicleSize - sh, p.y - vehicleSize - sh, 2 * vehicleSize, 2 * vehicleSize);
			}
		}

		// Draw Passengers
		if (drawPassengers) {
			Map<TimedArc, List<Passenger>> indexPassengerMap = calcPassengerIndex();
			Map<TimedArc, Integer> tempTimedArcPassengerMap = new LinkedHashMap<>();
			Stroke stroke = g.getStroke();
			Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f, new float [] {2f, 2f}, 0);
			for (Path<Passenger> passengerPath : solution.getPassengerPaths()) {
				Passenger pas = passengerPath.getCommodity();
				if (passengerPath.getPath().isEmpty()) {
					continue;
				}
				TimedArc ta = assignedSolution.getTimedArcForPassenger(pas, currentTime); //currentArc(passengerPath);

				if (ta == null) {
					continue;
				}


				Vehicle v = assignedSolution.getVehicleForPassenger(pas, ta, currentTime); //computeVehicle(ta, pas);
				if(v!=null) {
					if(firstVehicleMap.get(pas)==null) {
						passengerColorMap.put(pas, vehicleColorMap.get(v));
						firstVehicleMap.put(pas, v);
					}
				}

				int tempShiftAmount = shiftAmount;

				int sh = 0;



				if(v!=null) {
					//TODO: vehicleTimeMap is not updated correctly
					sh = shiftAmount * (1 + assignedSolution.getPassengerIndex(ta, pas, v, currentTime, true));

				}
				else {
					TimedArc ta2 = ta;
					if(ta2.getPurpose()==ArcPurpose.WAIT_OUT) {
						ta2 = ta2.toWaitInside();
					}
					if(!tempTimedArcPassengerMap.containsKey(ta2)) {
						tempTimedArcPassengerMap.put(ta2, 1);
					}
					
					sh = shiftAmountInactive * indexPassengerMap.get(ta2).indexOf(pas)+1;
					
				}
				int shX = sh;
				int shY = sh;

				g.setColor(passengerColorMap.get(pas));
				if (v==null) {
					if (ta.getToLocation().equals(pas.getDestination())) {
						shY = -shY;
						passengerColorMap.replace(pas, passengerEndColor);
						if (ta.getToTime() >= pas.getTimeEnd()) {
							g.setColor(passengerColorMap.get(pas));
						}
						else {
							g.setColor(passengerColorMap.get(pas));
						}
					}
					else {
						shY = -shY;
					}
				}
				else {
					shX = -shX;
				}


				Point p = computePoint(ta);
				if (p == null) {
					continue;
				}
				int px = p.x;
				int py = p.y;
				if(v!=null) {
					TimedArc taVehicle = assignedSolution.getTimedArcForVehicle(v, currentTime);
					int shv = tempShiftAmount * assignedSolution.getVehicleIndex(taVehicle, v);
					px -= shv;
					py -= shv;
				}
				g.fillOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
				g.setColor(passengerBorderColor);
				g.drawOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
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

		if (drawPassengersNotServed) {
			Stroke stroke = g.getStroke();
			Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10f, new float [] {2f, 2f}, 0);
			for(Entry<Location, List<Passenger>> entry: passengerNotServedMap.entrySet()) {
				Location origin = entry.getKey();
				List<Passenger> list = entry.getValue();
				for(int i = 0; i < list.size(); i++) {
					Passenger pas = list.get(i);
					int sh = shiftAmountInactive * (1+i);
					int shX = sh;
					int shY = sh;
					shX = -shX;
					shY = -shY;

					Point p = new Point(scaleXInt(origin.getX()), scaleYInt(origin.getY()));
					int px = p.x;
					int py = p.y;

					g.setColor(passengerNotServedColor);
					g.fillOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
					g.setColor(passengerBorderColor);
					g.drawOval(px - passengerSize - shX, py - passengerSize - shY, 2 * passengerSize, 2 * passengerSize);
					if (drawDestinationLines) {
						// Draw destination lines
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

	private Map<TimedArc, List<Passenger>> calcPassengerIndex() {
		Map<TimedArc, List<Passenger>> indexPassengerMap = new LinkedHashMap<>();
		Map<TimedArc, List<Passenger>> finishPassengerMap = new LinkedHashMap<>(); //These passengers are at their destination
		Map<TimedArc, List<Passenger>> servicePassengerMap = new LinkedHashMap<>(); //These passengers still have to go to their destination

		for (Path<Passenger> passengerPath : solution.getPassengerPaths()) {
			Passenger pas = passengerPath.getCommodity();
			if (passengerPath.getPath().isEmpty()) {
				continue;
			}

			TimedArc ta = assignedSolution.getTimedArcForPassenger(pas, currentTime); //currentArc(passengerPath);
			if (ta == null) {
				continue;
			}

			Vehicle v = assignedSolution.getVehicleForPassenger(pas, ta, currentTime); //computeVehicle(ta, pas);
			if(v!=null) {
				continue;
			}

			TimedArc ta2 = ta;
			if(ta2.getPurpose()==ArcPurpose.WAIT_OUT) {
				ta2 = ta2.toWaitInside();
			}
			
			if (ta.getToLocation().equals(pas.getDestination())) {
				if(!finishPassengerMap.containsKey(ta2)) {
					finishPassengerMap.put(ta2, new ArrayList<>());
				}
				List<Passenger> pasList = finishPassengerMap.get(ta2);
				pasList.add(passengerPath.getCommodity());
				finishPassengerMap.replace(ta2, pasList);		
			}
			else {
				if(!servicePassengerMap.containsKey(ta2)) {
					servicePassengerMap.put(ta2, new ArrayList<>());
				}
				List<Passenger> pasList = servicePassengerMap.get(ta2);
				pasList.add(passengerPath.getCommodity());
				servicePassengerMap.replace(ta2, pasList);	
			}
		}
		
		for(Entry<TimedArc, List<Passenger>> entry: finishPassengerMap.entrySet()) {
			if(!indexPassengerMap.containsKey(entry.getKey())) {
				indexPassengerMap.put(entry.getKey(), new ArrayList<>());
			}
			List<Passenger> pasList = indexPassengerMap.get(entry.getKey());
			pasList.addAll(entry.getValue());
			indexPassengerMap.replace(entry.getKey(), pasList);
		}
		for(Entry<TimedArc, List<Passenger>> entry: servicePassengerMap.entrySet()) {
			if(!indexPassengerMap.containsKey(entry.getKey())) {
				indexPassengerMap.put(entry.getKey(), new ArrayList<>());
			}
			List<Passenger> pasList = indexPassengerMap.get(entry.getKey());
			pasList.addAll(entry.getValue());
			indexPassengerMap.replace(entry.getKey(), pasList);
		}
		
		return indexPassengerMap;
	}

	private Vehicle computeVehicle(TimedArc ta, Passenger pas) {
		Vehicle v = null;
		if(ta==null) {
			return v;
		}
		for(Entry<Vehicle, Map<TimedArc, List<Passenger>>> entry: vehiclePassengerMap.entrySet()) {
			if(entry.getValue().containsKey(ta) && entry.getValue().get(ta).contains(pas)) {
				v = entry.getKey();
				break;
			}
		}
		return v;
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

		//int finalTime = path.get(path.size() - 1).getToTime();
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

	public void setVehiclePassengerMap(Map<Vehicle, Map<TimedArc, List<Passenger>>> map) {
		this.vehiclePassengerMap = map;
	}

	public void setAssignedSolution(AssignedSolution assignedSolution) {
		this.assignedSolution = assignedSolution;
	}

	public List<Color> createRainbowColor(int numColors) {
		int stepLength = (int) Math.ceil(numColors/6d);
		List<Color> colors = new ArrayList<Color>();
		for (int r=0; r<stepLength; r++) colors.add(new Color(r*255/stepLength, 255, 0));
		for (int g=stepLength; g>0; g--) colors.add(new Color(255, g*255/stepLength, 0));
		for (int b=0; b<stepLength; b++) colors.add(new Color(255, 0, b*255/stepLength));
		for (int r=stepLength; r>0; r--) colors.add(new Color(r*255/stepLength, 0, 255));
		for (int g=0; g<stepLength; g++) colors.add(new Color(0, g*255/stepLength, 255));
		for (int b=stepLength; b>0; b--) colors.add(new Color(0, 255, b*255/stepLength));
		return colors;                     
	}

	public void writeToFile(String formatName, File out) throws IOException {
		BufferedImage bi = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
		Graphics g = bi.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		drawFrame(g);
		ImageIO.write(bi, formatName, out);
	}

}
