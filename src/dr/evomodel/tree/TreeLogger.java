/*
 * TreeLogger.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
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

package dr.evomodel.tree;

import dr.app.tools.NexusExporter;
import dr.evolution.tree.*;
import dr.inference.loggers.LogFormatter;
import dr.inference.loggers.MCLogger;

import java.util.*;

/**
 * A logger that logs tree and clade frequencies.
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @version $Id: TreeLogger.java,v 1.25 2006/09/05 13:29:34 rambaut Exp $
 */
public class TreeLogger extends MCLogger {

    private Tree tree;
    private BranchRateController branchRateProvider = null;

    private TreeAttributeProvider[] treeAttributeProviders;
    private NodeAttributeProvider[] nodeAttributeProviders;
    private BranchAttributeProvider[] branchAttributeProviders;

    private boolean nexusFormat = false;
    public boolean usingRates = false;
    public boolean substitutions = false;
    private Map<String, Integer> idMap = new HashMap<String, Integer>();
    private List<String> taxaIds = new ArrayList<String>();
    private boolean mapNames = true;

    public TreeLogger(Tree tree, BranchRateController branchRateProvider,
                      TreeAttributeProvider[] treeAttributeProviders,
                      NodeAttributeProvider[] nodeAttributeProviders,
                      BranchAttributeProvider[] branchAttributeProviders,
                      LogFormatter formatter, int logEvery, boolean nexusFormat,
                      boolean sortTranslationTable, boolean mapNames) {

        super(formatter, logEvery, false);

        this.nexusFormat = nexusFormat;
        this.mapNames = mapNames;

        this.branchRateProvider = branchRateProvider;

        this.treeAttributeProviders = treeAttributeProviders;
        this.nodeAttributeProviders = nodeAttributeProviders;

        this.branchAttributeProviders = branchAttributeProviders;

        if (this.branchRateProvider != null) {
            this.substitutions = true;
        }
        this.tree = tree;

        for (int i = 0; i < tree.getTaxonCount(); i++) {
            taxaIds.add(tree.getTaxon(i).getId());
        }
        if (sortTranslationTable) {
            Collections.sort(taxaIds);
        }

        int k = 1;
        for (String taxaId : taxaIds) {
            idMap.put(taxaId, k);
            k += 1;
        }
    }

    public void startLogging() {

        if (nexusFormat) {
            int taxonCount = tree.getTaxonCount();
            logLine("#NEXUS");
            logLine("");
            logLine("Begin taxa;");
            logLine("\tDimensions ntax=" + taxonCount + ";");
            logLine("\tTaxlabels");

            for (String taxaId : taxaIds) {
                if (taxaId.matches(NexusExporter.SPECIAL_CHARACTERS_REGEX)) {
                    taxaId = "'" + taxaId + "'";
                }
                logLine("\t\t" + taxaId);
            }

            logLine("\t\t;");
            logLine("End;");
            logLine("");
            logLine("Begin trees;");

            // This is needed if the trees use numerical taxon labels
            logLine("\tTranslate");
            int k = 1;
            for (String taxaId : taxaIds) {
                if (taxaId.matches(NexusExporter.SPECIAL_CHARACTERS_REGEX)) {
                    taxaId = "'" + taxaId + "'";
                }
                if (k < taxonCount) {
                    logLine("\t\t" + k + " " + taxaId + ",");
                } else {
                    logLine("\t\t" + k + " " + taxaId);
                }
                k += 1;
            }
            logLine("\t\t;");
        }
    }

    public void log(int state) {

        if (logEvery <= 0 || ((state % logEvery) == 0)) {
            StringBuffer buffer = new StringBuffer("tree STATE_");
            buffer.append(state);
            if (treeAttributeProviders != null) {
                boolean hasAttribute = false;
                for (TreeAttributeProvider tap : treeAttributeProviders) {
                    if (!hasAttribute) {
                        buffer.append(" [&");
                        hasAttribute = true;
                    } else {
                        buffer.append(",");
                    }
                    buffer.append(tap.getTreeAttributeLabel());
                    buffer.append("=");
                    buffer.append(tap.getAttributeForTree(tree));

                }
                if (hasAttribute) {
                    buffer.append("]");
                }
            }

            buffer.append(" = [&R] ");

            if (substitutions) {
                Tree.Utils.newick(tree, tree.getRoot(), false, Tree.Utils.LENGTHS_AS_SUBSTITUTIONS,
                        branchRateProvider, nodeAttributeProviders, branchAttributeProviders, idMap, buffer);
            } else {
                Tree.Utils.newick(tree, tree.getRoot(), !mapNames, Tree.Utils.LENGTHS_AS_TIME,
                        null, nodeAttributeProviders, branchAttributeProviders, idMap, buffer);
            }

            buffer.append(";");
            logLine(buffer.toString());
        }
    }

    public void stopLogging() {

        logLine("End;");
        super.stopLogging();
    }


}