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
package podrouting.util.cli;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class MainContextFreeRecurseBase extends MainRecurseBase<Object> {

    @Override
    public final Object getContext() {
        return null;
    }

    @Override
    public final void processFile(File file, Supplier<File> outputFile, Object _ctx) throws IOException {
        processFile(file, outputFile);
    }

    public abstract void processFile(File file, Supplier<File> outputFile) throws IOException;

}
