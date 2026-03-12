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

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {
	private static final Logger log = LoggerFactory.getLogger(LogUtils.class);

	public static final String DEFAULT_PATTERN = "%d{ISO8601} %-5p (%t) [%c{1}(%M:%L)] %m%n";

	public static void addFileAppender(File logFile, String pattern, Level logLevel) {
		makeParents(logFile);

		FileAppender fa = new FileAppender();
		fa.setName("FileLogger");
		fa.setFile(logFile.toString());
		fa.setLayout(new PatternLayout(pattern));
		fa.setThreshold(logLevel);
		fa.setAppend(true);
		fa.activateOptions();

		//add appender to any Logger (here is root)
		org.apache.log4j.Logger.getRootLogger().addAppender(fa);

	}

	public static void addFileAppender(File logFile, Level logLevel) {
		addFileAppender(logFile, DEFAULT_PATTERN , logLevel);
	}

	public static void addFileAppender(File logFile) {
		addFileAppender(logFile, DEFAULT_PATTERN, Level.INFO);
	}
	
		
	public static void makeParents(File file) {
		File parent = file.getParentFile();
		if (parent == null) {
			return;
		}
		if (parent.exists()) {
			if (!parent.isDirectory()) {
				log.error("Could not create directory {}", parent);
			}
			else {
				return;
			}
		}
		parent.mkdirs();
	}

	
}
