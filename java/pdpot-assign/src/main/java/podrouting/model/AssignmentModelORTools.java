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
package podrouting.model;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Passenger;
import podrouting.data.Solution;
import podrouting.data.Vehicle;
import podrouting.data.assign.AssignedSolution;
import podrouting.data.timed.ArcPurpose;
import podrouting.data.timed.Path;
import podrouting.data.timed.TimedArc;
import podrouting.util.Pair;
import podrouting.util.SolutionHelper;

import java.util.*;
import java.util.Map.Entry;

public class AssignmentModelORTools {

	private static final Logger log = LoggerFactory.getLogger(AssignmentModelORTools.class);

	private Solution sol;
	private final SolutionHelper helper;

	private MPSolver model;

	private Map<Vehicle,Map<TimedArc,Map<Passenger, MPVariable>>> vars;
	private Map<Pair<Vehicle, TimedArc>, List<Pair<Passenger, MPVariable>>> predecessorVars;
	private Map<Pair<Vehicle, TimedArc>, List<Pair<Passenger, MPVariable>>> successorVars;
	private Map<Passenger, List<TimedArc>> outsideTransferMap;

	private List<TransferRecord> records;

	private List<MPVariable> varList;
	private List<MPConstraint> constraintList;

	private final double eps = 0.001;

    private MPSolver.ResultStatus status;

	AssignmentModelORTools(Solution sol) {

        this.sol = sol;

		// Update solution. If we see the pattern DRIVE -> WAIT_IN -> WAIT_OUT. We change this pattern to: DRIVE -> WAIT_OUT -> WAIT_OUT
		List<Path<Passenger>> passengerPaths = new ArrayList<>();
		for (Path<Passenger> path : sol.getPassengerPaths()) {
			List<TimedArc> timedArcs = new ArrayList<>();
			// Empty paths can stay empty
			if (!path.getPath().isEmpty()) {
				TimedArc prev = null;
				TimedArc prevprev = null;
				timedArcs.add(path.getFirst());
				for (TimedArc ta : path.getPath()) {
					if (prev != null && prevprev != null) {
						if (prevprev.getPurpose() == ArcPurpose.DRIVE && prev.getPurpose() == ArcPurpose.WAIT_IN && ta.getPurpose() == ArcPurpose.WAIT_OUT) {
							prev = prev.toWaitOutside();
						}
						timedArcs.add(prev);
					}
					prevprev = prev;
					prev = ta;
				}
				timedArcs.add(prev);
			}

			passengerPaths.add(new Path<>(path.getCommodity(), timedArcs));
		}
		this.sol = new Solution(sol.getInstance(), passengerPaths, sol.getVehiclePaths());
		
		this.helper = new SolutionHelper(sol);
		initModel();
	}


	private void initModel() {
        Loader.loadNativeLibraries();
        this.status = null;

        model = new MPSolver("AssignmentModel", MPSolver.OptimizationProblemType.SCIP_MIXED_INTEGER_PROGRAMMING);

		vars = new LinkedHashMap<>();
		predecessorVars = new LinkedHashMap<>();
		successorVars = new LinkedHashMap<>();
		records = new ArrayList<>();
		varList = new ArrayList<>();
		constraintList = new ArrayList<>();
		outsideTransferMap = new LinkedHashMap<>();

        MPObjective objExpr = model.objective();
        objExpr.setMinimization();

		// Determine outside transfers
		for (Path<Passenger> path : sol.getPassengerPaths()) {
			Passenger p = path.getCommodity();
			TimedArc prev = null;
			for (TimedArc ta : path.getPath()) {
				if (ta == null) {
					// TODO: Do we really want this?
					continue;
				}
				if (ta.getPurpose() != ArcPurpose.WAIT_OUT || (prev!=null && prev.getPurpose()==ArcPurpose.DRIVE && ta.getPurpose()==ArcPurpose.WAIT_OUT)) {
					// This passenger needs to fill up some capacity
                    String cname = "choose_p_"+p.getId()+"_t_"+ta.getFromTime();
                    MPConstraint expr = model.makeConstraint(1.0, 1.0, cname);
					boolean add = false;
					if(ta.getPurpose()==ArcPurpose.WAIT_OUT) {
						ta = ta.toWaitInside();
						if(!outsideTransferMap.containsKey(p)) {
							outsideTransferMap.put(p, new ArrayList<>());
						}
						outsideTransferMap.get(p).add(ta);
					}
					for (Vehicle v: helper.getVehicles(ta)) {
                        MPVariable var = model.makeBoolVar("x_p_"+p.getId()+"_v_"+v.getId()+"_t1_"+ta.getFromTime()+"_t2_"+ta.getToTime());
						addVar(v,ta,p,var);
                        expr.setCoefficient(var, 1.0);
						add = true;
					}
					if (!add) {
						System.out.println("Weird?");
					}
					constraintList.add(expr);
				}
				prev = ta;
			}

			for (Pair<TimedArc, TimedArc> pair : path.getArcPairs()) {
				if (pair.first.getPurpose() == ArcPurpose.WAIT_OUT
						|| 
						(pair.first.getPurpose()==ArcPurpose.WAIT_IN &&	pair.second.getPurpose() == ArcPurpose.WAIT_OUT) // Allow DRIVE -> WAIT_OUT
						) {
					continue;
				}
				TimedArc ta2 = pair.second;
				if(ta2.getPurpose()==ArcPurpose.WAIT_OUT) {
					ta2 = ta2.toWaitInside();
				}
				for (Vehicle v1 : helper.getVehicles(pair.first)) {
					for (Vehicle v2 : helper.getVehicles(ta2)) {
						if (!v1.equals(v2)) {
							String transfer = "p_"+p.getId()+"_v1_"+v1.getId()+"_v2_"+v2.getId()
									+"_t1_"+pair.first.getFromTime()+"_t2_"+pair.second.getFromTime()
									+"_t3_"+pair.second.getToTime();

                            MPVariable var = model.makeNumVar(0, Double.POSITIVE_INFINITY, "t_"+transfer);
							if(!checkVehicleTimedArc(v2, pair.first)) { //Check if vehicle v2 is also present at previous timed
								var.setBounds(0,0);
							}
							varList.add(var);
							records.add(new TransferRecord(pair, v1, v2, p, var));

							// Is het correct dat we hier in beide gevallen pair.first hebben??

							Pair<Vehicle, TimedArc> pred = new Pair<>(v2, pair.first);
							List<Pair<Passenger, MPVariable>> temp1 =
									predecessorVars.computeIfAbsent(pred, ignored -> new ArrayList<>());
							temp1.add(new Pair<>(p, var));
							predecessorVars.replace(pred, temp1);

							Pair<Vehicle, TimedArc> succ = new Pair<>(v1, pair.first);

							List<Pair<Passenger, MPVariable>> temp2 =
									successorVars.computeIfAbsent(succ, ignored -> new ArrayList<>());
							temp2.add(new Pair<>(p, var));
							successorVars.replace(succ, temp2);

                            MPConstraint con = model.makeConstraint(-1, Double.POSITIVE_INFINITY, "transfer_force_"+transfer);
                            con.setCoefficient(var, 1.0);
                            con.setCoefficient(getVar(v1,pair.first,p), -1.0);
                            con.setCoefficient(getVar(v2,ta2,p), -1.0);
							constraintList.add(con);

                            con = model.makeConstraint(Double.NEGATIVE_INFINITY, 0, "transfer_first_"+transfer);
                            con.setCoefficient(var, 1.0);
                            con.setCoefficient(getVar(v1,pair.first,p), -1.0);
							constraintList.add(con);

                            con = model.makeConstraint(Double.NEGATIVE_INFINITY, 0, "transfer_second_"+transfer);
                            con.setCoefficient(var, 1.0);
                            con.setCoefficient(getVar(v2,ta2,p), -1.0);
							constraintList.add(con);

                            objExpr.setCoefficient(var, 1.0);
						}
					}
				}
			}
		}

		for (Entry<Vehicle,Map<TimedArc,Map<Passenger,MPVariable>>> e : vars.entrySet()) {
			Vehicle v = e.getKey();
			Map<TimedArc,Map<Passenger,MPVariable>> map = e.getValue();
			for (Entry<TimedArc,Map<Passenger,MPVariable>> subMap : map.entrySet()) {
                TimedArc ta = subMap.getKey();
                String cname = "cap_v_"+v.getId()+"_t_"+ta.getFromTime();
				MPConstraint expr = model.makeConstraint(Double.NEGATIVE_INFINITY, v.getCapacity(), cname);
				//Each passenger can only have 1 variable

				// u^{kv}_a variables
				for (Entry<Passenger, MPVariable> entry : subMap.getValue().entrySet()) {
					if(helper.getPath(entry.getKey()).contains(subMap.getKey())) { //Check if passenger indeed has the arc on its path (otherwise it was originally a wait_out arc)
						MPVariable var = entry.getValue();
                        // This passenger is performing an outside transfer, therefore it will still be assigned to a vehicle, but not take up any capacity since it is already exiting the vehicle.
						boolean exclude = ta.getPurpose()==ArcPurpose.WAIT_IN
                                && (outsideTransferMap.containsKey(entry.getKey())
                                && outsideTransferMap.get(entry.getKey()).contains(ta));
                        if (!exclude) {
                            expr.setCoefficient(var, 1.0);
                        }
					}
				}

				// t- variables. Pred
				Pair<Vehicle, TimedArc> pred = new Pair<>(v, ta);
				if(predecessorVars.containsKey(pred)) {
					for(Pair<Passenger, MPVariable> pair: predecessorVars.get(pred)) {
						MPVariable var = pair.second;
                        expr.setCoefficient(var, 1.0);
					}
				}

				// t+ variables. Succ
				Pair<Vehicle, TimedArc> succ = new Pair<>(v, ta);
				if(successorVars.containsKey(succ)) {
					for(Pair<Passenger, MPVariable> pair: successorVars.get(succ)) {
						MPVariable var = pair.second;
                        expr.setCoefficient(var, -1.0);
					}
				}

				constraintList.add(expr);
			}
		}
	}

	private boolean checkVehicleTimedArc(Vehicle v, TimedArc a) {
		for(Path<Vehicle> path: sol.getVehiclePaths()) {
			if(path.getCommodity().equals(v)) {
				for(TimedArc ta: path.getPath()) {
					if(ta.equals(a)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Map<Vehicle,Map<TimedArc,List<Passenger>>> getVehiclePassengerMap() {

		Map<Vehicle,Map<TimedArc,List<Passenger>>> result1 = new LinkedHashMap<>();
			for(Entry<Vehicle, Map<TimedArc, Map<Passenger, MPVariable>>> e1: vars.entrySet()) {
				Map<TimedArc,List<Passenger>> result2 = new LinkedHashMap<>(); 
				for(Entry<TimedArc, Map<Passenger, MPVariable>> e2: e1.getValue().entrySet()) {
					List<Passenger> result3 = new ArrayList<>(); 
					for(Entry<Passenger, MPVariable> e3: e2.getValue().entrySet()) {
						if(e3.getValue().solutionValue()>=(1-eps)) {
							result3.add(e3.getKey());
						}
					}
					result2.put(e2.getKey(), result3);
				}
				result1.put(e1.getKey(), result2);
			}

            // Go through the transfer variables
            for (TransferRecord rec : records) {
                if (rec.var.solutionValue() >= (1 - eps)) {
                    log.trace("Processing inside transfer {}", rec);
                    List<Passenger> firstVehicleList = result1.get(rec.firstVehicle).get(rec.firstArc);
                    List<Passenger> secondVehicleList = result1.get(rec.secondVehicle).get(rec.firstArc);
                    // If a transfer variable is positive, remove the passenger from the first vehicle
                    while (firstVehicleList != null && firstVehicleList.contains(rec.passenger)) {
                        firstVehicleList.remove(rec.passenger);
                    }
                    if (secondVehicleList == null) {
                        secondVehicleList = new ArrayList<>();
                        result1.get(rec.secondVehicle).put(rec.firstArc, secondVehicleList);
                    }
                    // TODO: is the following needed??
                    if (!secondVehicleList.contains(rec.passenger)) {
                        secondVehicleList.add(rec.passenger);
                    }
                }
            }
		return result1;
	}

	private MPVariable getVar(Vehicle v, TimedArc ta, Passenger p) {
		return vars.getOrDefault(v, Collections.emptyMap())
				.getOrDefault(ta, Collections.emptyMap())
				.getOrDefault(p, null);
	}

	private void addVar(Vehicle v, TimedArc ta, Passenger p, MPVariable var) {
		if (!vars.containsKey(v)) {
			vars.put(v, new LinkedHashMap<>());
		}
		Map<TimedArc,Map<Passenger,MPVariable>> subMap = vars.get(v);
		if (!subMap.containsKey(ta)) {
			subMap.put(ta, new LinkedHashMap<>());
		}
		Map<Passenger,MPVariable> subSubMap = subMap.get(ta);
		subSubMap.put(p, var);
	}

	public boolean isFeasible()  {
        MPSolver.ResultStatus status = model.solve();
		return EnumSet.of(MPSolver.ResultStatus.FEASIBLE, MPSolver.ResultStatus.OPTIMAL).contains(status);
	}

	public void exportModel(String outputFile) {
        model.write(outputFile);
	}

	public OptionalInt solve() {
        if (status == null) {
            status = model.solve();
        }
        if (!EnumSet.of(MPSolver.ResultStatus.FEASIBLE, MPSolver.ResultStatus.OPTIMAL).contains(status)) {
            log.info("Done solving assignment model. It seems to be infeasible with status {}", status);
            return OptionalInt.empty();
		}
        int value = (int) Math.round(model.objective().value());
        log.info("Done solving assignment model. It has objective value {}", value);
		return OptionalInt.of(value);
	}

	public void clearModel() {
		model.clear();
	}

	static Optional<AssignedSolution> getAssignedSolution(Solution sol) {
        AssignmentModelORTools am = new AssignmentModelORTools(sol);
        OptionalInt obj = am.solve();
        Optional<AssignedSolution> result;
        if (obj.isPresent()) {
            result = Optional.of(AssignedSolution.createFromMap(sol, am.getVehiclePassengerMap(), am.getRecordOutsideTransferMap()));
        }
        else {
            result = Optional.empty();
        }
        am.clearModel();
        return result;
	}

	static OptionalInt getAssignmentValue(Solution sol) {
        AssignmentModelORTools am = new AssignmentModelORTools(sol);
		OptionalInt value = am.solve();
        am.clearModel();
		return value;
	}



	public Map<Passenger, List<TimedArc>> getRecordOutsideTransferMap() {
		return outsideTransferMap;
	}

	/**
	 * Helper class to trace the inside transfer decisions
	 */
	private static final class TransferRecord {

		private final MPVariable var;
		private final TimedArc firstArc, secondArc;
		private final Vehicle firstVehicle, secondVehicle;
		private final Passenger passenger;

		public TransferRecord(Pair<TimedArc,TimedArc> pair, Vehicle v1, Vehicle v2, Passenger p, MPVariable var) {
			this.var = var;
			this.firstArc = pair.first;
			this.secondArc = pair.second;
			this.firstVehicle = v1;
			this.secondVehicle = v2;
			this.passenger = p;
		}

		@Override
		public String toString() {
			return "Passenger "+passenger.getId()+" from vehicle "+firstVehicle.getId()
					+" to "+secondVehicle.getId() + " on arcs " + firstArc + " and " + secondArc;
		}
	}

}
