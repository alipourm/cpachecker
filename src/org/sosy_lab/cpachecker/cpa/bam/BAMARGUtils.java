/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

class BAMARGUtils {
  private BAMARGUtils() {}

  public static Multimap<Block, ReachedSet> gatherReachedSets(BAMCPA cpa, ReachedSet finalReachedSet) {
    Multimap<Block, ReachedSet> result = HashMultimap.create();
    gatherReachedSets(cpa, cpa.getBlockPartitioning().getMainBlock(), finalReachedSet, result);
    return result;
  }

  private static void gatherReachedSets(BAMCPA cpa, Block block, ReachedSet reachedSet, Multimap<Block, ReachedSet> blockToReachedSet) {
    if (blockToReachedSet.containsEntry(block, reachedSet)) {
      return; //avoid looping in recursive block calls
    }

    blockToReachedSet.put(block, reachedSet);

    ARGState firstElement = (ARGState)reachedSet.getFirstState();

    Deque<ARGState> worklist = new LinkedList<>();
    Set<ARGState> processed = new HashSet<>();

    worklist.add(firstElement);

    while (worklist.size() != 0) {
      ARGState currentElement = worklist.removeLast();

      assert reachedSet.contains(currentElement);

      if (processed.contains(currentElement)) {
        continue;
      }
      processed.add(currentElement);

      for (ARGState child : currentElement.getChildren()) {
        CFAEdge edge = currentElement.getEdgeToChild(child);
        if (edge == null) {
          //this is a summary edge
          Pair<Block, ReachedSet> pair = getCachedReachedSet(cpa, currentElement, reachedSet.getPrecision(currentElement));
          gatherReachedSets(cpa, pair.getFirst(), pair.getSecond(), blockToReachedSet);
        }
        if (!worklist.contains(child)) {
          if (reachedSet.contains(child)) {
            worklist.add(child);
          }
        }
      }
    }
  }

  private static Pair<Block, ReachedSet> getCachedReachedSet(BAMCPA cpa, ARGState root, Precision rootPrecision) {
    CFANode rootNode = extractLocation(root);
    Block rootSubtree = cpa.getBlockPartitioning().getBlockForCallNode(rootNode);

    ReachedSet reachSet = cpa.getData().initialStateToReachedSet.get(root);
    assert reachSet != null;
    return Pair.of(rootSubtree, reachSet);
  }

  public static ARGState copyARG(ARGState pRoot) {
    HashMap<ARGState, ARGState> stateToCopyElem = new HashMap<>();
    HashSet<ARGState> visited = new HashSet<>();
    Stack<ARGState> toVisit = new Stack<>();
    ARGState current, copyState, copyStateInner;

    visited.add(pRoot);
    toVisit.add(pRoot);

    while (!toVisit.isEmpty()) {
      current = toVisit.pop();

      if (stateToCopyElem.get(current) == null) {
        copyState = copyNode(current);
        stateToCopyElem.put(current, copyState);
      } else {
        copyState = stateToCopyElem.get(current);
      }

      for (ARGState c : current.getChildren()) {
        if (stateToCopyElem.get(c) == null) {
          copyStateInner = copyNode(c);
          stateToCopyElem.put(c, copyStateInner);
        } else {
          copyStateInner = stateToCopyElem.get(c);
        }
        copyStateInner.addParent(copyState);
        if (!visited.contains(c)) {
          visited.add(c);
          toVisit.add(c);
        }
      }

      if (current.isCovered()) {
        if (stateToCopyElem.get(current.getCoveringState()) == null) {
          copyStateInner = copyNode(current.getCoveringState());
          stateToCopyElem.put(current.getCoveringState(), copyStateInner);
        } else {
          copyStateInner = stateToCopyElem.get(current.getCoveringState());
        }
        if (!visited.contains(current.getCoveringState())) {
          visited.add(current.getCoveringState());
          toVisit.add(current.getCoveringState());
        }
        copyState.setCovered(copyStateInner);
      }
    }
    return stateToCopyElem.get(pRoot);
  }

  private static ARGState copyNode(ARGState toCopy) {
    ARGState copyState;
    if (toCopy instanceof BAMARGBlockStartState) {
      copyState = new BAMARGBlockStartState(toCopy.getWrappedState(), null);
      ((BAMARGBlockStartState) copyState).setAnalyzedBlock(((BAMARGBlockStartState) toCopy).getAnalyzedBlock());
    } else {
      copyState = new ARGState(toCopy.getWrappedState(), null);
    }
    return copyState;
  }
}
