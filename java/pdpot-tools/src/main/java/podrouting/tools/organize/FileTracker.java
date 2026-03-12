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
package podrouting.tools.organize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

public class FileTracker {

    private final Map<Long, List<File>> uniqueFiles = new TreeMap<>();

    public boolean addFile(File file) throws IOException {
        Long hash = hashFile(file);
        List<File> fileList = uniqueFiles.get(hash);
        if (fileList == null) {
            fileList = new ArrayList<>();
            fileList.add(file);
            uniqueFiles.put(hash, fileList);
        }
        else {
            for (File other : fileList) {
                if (checkEqual(file, other)) {
                    // Duplicate found
                    return false;
                }
            }
            fileList.add(file);
        }
        return true;
    }

    private Long hashFile(File file) throws IOException {
        CRC32 hashObj = new CRC32();
        for (byte b : Files.readAllBytes(file.toPath())) {
            hashObj.update(b);
        }
        return hashObj.getValue();
    }

    private boolean checkEqual(File file1, File file2) throws IOException {
        byte[] f1Bytes = Files.readAllBytes(file1.toPath());
        byte[] f2Bytes = Files.readAllBytes(file2.toPath());
        return Arrays.equals(f1Bytes, f2Bytes);
    }


}
