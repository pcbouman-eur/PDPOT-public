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

import com.google.ortools.linearsolver.MPSolver;

public enum ORToolsSolverChoice {
    SCIP,
    CBC,
    CPLEX,
    GUROBI,
    BOP;

    public MPSolver.OptimizationProblemType getOptimizationProblemType() {
        return switch(this) {
            case SCIP -> MPSolver.OptimizationProblemType.SCIP_MIXED_INTEGER_PROGRAMMING;
            case CBC -> MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING;
            case CPLEX -> MPSolver.OptimizationProblemType.CPLEX_MIXED_INTEGER_PROGRAMMING;
            case GUROBI -> MPSolver.OptimizationProblemType.GUROBI_MIXED_INTEGER_PROGRAMMING;
            case BOP -> MPSolver.OptimizationProblemType.BOP_INTEGER_PROGRAMMING;
        };
    }

}
