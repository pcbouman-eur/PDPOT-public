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
package podrouting.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import podrouting.data.Instance;
import podrouting.data.Solution;

public final class IOUtils {

    private final static Logger log = LoggerFactory.getLogger(IOUtils.class);

	public static final String SOLUTION_POSTFIX = ".solution.json";
	public static final String INSTANCE_POSTFIX = ".json";

	private IOUtils() {}
	

	public static Instance readInstance(File jsonFile) throws IOException
	{
		ObjectMapper om = new ObjectMapper();
		return om.readValue(jsonFile, Instance.class);
	}
	
	public static void writeInstance(Instance i, File jsonFile) throws IOException {
		ObjectMapper om = new ObjectMapper();
		om.writeValue(jsonFile, i);
	}
	
	public static Solution readSolution(File jsonFile) throws IOException
	{
		ObjectMapper om = new ObjectMapper();
		return om.readValue(jsonFile, Solution.class);
	}
	
	public static void writeSolution(Solution s, File jsonFile) throws IOException {
		ObjectMapper om = new ObjectMapper();
		om.writeValue(jsonFile, s);
	}

    public static List<ZipData<Instance>> readInstancesFromZip(File zipFile) throws IOException {
        return readZipFile(zipFile, Instance.class);
    }

    public static List<ZipData<Solution>> readSolutionsFromZip(File zipFile) throws IOException {
        return readZipFile(zipFile, Solution.class);
    }

    public record ZipData<E> (E object, String path, String filename, String parent) {}

    private static <E> List<ZipData<E>> readZipFile(File zipFile, Class<E> clz) throws IOException {
        ObjectMapper om = new ObjectMapper();
        List<ZipData<E>> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                    String path = entry.getName();
                    try (InputStream in = zip.getInputStream(entry)) {
                        E obj = om.readValue(in, clz);
                        String dir = "";
                        String name = path;
                        if (path.contains("/")) {
                            dir = path.substring(0, path.lastIndexOf("/"));
                            name = path.substring(path.lastIndexOf("/") + 1);
                        }
                        result.add(new ZipData<>(obj, path, name, dir));
                    } catch (JsonParseException | JsonMappingException ex) {
                        log.warn("Skipping .json file {} from zip file {}. A {} exception occured. Message: {}",
                                path, zipFile, ex.getClass().getSimpleName(), ex.getMessage());
                    }
                }
            }
        }
        return result;
    }
	
}
