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
package podrouting.tools.cli;

import picocli.CommandLine;
import podrouting.util.cli.DynamicSubcommand;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.ServiceLoader;

@CommandLine.Command(
    name = "pdpot-tools",
    mixinStandardHelpOptions = true,
    subcommands = {
        OrganizeMain.class,
        TopoDiffMain.class,
        TransformMain.class,
        SummarizeMain.class,
    },
    versionProvider = ToolsMain.VersionProvider.class
)
public class ToolsMain {
    public static void main(String... args) {
        CommandLine cmd = new CommandLine(new ToolsMain());
        addDynamicSubcommands(cmd);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    static void addDynamicSubcommands(CommandLine cmd) {
        ServiceLoader<DynamicSubcommand> loader = ServiceLoader.load(DynamicSubcommand.class);
        for (DynamicSubcommand command : loader) {
            CommandLine subCmd =  new CommandLine(command);
            String name = subCmd.getCommandName();
            cmd.addSubcommand(name, subCmd);
        }
    }

    static class VersionProvider implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() throws Exception {
            ArrayList<String> list = new ArrayList<>();
            URL url = getClass().getResource("/version.txt");
            if (url != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        list.add(line);
                    }
                }
            }
            else {
                list.add("Version information is missing");
            }
            return list.toArray(new String[0]);
        }
    }

}
