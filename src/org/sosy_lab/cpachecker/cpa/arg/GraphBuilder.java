/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.arg;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphType;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

enum GraphBuilder {

  ARG_PATH {

    @Override
    public String getId(ARGState pState) {
      return getId(pState, "");
    }

    private String getId(ARGState pState, String pIdentPostfix) {
      return String.format("A%d%s", pState.getStateId(), pIdentPostfix);
    }

    private String getId(ARGState pState, int pSubStateNo, int pSubStateCount) {
      return getId(pState, String.format("_%d_%d", pSubStateNo, pSubStateCount));
    }

    @Override
    public void buildGraph(ARGState pRootState,
        Predicate<? super ARGState> pPathStates,
        Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
        Map<ARGState, CFAEdgeWithAssumptions> pValueMap,
        GraphMlBuilder pDocument,
        Iterable<Pair<ARGState, Iterable<ARGState>>> pARGEdges,
        EdgeAppender pEdgeAppender) {
      int multiEdgeCount = 0;
      for (Pair<ARGState, Iterable<ARGState>> argEdges : pARGEdges) {
        ARGState s = argEdges.getFirst();
        String sourceStateNodeId = getId(s);

        // Process child states
        for (ARGState child : argEdges.getSecond()) {

          String childStateId = getId(child);
          CFAEdge edgeToNextState = s.getEdgeToChild(child);
          String prevStateId = sourceStateNodeId;

          if (edgeToNextState instanceof MultiEdge) {
            // The successor state might have several incoming MultiEdges.
            // In this case the state names like ARG<successor>_0 would occur
            // several times.
            // So we add this counter to the state names to make them unique.
            multiEdgeCount++;

            // Write out a long linear chain of pseudo-states (one state encodes multiple edges)
            // because the AutomatonCPA also iterates through the MultiEdge.
            List<CFAEdge> edges = ((MultiEdge)edgeToNextState).getEdges();

            // inner part (without last edge)
            for (int i = 0; i < edges.size()-1; i++) {
              CFAEdge innerEdge = edges.get(i);
              String pseudoStateId = getId(child, i, multiEdgeCount);

              assert (!(innerEdge instanceof AssumeEdge));

              Optional<Collection<ARGState>> absentStates = Optional.absent();
              pEdgeAppender.appendNewEdge(pDocument, prevStateId, pseudoStateId, innerEdge, absentStates, pValueMap);
              prevStateId = pseudoStateId;
            }

            // last edge connecting it with the real successor
            edgeToNextState = edges.get(edges.size()-1);
          }

          Optional<Collection<ARGState>> state =
              Optional.<Collection<ARGState>>of(Collections.singleton(s));

          // Only proceed with this state if the path states contain the child
          if (pPathStates.apply(child) && pIsRelevantEdge.apply(Pair.of(s, child))) {
            // Child belongs to the path!
            pEdgeAppender.appendNewEdge(
                pDocument, prevStateId, childStateId, edgeToNextState, state, pValueMap);
          } else {
            // Child does not belong to the path --> add a branch to the SINK node!
            pEdgeAppender.appendNewEdgeToSink(
                pDocument, prevStateId, edgeToNextState, state, pValueMap);
          }
        }
      }
    }

    @Override
    public GraphType getGraphType() {
      return GraphType.ERROR_WITNESS;
    }
  },
  PROOF {

    @Override
    public String getId(ARGState pState) {
      return Joiner.on(",").join(AbstractStates.extractLocations(pState));
    }

    @Override
    public void buildGraph(ARGState pRootState,
        final Predicate<? super ARGState> pPathStates,
        final Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
        Map<ARGState, CFAEdgeWithAssumptions> pValueMap,
        GraphMlBuilder pDocument,
        Iterable<Pair<ARGState, Iterable<ARGState>>> pARGEdges,
        EdgeAppender pEdgeAppender) {

      // normally there is only one node per state, thus we assume that there is only one root-node
      final CFANode rootNode = Iterables.getOnlyElement(AbstractStates.extractLocations(pRootState));

      // Get all successor nodes of edges
      final Set<CFANode> subProgramNodes = new HashSet<>();
      final Multimap<CFANode, ARGState> states = HashMultimap.create();

      subProgramNodes.add(rootNode);
      for (final Pair<ARGState, Iterable<ARGState>> edge : pARGEdges) {
        for (ARGState target : edge.getSecond()) {
          // where the successor ARG node is in the set of target path states AND the edge is relevant
          if (pPathStates.apply(target) && pIsRelevantEdge.apply(Pair.of(edge.getFirst(), target))) {
            for (CFANode location : AbstractStates.extractLocations(target)) {
              subProgramNodes.add(location);
              states.put(location, target);
            }
          }
        }
      }

      Queue<CFANode> waitlist = new ArrayDeque<>();
      Set<CFANode> visited = new HashSet<>();
      waitlist.offer(rootNode);
      visited.add(rootNode);
      while (!waitlist.isEmpty()) {
        CFANode current = waitlist.poll();
        for (CFAEdge leavingEdge : CFAUtils.leavingEdges(current)) {
          CFANode successor = leavingEdge.getSuccessor();
          if (subProgramNodes.contains(successor)) {
            appendEdge(
                pDocument,
                pEdgeAppender,
                leavingEdge,
                Optional.of(states.get(successor)),
                pValueMap);
            if (visited.add(successor)) {
              waitlist.offer(successor);
            }
          } else {
            String sourceId = current.toString();
            pEdgeAppender.appendNewEdgeToSink(
                pDocument,
                sourceId,
                leavingEdge,
                Optional.<Collection<ARGState>>absent(),
                pValueMap);
          }
        }
      }
    }

    private void appendEdge(
        GraphMlBuilder pDocument,
        EdgeAppender pEdgeAppender,
        CFAEdge pEdge,
        Optional<Collection<ARGState>> pStates,
        Map<ARGState, CFAEdgeWithAssumptions> pValueMap) {
      if (pEdge instanceof MultiEdge) {
        Iterator<CFAEdge> edgeIterator = ((MultiEdge) pEdge).iterator();
        while (edgeIterator.hasNext()) {
          CFAEdge edge = edgeIterator.next();
          appendEdge(
              pDocument,
              pEdgeAppender,
              edge,
              edgeIterator.hasNext() ? Optional.<Collection<ARGState>>absent() : pStates,
              pValueMap);
        }
      } else {
        String sourceId = pEdge.getPredecessor().toString();
        String targetId = pEdge.getSuccessor().toString();
        pEdgeAppender.appendNewEdge(pDocument, sourceId, targetId, pEdge, pStates, pValueMap);
      }
    }

    @Override
    public GraphType getGraphType() {
      return GraphType.PROOF_WITNESS;
    }

  };

  public abstract GraphType getGraphType();

  public abstract String getId(ARGState pState);

  public abstract void buildGraph(ARGState pRootState,
      Predicate<? super ARGState> pPathStates,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
      Map<ARGState, CFAEdgeWithAssumptions> pValueMap,
      GraphMlBuilder pDocument,
      Iterable<Pair<ARGState, Iterable<ARGState>>> pARGEdges,
      EdgeAppender pEdgeAppender);

}