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

import podrouting.data.Solution;
import podrouting.data.assign.AssignedSolution;

import java.util.Optional;
import java.util.OptionalInt;

public class AssignmentModelTools {


    private AssignmentModelTools() {
        throw new AssertionError("This is an utility class and cannot be instantiated.");
    }

    public static Optional<AssignedSolution> getAssignedSolution(Solution sol) {
        return AssignmentModelORTools.getAssignedSolution(sol);
    }

    public static OptionalInt getAssignmentValue(Solution sol) {
        return AssignmentModelORTools.getAssignmentValue(sol);
    }

}
