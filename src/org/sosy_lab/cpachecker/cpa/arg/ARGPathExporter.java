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
package org.sosy_lab.cpachecker.cpa.arg;

import static org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.SINK_NODE_ID;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpressionCollectingVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.counterexample.AssumptionToEdgeAllocator;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CFAPathWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.RichModel;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisConcreteErrorPathAllocator;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.SourceLocationMapper;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.AssumeCase;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeType;
import org.sosy_lab.cpachecker.util.expressions.And;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTree;
import org.sosy_lab.cpachecker.util.expressions.LeafExpression;
import org.sosy_lab.cpachecker.util.expressions.Or;
import org.sosy_lab.cpachecker.util.expressions.ToCodeVisitor;
import org.w3c.dom.Element;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedMapDifference;
import com.google.common.collect.TreeMultimap;

@Options(prefix="cpa.arg.witness")
public class ARGPathExporter {

  private static final Function<ARGState, ARGState> COVERED_TO_COVERING = new Function<ARGState, ARGState>() {

    @Override
    public ARGState apply(ARGState pChild) {
      ARGState child = pChild;
      // The child might be covered by another state
      // --> switch to the covering state
      if (child.isCovered()) {
        child = child.getCoveringState();
        assert !child.isCovered();
      }
      return child;
    }

  };

  @Option(secure=true, description="Verification witness: Include function calls and function returns?")
  boolean exportFunctionCallsAndReturns = true;

  @Option(secure=true, description="Verification witness: Include assumptions (C statements)?")
  boolean exportAssumptions = true;

  @Option(secure=true, description="Verification witness: Include the considered case of an assume?")
  boolean exportAssumeCaseInfo = true;

  @Option(secure=true, description="Verification witness: Include the (starting) line numbers of the operations on the transitions?")
  boolean exportLineNumbers = true;

  @Option(secure=true, description="Verification witness: Include the sourcecode of the operations?")
  boolean exportSourcecode = true;

  @Option(secure=true, description="Verification witness: Include the offset within the file?")
  boolean exportOffset = true;

  private final LogManager logger;

  private final MachineModel machineModel;

  private final Language language;

  private final AssumptionToEdgeAllocator assumptionToEdgeAllocator;

  /**
   * This is a temporary hack to easily obtain specification and verification tasks.
   * TODO: Move the witness export out of the ARG CPA after the new error report has been integrated
   * and obtain the values without this hack.
   */
  @Options
  private static class HackyOptions {

    @Option(secure=true, name="analysis.programNames",
        description="A String, denoting the programs to be analyzed")
    private String programs;

    @Option(secure=true, name="properties",
        description="List of property files (INTERNAL USAGE ONLY - DO NOT USE)")
    @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
    private List<Path> propertyFiles = ImmutableList.of();

    @Option(secure=true,
        name="cpa.predicate.handlePointerAliasing",
        description = "Handle aliasing of pointers. "
        + "This adds disjunctions to the formulas, so be careful when using cartesian abstraction.")
    private boolean handlePointerAliasing = true;
  }

  private final HackyOptions hackyOptions = new HackyOptions();

  public ARGPathExporter(
      final Configuration pConfig,
      final LogManager pLogger,
      MachineModel pMachineModel,
      Language pLanguage)
      throws InvalidConfigurationException {
    Preconditions.checkNotNull(pConfig);
    pConfig.inject(this);
    pConfig.inject(hackyOptions);
    this.machineModel = pMachineModel;
    this.language = pLanguage;
    this.logger = pLogger;
    this.assumptionToEdgeAllocator = new AssumptionToEdgeAllocator(pConfig, pLogger, pMachineModel);
  }

  public void writeErrorWitness(Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
      CounterexampleInfo pCounterExample)
      throws IOException {

    String defaultFileName = getInitialFileName(pRootState);
    WitnessWriter writer = new WitnessWriter(defaultFileName);
    writer.writePath(pTarget, pRootState, pIsRelevantState, pIsRelevantEdge, Optional.of(pCounterExample), GraphBuilder.ARG_PATH);
  }

  public void writeProofWitness(Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge)
      throws IOException {

    String defaultFileName = getInitialFileName(pRootState);
    WitnessWriter writer = new WitnessWriter(defaultFileName);
    writer.writePath(
        pTarget,
        pRootState,
        pIsRelevantState,
        pIsRelevantEdge,
        Optional.<CounterexampleInfo>absent(),
        GraphBuilder.PROOF);
  }

  private String getInitialFileName(ARGState pRootState) {
    Deque<CFANode> worklist = Queues.newArrayDeque(AbstractStates.extractLocations(pRootState));

    while (!worklist.isEmpty()) {
      CFANode l = worklist.pop();
      for (CFAEdge e: CFAUtils.leavingEdges(l)) {
        Set<FileLocation> fileLocations = SourceLocationMapper.getFileLocationsFromCfaEdge(e);
        if (fileLocations.size() > 0) {
          String fileName = fileLocations.iterator().next().getFileName();
          if (fileName != null) {
            return fileName;
          }
        }
        worklist.push(e.getSuccessor());
      }
    }

    throw new RuntimeException("Could not determine file name based on abstract state!");
  }

  private class WitnessWriter implements EdgeAppender {

    private final Multimap<String, NodeFlag> nodeFlags = TreeMultimap.create();
    private final Multimap<String, Property> violatedProperties = HashMultimap.create();
    private final Map<DelayedAssignmentsKey, CFAEdgeWithAssumptions> delayedAssignments = Maps.newHashMap();

    private final Multimap<String, Edge> leavingEdges = TreeMultimap.create();
    private final Multimap<String, Edge> enteringEdges = TreeMultimap.create();

    private final String defaultSourcefileName;
    private boolean isFunctionScope = false;

    public WitnessWriter(@Nullable String pDefaultSourcefileName) {
      this.defaultSourcefileName = pDefaultSourcefileName;
    }

    @Override
    public void appendNewEdge(
        final GraphMlBuilder pDoc,
        String pFrom,
        final String pTo,
        final CFAEdge pEdge,
        final Optional<Collection<ARGState>> pFromState,
        final Map<ARGState, CFAEdgeWithAssumptions> pValueMap) {

      attemptSwitchToFunctionScope(pEdge);

      TransitionCondition desc = constructTransitionCondition(pFrom, pTo, pEdge, pFromState, pValueMap);

      Edge edge = new Edge(pFrom, pTo, desc);

      putEdge(edge);
    }

    @Override
    public void appendNewEdgeToSink(
        GraphMlBuilder pDoc,
        String pFrom,
        CFAEdge pEdge,
        Optional<Collection<ARGState>> pFromState,
        Map<ARGState, CFAEdgeWithAssumptions> pValueMap) {
      appendNewEdge(pDoc, pFrom, SINK_NODE_ID, pEdge, pFromState, pValueMap);
    }

    private void attemptSwitchToFunctionScope(CFAEdge pEdge) {
      if (isFunctionScope) {
        return;
      }
      if (!(pEdge instanceof BlankEdge)) {
        return;
      }
      BlankEdge edge = (BlankEdge) pEdge;
      if (!edge.getDescription().equals("Function start dummy edge")) {
        return;
      }
      isFunctionScope = true;
    }

    private TransitionCondition constructTransitionCondition(
        final String pFrom,
        final String pTo,
        final CFAEdge pEdge,
        final Optional<Collection<ARGState>> pFromState,
        final Map<ARGState, CFAEdgeWithAssumptions> pValueMap) {

      final TransitionCondition result = new TransitionCondition();
      if (AutomatonGraphmlCommon.handleAsEpsilonEdge(pEdge)) {
        return result;
      }

      if (exportFunctionCallsAndReturns) {
        if (pEdge.getSuccessor() instanceof FunctionEntryNode) {
          FunctionEntryNode in = (FunctionEntryNode) pEdge.getSuccessor();
          result.put(KeyDef.FUNCTIONENTRY, in.getFunctionName());

        }
        if (pEdge.getSuccessor() instanceof FunctionExitNode) {
          FunctionExitNode out = (FunctionExitNode) pEdge.getSuccessor();
          result.put(KeyDef.FUNCTIONEXIT, out.getFunctionName());
        }
      }

      String functionName = pEdge.getPredecessor().getFunctionName();
      if (pFromState.isPresent()) {

        List<ExpressionTree> code = new ArrayList<>();
        boolean isFunctionScope = this.isFunctionScope;

        Collection<ARGState> states = pFromState.get();
        for (ARGState state : states) {

          DelayedAssignmentsKey key = new DelayedAssignmentsKey(pFrom, pEdge, state);
          CFAEdgeWithAssumptions cfaEdgeWithAssignments = delayedAssignments.get(key);

          final CFAEdgeWithAssumptions currentEdgeWithAssignments;
          if (pValueMap != null && (currentEdgeWithAssignments = pValueMap.get(state)) != null) {
            if (cfaEdgeWithAssignments == null) {
              cfaEdgeWithAssignments = currentEdgeWithAssignments;

            } else {
              Builder<AExpressionStatement> allAssignments = ImmutableList.builder();
              allAssignments.addAll(cfaEdgeWithAssignments.getExpStmts());
              allAssignments.addAll(currentEdgeWithAssignments.getExpStmts());
              cfaEdgeWithAssignments =
                  new CFAEdgeWithAssumptions(
                      pEdge, allAssignments.build(), currentEdgeWithAssignments.getComment());
            }
          }

          if (cfaEdgeWithAssignments != null) {

            List<AExpressionStatement> assignments = cfaEdgeWithAssignments.getExpStmts();
            Predicate<AExpressionStatement> assignsParameterOfOtherFunction =
                new AssignsParameterOfOtherFunction(pEdge);
            List<AExpressionStatement> functionValidAssignments =
                FluentIterable.from(assignments).filter(assignsParameterOfOtherFunction).toList();

            if (functionValidAssignments.size() < assignments.size()) {
              cfaEdgeWithAssignments =
                  new CFAEdgeWithAssumptions(
                      pEdge, functionValidAssignments, cfaEdgeWithAssignments.getComment());
              FluentIterable<CFAEdge> nextEdges = CFAUtils.leavingEdges(pEdge.getSuccessor());

              if (nextEdges.size() == 1 && state.getChildren().size() == 1) {
                String keyFrom = pTo;
                CFAEdge keyEdge = Iterables.getOnlyElement(nextEdges);
                ARGState keyState = Iterables.getOnlyElement(state.getChildren());
                List<AExpressionStatement> valueAssignments =
                    FluentIterable.from(assignments)
                        .filter(Predicates.not(assignsParameterOfOtherFunction))
                        .toList();
                CFAEdgeWithAssumptions valueCFAEdgeWithAssignments =
                    new CFAEdgeWithAssumptions(keyEdge, valueAssignments, "");
                delayedAssignments.put(
                    new DelayedAssignmentsKey(keyFrom, keyEdge, keyState),
                    valueCFAEdgeWithAssignments);
              }
            }

            // Do not export our own temporary variables
            assignments =
                FluentIterable.from(cfaEdgeWithAssignments.getExpStmts())
                    .filter(
                        new Predicate<AExpressionStatement>() {

                          @Override
                          public boolean apply(AExpressionStatement statement) {
                            if (statement.getExpression() instanceof CExpression) {
                              CExpression expression = (CExpression) statement.getExpression();
                              for (CIdExpression idExpression :
                                  expression.accept(new CIdExpressionCollectingVisitor())) {
                                if (idExpression
                                    .getDeclaration()
                                    .getQualifiedName()
                                    .toUpperCase()
                                    .contains("__CPACHECKER_TMP")) {
                                  return false;
                                }
                              }
                              return true;
                            }
                            return false;
                          }
                        })
                    .toList();

            // Determine the scope for static local variables
            for (AExpressionStatement functionValidAssignment : functionValidAssignments) {
              if (functionValidAssignment instanceof CExpressionStatement) {
                CExpression expression = (CExpression) functionValidAssignment.getExpression();
                for (CIdExpression idExpression :
                    expression.accept(new CIdExpressionCollectingVisitor())) {
                  CSimpleDeclaration declaration = idExpression.getDeclaration();
                  if (declaration.getName().contains("static")
                      && !declaration.getOrigName().contains("static")
                      && declaration.getQualifiedName().contains("::")) {
                    isFunctionScope = true;
                    functionName =
                        declaration
                            .getQualifiedName()
                            .substring(0, declaration.getQualifiedName().indexOf("::"));
                  }
                }
              }
            }

            if (!assignments.isEmpty()) {
              code.add(
                  And.of(
                      FluentIterable.from(assignments)
                          .transform(LeafExpression.FROM_STATEMENT)
                          .toList()));
            }
          }
        }

        if (exportAssumptions && !code.isEmpty()) {
          result.put(KeyDef.ASSUMPTION, Or.of(code).accept(ToCodeVisitor.INSTANCE));
          if (isFunctionScope) {
            result.put(KeyDef.ASSUMPTIONSCOPE, functionName);
          }
        }
      }

      if (pFromState.isPresent()) {
        // TODO interface for extracting the information from states, similar to FormulaReportingState
        Set<ExpressionTree> stateInvariants = new HashSet<>();
        for (ARGState state : pFromState.get()) {
          ValueAnalysisState valueAnalysisState =
              AbstractStates.extractStateByType(state, ValueAnalysisState.class);
          if (valueAnalysisState != null) {
            Set<ExpressionTree> stateInvariantParts = new HashSet<>();
            ConcreteState concreteState = ValueAnalysisConcreteErrorPathAllocator.createConcreteState(valueAnalysisState);
            for (AExpressionStatement expressionStatement : assumptionToEdgeAllocator.allocateAssumptionsToEdge(pEdge, concreteState).getExpStmts()) {
              stateInvariantParts.add(LeafExpression.of(expressionStatement.getExpression()));
            }
            if (!stateInvariantParts.isEmpty()) {
              stateInvariants.add(And.of(stateInvariantParts));
            }
          }
        }
        if (!stateInvariants.isEmpty()) {
          result.put(KeyDef.INVARIANT, Or.of(stateInvariants).accept(ToCodeVisitor.INSTANCE));
          if (isFunctionScope) {
            result.put(KeyDef.INVARIANTSCOPE, functionName);
          }
        }
      }

      if (exportAssumeCaseInfo) {
        if (pEdge instanceof AssumeEdge) {
          AssumeEdge a = (AssumeEdge) pEdge;
          // If the assume edge or its sibling edge is followed by a pointer call,
          // the assumption is artificial and should not be exported
          if (CFAUtils.leavingEdges(a.getPredecessor()).anyMatch(new Predicate<CFAEdge>() {

            @Override
            public boolean apply(CFAEdge pArg0) {
              return CFAUtils.leavingEdges(pArg0.getSuccessor()).anyMatch(new Predicate<CFAEdge>() {

                @Override
                public boolean apply(CFAEdge pArg0) {
                  return pArg0.getRawStatement().startsWith("pointer call");
                }

              });
            }

          })) {
            result.keyValues.clear();
            return result;
          }
          AssumeCase assumeCase = a.getTruthAssumption() ? AssumeCase.THEN : AssumeCase.ELSE;
          result.put(KeyDef.CONTROLCASE, assumeCase.toString());
        }
      }

      if (exportLineNumbers) {
        Set<FileLocation> locations = SourceLocationMapper.getFileLocationsFromCfaEdge(pEdge);
        if (locations.size() > 0) {
          FileLocation l = locations.iterator().next();
          if (!l.getFileName().equals(defaultSourcefileName)) {
            result.put(KeyDef.ORIGINFILE, l.getFileName());
          }
          result.put(KeyDef.ORIGINLINE, Integer.toString(l.getStartingLineInOrigin()));
        }
      }

      if (exportOffset) {
        Set<FileLocation> locations = SourceLocationMapper.getFileLocationsFromCfaEdge(pEdge);
        if (locations.size() > 0) {
          FileLocation l = locations.iterator().next();
          if (!l.getFileName().equals(defaultSourcefileName)) {
            result.put(KeyDef.ORIGINFILE, l.getFileName());
          }
          result.put(KeyDef.OFFSET, Integer.toString(l.getNodeOffset()));
        }
      }

      if (exportSourcecode && !pEdge.getRawStatement().trim().isEmpty()) {
        result.put(KeyDef.SOURCECODE, pEdge.getRawStatement());
      }

      return result;
    }

    private void appendKeyDefinitions(GraphMlBuilder pDoc, GraphType pGraphType) {
      EnumSet<KeyDef> keyDefs = EnumSet.allOf(KeyDef.class);
      pDoc.appendNewKeyDef(KeyDef.NODETYPE, AutomatonGraphmlCommon.defaultNodeType.text);
      keyDefs.remove(KeyDef.NODETYPE);
      pDoc.appendNewKeyDef(KeyDef.ORIGINFILE, defaultSourcefileName);
      keyDefs.remove(KeyDef.ORIGINFILE);
      for (NodeFlag f : NodeFlag.values()) {
        keyDefs.remove(f.key);
        pDoc.appendNewKeyDef(f.key, "false");
      }
      for (KeyDef keyDef : keyDefs) {
        pDoc.appendNewKeyDef(keyDef, null);
      }
    }

    /**
     * Starting from the given initial ARG state, collects that state and all
     * transitive successors (as defined by the successor function) that are
     * children of their direct predecessor and are accepted by the path state
     * predicate.
     *
     * @param pInitialState the initial ARG state.
     * @param pSuccessorFunction the function defining the successors of a
     * state.
     * @param pPathStates a filter on the nodes.
     *
     * @return the parents with their children.
     */
    private Iterable<ARGState> collectPathNodes(
        final ARGState pInitialState,
        final Function<? super ARGState, ? extends Iterable<ARGState>> pSuccessorFunction,
        final Predicate<? super ARGState> pPathStates) {
      return FluentIterable
          .from(collectPathEdges(pInitialState, pSuccessorFunction, pPathStates))
          .transform(Pair.<ARGState>getProjectionToFirst());
    }

    /**
     * Starting from the given initial ARG state, collects that state and all
     * transitive successors (as defined by the successor function) that are
     * children of their direct predecessor. Children are only computed for
     * nodes that are accepted by the path state predicate.
     *
     * @param pInitialState the initial ARG state.
     * @param pSuccessorFunction the function defining the successors of a
     * state.
     * @param pPathStates a filter on the parent nodes.
     *
     * @return the parents with their children.
     */
    private Iterable<Pair<ARGState, Iterable<ARGState>>> collectPathEdges(
        final ARGState pInitialState,
        final Function<? super ARGState, ? extends Iterable<ARGState>> pSuccessorFunction,
        final Predicate<? super ARGState> pPathStates) {
      return new Iterable<Pair<ARGState, Iterable<ARGState>>>() {

        private final Set<ARGState> visited = new HashSet<>();

        private final Deque<ARGState> waitlist = new ArrayDeque<>();

        {
          waitlist.add(pInitialState);
          visited.add(pInitialState);
        }

        @Override
        public Iterator<Pair<ARGState, Iterable<ARGState>>> iterator() {
          return new Iterator<Pair<ARGState, Iterable<ARGState>>>() {

            @Override
            public boolean hasNext() {
              return !waitlist.isEmpty();
            }

            @Override
            public Pair<ARGState, Iterable<ARGState>> next() {
              if (!hasNext()) {
                throw new NoSuchElementException();
              }
              assert !waitlist.isEmpty();
              final ARGState parent = waitlist.poll();

              Predicate<ARGState> childFilter = new Predicate<ARGState>() {

                @Override
                public boolean apply(ARGState pChild) {
                  return parent.getChildren().contains(pChild);
                }

              };

              // Get all children
              FluentIterable<ARGState> children = FluentIterable
                  .from(pSuccessorFunction.apply(parent))
                  .transform(COVERED_TO_COVERING)
                  .filter(childFilter);

              // Only the children on the path become parents themselves
              for (ARGState child : children.filter(pPathStates)) {
                if (visited.add(child)) {
                  waitlist.offer(child);
                }
              }

              return Pair.<ARGState, Iterable<ARGState>>of(parent, children);
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException("Removal not supported.");
            }

          };
        }
      };
    }

    public void writePath(Appendable pTarget,
        final ARGState pRootState,
        final Predicate<? super ARGState> pIsRelevantState,
        final Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
        Optional<CounterexampleInfo> pCounterExample,
        GraphBuilder pGraphBuilder)
        throws IOException {

      final Function<? super ARGState, ? extends Iterable<ARGState>> successorFunction = ARGUtils.CHILDREN_OF_STATE;

      Map<ARGState, CFAEdgeWithAssumptions> valueMap = null;
      if (pCounterExample.isPresent()) {
        RichModel model = pCounterExample.get().getTargetPathModel();
        CFAPathWithAssumptions cfaPath = model.getCFAPathWithAssignments();
        if (cfaPath != null) {
          ARGPath targetPath = pCounterExample.get().getTargetPath();
          valueMap = model.getExactVariableValues(targetPath);
        }
      }

      GraphType graphType = pGraphBuilder.getGraphType();

      GraphMlBuilder doc;
      try {
        doc = new GraphMlBuilder(pTarget);
      } catch (ParserConfigurationException e) {
        throw new IOException(e);
      }

      // TODO: Full schema details
      // Version of format..
      // TODO! (we could use the version of a XML schema)

      // ...
      String entryStateNodeId = pGraphBuilder.getId(pRootState);

      doc.appendDocHeader();
      appendKeyDefinitions(doc, graphType);
      doc.appendGraphHeader(
          graphType,
          language,
          FluentIterable.from(hackyOptions.propertyFiles).transform(new Function<Path, String>() {

            @Override
            public String apply(Path pArg0) {
              try {
                return pArg0.asCharSource(Charsets.UTF_8).read().trim();
              } catch (IOException e) {
                logger.logUserException(Level.WARNING, e, "Could not export specification to witness.");
                return "Unknown specification";
              }
            }

          }),
          hackyOptions.programs,
          hackyOptions.handlePointerAliasing ? "precise" : "simple",
          machineModel);

      // Collect node flags in advance
      for (ARGState s : collectPathNodes(pRootState, successorFunction, pIsRelevantState)) {
        String sourceStateNodeId = pGraphBuilder.getId(s);
        EnumSet<NodeFlag> sourceNodeFlags = EnumSet.noneOf(NodeFlag.class);
        if (sourceStateNodeId.equals(entryStateNodeId)) {
          sourceNodeFlags = EnumSet.of(NodeFlag.ISENTRY);
        }
        sourceNodeFlags.addAll(extractNodeFlags(s));
        nodeFlags.putAll(sourceStateNodeId, sourceNodeFlags);
        violatedProperties.putAll(sourceStateNodeId, extractViolatedProperties(s));
      }
      // Write the sink node
      nodeFlags.put(SINK_NODE_ID, NodeFlag.ISSINKNODE);

      // Build the actual graph
      pGraphBuilder.buildGraph(pRootState, pIsRelevantState, pIsRelevantEdge, valueMap, doc, collectPathEdges(pRootState, successorFunction, pIsRelevantState), this);

      // Remove edges that lead to the sink but have a sibling edge that has the same label
      Collection<Edge> toRemove = FluentIterable.from(leavingEdges.values()).filter(new Predicate<Edge>() {

        @Override
        public boolean apply(final Edge pEdge) {
          return pEdge.target.equals(SINK_NODE_ID)
              && FluentIterable.from(leavingEdges.get(pEdge.source)).filter(Predicates.not(Predicates.equalTo(pEdge))).anyMatch(new Predicate<Edge>() {

                @Override
                public boolean apply(Edge pArg0) {
                  return pArg0.label.equals(pEdge.label);
                }});
        }

      }).toList();
      for (Edge edge : toRemove) {
        enteringEdges.remove(edge.target, edge);
        leavingEdges.remove(edge.source, edge);
      }

      // Merge nodes with empty or repeated edges
      Supplier<Iterator<Edge>> redundantEdgeIteratorSupplier = new Supplier<Iterator<Edge>>() {

        @Override
        public Iterator<Edge> get() {
          return FluentIterable
              .from(leavingEdges.values())
              .filter(new Predicate<Edge>() {

                @Override
                public boolean apply(final Edge pEdge) {
                  // An edge is redundant if it is the only leaving edge of a
                  // node and it is empty or all its non-assumption contents
                  // are summarized by a preceding edge
                  if ((!pEdge.label.hasTransitionRestrictions()
                      || FluentIterable.from(enteringEdges.get(pEdge.source)).anyMatch(new Predicate<Edge>() {

                        @Override
                        public boolean apply(Edge pPrecedingEdge) {
                          return pPrecedingEdge.label.summarizes(pEdge.label);
                        }

                      })
                      || pEdge.label.keyValues.size() == 1 && pEdge.label.keyValues.containsKey(KeyDef.FUNCTIONEXIT))
                      && leavingEdges.get(pEdge.source).size() == 1) {
                    return true;
                  }
                  return false;
                }

              }).iterator();
        }

      };
      Iterator<Edge> redundantEdgeIterator = redundantEdgeIteratorSupplier.get();
      while (redundantEdgeIterator.hasNext()) {
        Edge edge = redundantEdgeIterator.next();
        mergeNodes(edge);
        redundantEdgeIterator = redundantEdgeIteratorSupplier.get();
        assert leavingEdges.isEmpty() || leavingEdges.containsKey(entryStateNodeId);
      }

      // Write elements
      {
        Set<String> visited = Sets.newHashSet();
        Deque<String> waitlist = Queues.newArrayDeque();
        waitlist.push(entryStateNodeId);
        visited.add(entryStateNodeId);
        appendNewNode(doc, entryStateNodeId);
        while (!waitlist.isEmpty()) {
          String source = waitlist.pop();
          for (Edge edge : leavingEdges.get(source)) {
            if (visited.add(edge.target)) {
              appendNewNode(doc, edge.target);
              waitlist.push(edge.target);
            }
            newEdge(doc, edge);
          }
        }
      }

      doc.appendFooter();
    }

    private void mergeNodes(final Edge pEdge) {
      final String source = pEdge.source;
      final String target = pEdge.target;
      final TransitionCondition label = pEdge.label;
      Preconditions.checkArgument((!label.hasTransitionRestrictions()
          || FluentIterable.from(enteringEdges.get(pEdge.source)).anyMatch(new Predicate<Edge>() {

        @Override
        public boolean apply(Edge pPrecedingEdge) {
          return pPrecedingEdge.label.summarizes(pEdge.label);
        }

      })
        || pEdge.label.keyValues.size() == 1 && pEdge.label.keyValues.containsKey(KeyDef.FUNCTIONEXIT))
        && leavingEdges.get(pEdge.source).size() == 1);
      Preconditions.checkArgument(removeEdge(pEdge));

      // Merge the flags
      nodeFlags.putAll(source, nodeFlags.removeAll(target));
      // Merge the violated properties
      violatedProperties.putAll(source, violatedProperties.removeAll(target));

      // Move the leaving edges
      FluentIterable<Edge> leavingEdges = FluentIterable.from(Lists.newArrayList(this.leavingEdges.get(target)));
      // Remove the edges from their successors
      for (Edge leavingEdge : leavingEdges) {
        boolean removed = removeEdge(leavingEdge);
        assert removed;
      }
      // Create the replacement edges
      leavingEdges = leavingEdges
          .transform(new Function<Edge, Edge>() {

            @Override
            public Edge apply(Edge pOldEdge) {
              TransitionCondition label = new TransitionCondition();
              label.keyValues.putAll(pEdge.label.keyValues);
              label.keyValues.putAll(pOldEdge.label.keyValues);
              return new Edge(source, pOldEdge.target, label);
            }

          });
      // Add them as leaving edges to the source node
      // and them as entering edges to their target nodes
      for (Edge leavingEdge : leavingEdges) {
        putEdge(leavingEdge);
      }

      // Move the entering edges
      FluentIterable<Edge> enteringEdges = FluentIterable.from(Lists.newArrayList(this.enteringEdges.get(target)));
      // Remove the edges from their predecessors
      for (Edge enteringEdge : enteringEdges) {
        boolean removed = removeEdge(enteringEdge);
        assert removed;
      }
      // Create the replacement edges
      enteringEdges = enteringEdges
          .filter(Predicates.not(Predicates.equalTo(pEdge)))
          .transform(new Function<Edge, Edge>() {

            @Override
            public Edge apply(Edge pOldEdge) {
              TransitionCondition label = new TransitionCondition();
              label.keyValues.putAll(pEdge.label.keyValues);
              label.keyValues.putAll(pOldEdge.label.keyValues);
              return new Edge(pOldEdge.source, source, label);
            }

          });
      // Add them as entering edges to the source node
      // and add them as leaving edges to their source nodes
      for (Edge enteringEdge : enteringEdges) {
        putEdge(enteringEdge);
      }

    }

    private void putEdge(Edge pEdge) {
      leavingEdges.put(pEdge.source, pEdge);
      enteringEdges.put(pEdge.target, pEdge);
    }

    private boolean removeEdge(Edge pEdge) {
      if (leavingEdges.remove(pEdge.source, pEdge)) {
        boolean alsoRemoved = enteringEdges.remove(pEdge.target, pEdge);
        assert alsoRemoved;
        return true;
      }
      return false;
    }

    private void newEdge(GraphMlBuilder pDoc, Edge pEdge) {
      Element result = pDoc.createEdgeElement(pEdge.source, pEdge.target);
      for (KeyDef k : pEdge.label.keyValues.keySet())  {
        pDoc.addDataElementChild(result, k, pEdge.label.keyValues.get(k));
      }
      pDoc.appendToAppendable(result);
    }

    private void appendNewNode(GraphMlBuilder pDoc, String pEntryStateNodeId) throws IOException {
      Element result = pDoc.createNodeElement(pEntryStateNodeId, NodeType.ONPATH);
      for (NodeFlag f : nodeFlags.get(pEntryStateNodeId)) {
        pDoc.addDataElementChild(result, f.key, "true");
      }
      for (Property violation : violatedProperties.get(pEntryStateNodeId)) {
        pDoc.addDataElementChild(result, KeyDef.VIOLATEDPROPERTY, violation.toString());
      }
      pDoc.appendToAppendable(result);
    }

    private Collection<NodeFlag> extractNodeFlags(ARGState pState) {
      if (pState.isTarget()) {
        return Collections.singleton(NodeFlag.ISVIOLATION);
      }
      return Collections.emptySet();
    }

    private Collection<Property> extractViolatedProperties(ARGState pState) {
      ArrayList<Property> result = Lists.newArrayList();
      if (pState.isTarget()) {
        result.addAll(pState.getViolatedProperties());
      }
      return result;
    }
  }

  private static class DelayedAssignmentsKey {

    private final String from;

    private final CFAEdge edge;

    private final ARGState state;

    public DelayedAssignmentsKey(String pFrom, CFAEdge pEdge, ARGState pState) {
      this.from = pFrom;
      this.edge = pEdge;
      this.state = pState;
    }

    @Override
    public int hashCode() {
      return Objects.hash(from, edge, state);
    }

    @Override
    public boolean equals(Object pObj) {
      if (this == pObj) {
        return true;
      }
      if (pObj instanceof DelayedAssignmentsKey) {
        DelayedAssignmentsKey other = (DelayedAssignmentsKey) pObj;
        return Objects.equals(from, other.from)
            && Objects.equals(edge, other.edge)
            && Objects.equals(state, other.state);
      }
      return false;
    }

  }

  private static class AssignsParameterOfOtherFunction implements Predicate<AExpressionStatement> {

    private final CFAEdge edge;

    private final String qualifier;

    public AssignsParameterOfOtherFunction(CFAEdge pEdge) {
      edge = pEdge;
      String currentFunctionName = pEdge.getPredecessor().getFunctionName();
      qualifier = Strings.isNullOrEmpty(currentFunctionName) ? "" : currentFunctionName + "::";
    }

    @Override
    public boolean apply(AExpressionStatement pArg0) {
      AExpression exp = pArg0.getExpression();
      if (!(exp instanceof CExpression)) {
        return false;
      }
      CExpression cExp = (CExpression) exp;
      return cExp.accept(new CExpressionVisitor<Boolean, RuntimeException>() {

        @Override
        public Boolean visit(CArraySubscriptExpression pIastArraySubscriptExpression) {
          return pIastArraySubscriptExpression.getArrayExpression().accept(this)
              && pIastArraySubscriptExpression.getSubscriptExpression().accept(this);
        }

        @Override
        public Boolean visit(CFieldReference pIastFieldReference) {
          return pIastFieldReference.getFieldOwner().accept(this);
        }

        @Override
        public Boolean visit(CIdExpression pIastIdExpression) {
          CSimpleDeclaration declaration = pIastIdExpression.getDeclaration();
          if (declaration instanceof CParameterDeclaration && edge instanceof FunctionCallEdge) {
            return declaration.getQualifiedName().startsWith(qualifier);
          }
          return true;
        }

        @Override
        public Boolean visit(CPointerExpression pPointerExpression) {
          return pPointerExpression.getOperand().accept(this);
        }

        @Override
        public Boolean visit(CComplexCastExpression pComplexCastExpression) {
          return pComplexCastExpression.getOperand().accept(this);
        }

        @Override
        public Boolean visit(CBinaryExpression pIastBinaryExpression) {
          return pIastBinaryExpression.getOperand1().accept(this)
              && pIastBinaryExpression.getOperand2().accept(this);
        }

        @Override
        public Boolean visit(CCastExpression pIastCastExpression) {
          return pIastCastExpression.getOperand().accept(this);
        }

        @Override
        public Boolean visit(CCharLiteralExpression pIastCharLiteralExpression) {
          return true;
        }

        @Override
        public Boolean visit(CFloatLiteralExpression pIastFloatLiteralExpression) {
          return true;
        }

        @Override
        public Boolean visit(CIntegerLiteralExpression pIastIntegerLiteralExpression) {
          return true;
        }

        @Override
        public Boolean visit(CStringLiteralExpression pIastStringLiteralExpression) {
          return true;
        }

        @Override
        public Boolean visit(CTypeIdExpression pIastTypeIdExpression) {
          return true;
        }

        @Override
        public Boolean visit(CUnaryExpression pIastUnaryExpression) {
          return pIastUnaryExpression.getOperand().accept(this);
        }

        @Override
        public Boolean visit(CImaginaryLiteralExpression pIastLiteralExpression) {
          return pIastLiteralExpression.getValue().accept(this);
        }

        @Override
        public Boolean visit(CAddressOfLabelExpression pAddressOfLabelExpression) {
          return true;
        }
      });
    }

  }

  private static class TransitionCondition implements Comparable<TransitionCondition> {

    public final SortedMap<KeyDef, String> keyValues = Maps.newTreeMap();

    public void put(final KeyDef pKey, final String pValue) {
      keyValues.put(pKey, pValue);
    }

    @Override
    public boolean equals(Object pOther) {
      if (this == pOther) {
        return true;
      }
      if (!(pOther instanceof TransitionCondition)) {
        return false;
      }

      TransitionCondition oT = (TransitionCondition) pOther;

      return this.keyValues.equals(oT.keyValues);
    }

    public boolean hasTransitionRestrictions() {
      return !keyValues.isEmpty();
    }

    @Override
    public int hashCode() {
      return keyValues.hashCode();
    }

    @Override
    public String toString() {
      return keyValues.toString();
    }

    public boolean summarizes(TransitionCondition pLabel) {
      if (equals(pLabel)) {
        return true;
      }
      boolean ignoreAssumptionScope =
          !keyValues.keySet().contains(KeyDef.ASSUMPTION)
              || !pLabel.keyValues.keySet().contains(KeyDef.ASSUMPTION);
      boolean ignoreInvariantScope =
          !keyValues.keySet().contains(KeyDef.INVARIANT)
          || !pLabel.keyValues.keySet().contains(KeyDef.INVARIANT);
      for (KeyDef keyDef : KeyDef.values()) {
        if (!keyDef.equals(KeyDef.ASSUMPTION)
            && !keyDef.equals(KeyDef.INVARIANT)
            && !(ignoreAssumptionScope && keyDef.equals(KeyDef.ASSUMPTIONSCOPE))
            && !(ignoreInvariantScope && keyDef.equals(KeyDef.INVARIANTSCOPE))
            && !Objects.equals(keyValues.get(keyDef), pLabel.keyValues.get(keyDef))) {
          return false;
        }
      }
      return true;
    }

    @Override
    public int compareTo(TransitionCondition pO) {
      if (this == pO) {
        return 0;
      }
      SortedMapDifference<KeyDef, String> differences = Maps.difference(keyValues, pO.keyValues);
      if (differences.areEqual()) {
        return 0;
      }
      if (differences.entriesOnlyOnLeft().isEmpty()) {
        return -1;
      } else if (differences.entriesOnlyOnRight().isEmpty()) {
        return 1;
      }
      ValueDifference<String> difference =
          differences.entriesDiffering().values().iterator().next();
      return difference.leftValue().compareTo(difference.rightValue());
    }
  }

  private static class Edge implements Comparable<Edge> {

    private final String source;

    private final String target;

    private final TransitionCondition label;

    public Edge(String pSource, String pTarget, TransitionCondition pLabel) {
      Preconditions.checkNotNull(pSource);
      Preconditions.checkNotNull(pTarget);
      Preconditions.checkNotNull(pLabel);
      this.source = pSource;
      this.target = pTarget;
      this.label = pLabel;
    }

    @Override
    public String toString() {
      return String.format("{%s -- %s --> %s}", source, label, target);
    }

    @Override
    public int compareTo(Edge pO) {
      if (pO == this) {
        return 0;
      }
      int comp = source.compareTo(pO.source);
      if (comp != 0) {
        return comp;
      }
      comp = target.compareTo(pO.target);
      if (comp != 0) {
        return comp;
      }
      return label.compareTo(pO.label);
    }

    @Override
    public int hashCode() {
      return Objects.hash(source, target, label);
    }

    @Override
    public boolean equals(Object pOther) {
      if (this == pOther) {
        return true;
      }
      if (pOther instanceof Edge) {
        Edge other = (Edge) pOther;
        return source.equals(other.source)
            && target.equals(other.target)
            && label.equals(other.label);
      }
      return false;
    }
  }

}
