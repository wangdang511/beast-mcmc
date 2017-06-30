/*
 * TopologyTracer.java
 *
 * Copyright (c) 2002-2017 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.app.tools;

import dr.app.beast.BeastVersion;
import dr.app.util.Arguments;
import dr.app.util.Utils;
import dr.evolution.io.Importer;
import dr.evolution.io.NewickImporter;
import dr.evolution.io.NexusImporter;
import dr.evolution.io.TreeImporter;
import dr.evolution.tree.*;
import dr.evolution.tree.treemetrics.*;
import dr.util.Version;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Guy Baele
 * @author Andrew Rambaut
 */
public class TopologyTracer {

    private final static Version version = new BeastVersion();

    private static final String STATE = "state";


    public TopologyTracer(final int burninTrees,
                          final int burninStates,
                          final String metric,
                          final String treeFile,
                          final String treeFile2,
                          final String userProvidedTreeFile,
                          final String outputFile,
                          final ArrayList<Double> lambdaValues) {

        // output to stdout
        PrintStream progressStream = System.out;

        try {

            long startTime = System.currentTimeMillis();

            progressStream.println("Reading & processing trees ...");

            BufferedReader reader = new BufferedReader(new FileReader(treeFile));

            String line = reader.readLine();

            TreeImporter importer;
            if (line.toUpperCase().startsWith("#NEXUS")) {
                importer = new NexusImporter(reader);
            } else {
                importer = new NewickImporter(reader);
            }

            BufferedReader reader2 = null;
            TreeImporter importer2 = null;
            if (treeFile2 != null) {
                reader2 = new BufferedReader(new FileReader(treeFile2));
                String line2 = reader2.readLine();

                if (line2.toUpperCase().startsWith("#NEXUS")) {
                    importer2 = new NexusImporter(reader2);
                } else {
                    importer2 = new NewickImporter(reader2);
                }
            }

            Tree focalTree = null;
            if (!userProvidedTreeFile.equals("")) {
                progressStream.println("User-provided focal tree.");
                //get tree from user provided tree file
                BufferedReader focalReader = new BufferedReader(new FileReader(userProvidedTreeFile));
                TreeImporter userImporter;
                String userLine = focalReader.readLine();
                if (userLine.toUpperCase().startsWith("#NEXUS")) {
                    userImporter = new NexusImporter(focalReader);
                } else {
                    userImporter = new NewickImporter(focalReader);
                }
                focalTree = userImporter.importNextTree();
                focalReader.close();
            }

            List<TreeMetric> treeMetrics = new ArrayList<TreeMetric>();
            if (metric.equals("all") || metric.equals("rf")) {
                treeMetrics.add(new RobinsonFouldsMetric());
            }
            if (metric.equals("all") || metric.equals("clade")) {
                treeMetrics.add(new CladeHeightMetric());
            }
            if (metric.equals("all") || metric.equals("branch")) {
                treeMetrics.add(new RootedBranchScoreMetric());
            }
            if (metric.equals("all") || metric.equals("sp")) {
                treeMetrics.add(new SteelPennyPathDifferenceMetric());
            }
            if (metric.equals("all") || metric.equals("kc")) {
                for (double lambda : lambdaValues) {
                    treeMetrics.add(new KendallColijnPathDifferenceMetric(lambda));
                }
            }

            if (treeMetrics.size() == 0) {
                throw new IllegalArgumentException("Unknown metric name");
            }


            List<Long> treeStates = new ArrayList<Long>();
            List<String> treeIds = new ArrayList<String>();
            List<List<Double>> metricValues = new ArrayList<List<Double>>();
            for (TreeMetric treeMetric : treeMetrics) {
                metricValues.add(new ArrayList<Double>());
            }

//            if (!userProvidedTree) {
//                //take into account first distance of focal tree to itself
//                treeIds.add(focalTree.getId());
//                treeStates.add((long) 0);
//
//                int i = 0;
//                for (TreeMetric treeMetric : treeMetrics) {
//                    metricValues.get(i).add(treeMetric.getMetric(focalTree, focalTree));
//                    i++;
//                }
//
//            }

            int numberOfTrees = 1;

            while (importer.hasTree()) {

                //no need to keep trees in memory
                Tree tree = importer.importNextTree();
                long state = Long.parseLong(tree.getId().split("_")[1]);

                Tree tree2 = null;
                if (importer2 != null) {
                    tree2 = importer2.importNextTree();
                    long state2 = Long.parseLong(tree2.getId().split("_")[1]);

                    if (state != state2) {
                        throw new RuntimeException("State numbers in paired tree files are not in synchrony");
                    }
                }

                // one or other of burninTrees and burninStates should be 0
                if (numberOfTrees >= burninTrees && state >= burninStates) {
                    if (tree2 == null) {
                        if (focalTree == null) {
                            // if we haven't set a user focal tree then use the first tree
                            focalTree = tree;
                        }
                    } else {
                        focalTree = tree2;
                    }

                    treeIds.add(tree.getId());
                    treeStates.add(state);

                    int i = 0;
                    for (TreeMetric treeMetric : treeMetrics) {
                        metricValues.get(i).add(treeMetric.getMetric(focalTree, tree));
                        i++;
                    }
                }

                numberOfTrees++;

                if (numberOfTrees % 25 == 0) {
                    progressStream.println(numberOfTrees + " trees parsed ...");
                    progressStream.flush();
                }

            }

            progressStream.println("\nWriting log file ...");

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            BeastVersion version = new BeastVersion();
            writer.write("# BEAST " + version.getVersionString() + "\n");
            writer.write(STATE);
            for (TreeMetric treeMetric : treeMetrics) {
                writer.write("\t" + treeMetric.getType().getShortName());
            }
            writer.write("\n");

            for (int i = 0; i < treeStates.size(); i++) {
                writer.write(Long.toString(treeStates.get(i)));
                for (List<Double> values : metricValues) {
                    writer.write("\t" + values.get(i));
                }
                writer.write("\n");
            }

            progressStream.println("Done.");

            long endTime = System.currentTimeMillis();

            progressStream.println("\nAnalyzed " + treeStates.size() + " trees, took " + (endTime-startTime)/1000.0 + " seconds.\n");

            progressStream.flush();
            progressStream.close();

            writer.flush();
            writer.close();

        } catch (FileNotFoundException fnf) {
            System.err.println(fnf.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        } catch (Importer.ImportException ime) {
            System.err.println(ime.getMessage());
        }

    }

    public static void printUsage(Arguments arguments) {
        arguments.printUsage("TopologyTracer", "<input-file-name> <output-file-name>");
        System.out.println();
        System.out.println("  Example: treeloganalyser test.trees ess-values.log");
        System.out.println();
    }

    public static void main(String[] args) {

        // There is a major issue with languages that use the comma as a decimal separator.
        // To ensure compatibility between programs in the package, enforce the US locale.
        Locale.setDefault(Locale.US);

        Arguments arguments = new Arguments(
                new Arguments.Option[]{
                        new Arguments.IntegerOption("burnin", "the number of states to be considered as 'burn-in' [default = none]"),
                        new Arguments.IntegerOption("burninTrees", "the number of trees to be considered as 'burn-in'"),
                        new Arguments.Option("paired", "take 2 input tree files and compute metric between tree pairs"),
                        new Arguments.StringOption("tree", "tree file name", "a focal tree provided by the user [default = first tree in .trees file]"),
                        new Arguments.StringOption("metric", new String[] {"kc", "sp", "rf", "clade", "branch", "all"}, false,
                                "which tree metric to use ('kc', 'sp', 'rf', 'clade', 'branch') [default = all]"
                        ),
                        new Arguments.RealOption("lambda", "the lambda value to be used for the 'Kendall-Colijn metric' [default = {0,0.5,1}]"),
                        new Arguments.Option("help", "option to print this message")
                });

        try {
            arguments.parseArguments(args);
        } catch (Arguments.ArgumentException ae) {
            System.out.println(ae.getMessage());
            printUsage(arguments);
            System.exit(1);
        }

        int burninStates = -1;
        int burninTrees = -1;
        if (arguments.hasOption("burnin")) {
            burninStates = arguments.getIntegerOption("burnin");
        }
        if (arguments.hasOption("burninTrees")) {
            burninTrees = arguments.getIntegerOption("burninTrees");
        }

        boolean paired = arguments.hasOption("paired");

        String metric = "all";
        if (arguments.hasOption("metric")) {
            metric = arguments.getStringOption("metric");
        }

        ArrayList<Double> lambdaValues = new ArrayList<Double>();
        lambdaValues.add(0.0);
        lambdaValues.add(0.5);
        lambdaValues.add(1.0);

        if (arguments.hasOption("lambda")) {
            lambdaValues.add(arguments.getRealOption("lambda"));
        }

        String providedFileName = "";
        if (arguments.hasOption("tree")) {
            providedFileName = arguments.getStringOption("tree");
        }

        String inputFileName = null;
        String inputFileName2 = null;
        String outputFileName = null;

        String[] args2 = arguments.getLeftoverArguments();

        if (args2.length > (paired ? 3 : 2)) {
            System.err.println("Unknown option: " + args2[(paired ? 3 : 2)]);
            System.err.println();
            printUsage(arguments);
            System.exit(1);
        }

        if (paired) {
            inputFileName = args2[0];
            inputFileName2 = args2[1];
            outputFileName = args2[2];
        } else {
            inputFileName = args2[0];
            outputFileName = args2[1];
        }

        if (inputFileName == null) {
            // No input file name was given so throw up a dialog box...
            inputFileName = Utils.getLoadFileName("TopologyTracer " + version.getVersionString() + " - Select log file to analyse");
        }

        new TopologyTracer(burninStates, burninTrees, metric, inputFileName, inputFileName2, providedFileName, outputFileName, lambdaValues);

        System.exit(0);


    }

}
