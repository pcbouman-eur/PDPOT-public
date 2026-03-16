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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    public static boolean isZipFile(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static List<ZipData<Instance>> readInstancesFromZip(File zipFile) throws IOException {
        return readZipFile(zipFile, Instance.class);
    }

    public static List<ZipData<Solution>> readSolutionsFromZip(File zipFile) throws IOException {
        return readZipFile(zipFile, Solution.class);
    }

    public record ZipData<E> (E object, String path, String filename, String parent) {}

    private static boolean isSolution(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(SOLUTION_POSTFIX);
    }

    private static boolean isInstance(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(INSTANCE_POSTFIX);
    }

    private static <E> List<ZipData<E>> readZipFile(File zipFile, Class<E> clz) throws IOException {
        ObjectMapper om = new ObjectMapper();
        List<ZipData<E>> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && isInstance(entry.getName())) {
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

    public static List<FileReference> scanForFileReferences(File directory) throws IOException {
        return Files.walk(Paths.get(directory.toURI()))
            .map(IOUtils::toFileReference)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    public static List<ZipReference> scanZipForJsonReferences(File zipFile) throws IOException {
        List<ZipReference> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && isSolution(entry.getName())) {
                    ZipReference ref = new ZipReference(zipFile, entry.getName(), true);
                    result.add(ref);
                }
                else if (!entry.isDirectory() && isInstance(entry.getName())) {
                    ZipReference ref = new ZipReference(zipFile, entry.getName(), false);
                    result.add(ref);
                }
            }
        }
        return result;
    }

    private static Optional<FileReference> toFileReference(Path p) {
        if (isSolution(p.toString())) {
            return Optional.of(new FileReference(p.toFile(), true));
        }
        if (isInstance(p.toString())) {
            return Optional.of(new FileReference(p.toFile(), false));
        }
        return Optional.empty();
    }
    public sealed interface Reference permits ZipReference, FileReference {
        String source();
        boolean solution();

        <E> E read(Class<E> clz) throws IOException;


        default Instance readInstance() throws IOException {
            if (solution()) {
                return readSolution().getInstance();
            }
            return read(Instance.class);
        }

        default Solution readSolution() throws IOException {
            if (!solution()) {
                throw new IllegalStateException("This is not a reference a solution file");
            }
            return read(Solution.class);
        }
    }
    public record ZipReference(File zipFile, String entryName, boolean solution) implements Reference {
        @Override
        public String source() {
            return zipFile.getAbsolutePath()+":"+entryName;
        }

        @Override
        public <E> E read(Class<E> clz) throws IOException {
            ObjectMapper om = new ObjectMapper();
            try (ZipFile zip = new ZipFile(zipFile)) {
                try (InputStream is = zip.getInputStream(zip.getEntry(entryName))) {
                    return om.readValue(is, clz);
                }
            }
        }
    }

    public record FileReference(File file, boolean solution) implements Reference {
        @Override
        public String source() {
            return file.getAbsolutePath();
        }

        public <E> E read(Class<E> clz) throws IOException {
            ObjectMapper om = new ObjectMapper();
            try (InputStream is = new FileInputStream(file)) {
                return om.readValue(is, clz);
            }
        }

    }

}
