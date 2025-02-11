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
package org.sosy_lab.cpachecker.core.algorithm.invariants;

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.sosy_lab.common.Classes.UnexpectedCheckedException;
import org.sosy_lab.common.LazyFutureTask;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.invariants.InvariantSupplier.TrivialInvariantSupplier;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AdjustableConditionCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSetWrapper;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.invariants.InvariantsCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.BooleanFormulaManager;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Class that encapsulates invariant generation by using the CPAAlgorithm
 * with an appropriate configuration.
 * Supports synchronous and asynchronous execution,
 * and continuously-refined invariants.
 */
@Options(prefix="invariantGeneration")
public class CPAInvariantGenerator implements InvariantGenerator, StatisticsProvider {

  private static class CPAInvariantGeneratorStatistics implements Statistics {

    final Timer invariantGeneration = new Timer();

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
      out.println("Time for invariant generation:   " + invariantGeneration);
    }

    @Override
    public String getName() {
      return "CPA-based invariant generator";
    }
  }

  @Options(prefix="invariantGeneration")
  private static class InvariantGeneratorOptions {


    @Option(secure=true, description="generate invariants in parallel to the normal analysis")
    private boolean async = false;

    @Option(secure=true, description="adjust invariant generation conditions if supported by the analysis")
    private boolean adjustConditions = false;

  }

  @Option(secure=true, name="config",
          required=true,
          description="configuration file for invariant generation")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path configFile;

  private final CPAInvariantGeneratorStatistics stats = new CPAInvariantGeneratorStatistics();
  private final LogManager logger;
  private final CPAAlgorithm algorithm;
  private final ConfigurableProgramAnalysis cpa;
  private final ReachedSetFactory reachedSetFactory;

  private final ShutdownManager shutdownManager;

  private final int iteration;

  // After start(), this will hold a Future for the final result of the invariant generation.
  // We use a Future instead of just the atomic reference below
  // to be able to ask for termination and see thrown exceptions.
  private Future<InvariantSupplier> invariantGenerationFuture = null;

  private volatile boolean programIsSafe = false;

  private final ShutdownRequestListener shutdownListener = new ShutdownRequestListener() {

    @Override
    public void shutdownRequested(String pReason) {
      if (!invariantGenerationFuture.isDone() && !programIsSafe) {
        invariantGenerationFuture.cancel(true);
      }
    }
  };

  private Optional<ShutdownManager> shutdownOnSafeNotifier;

  /**
   * Creates a new {@link CPAInvariantGenerator}.
   *
   * @param pConfig the configuration options.
   * @param pLogger the logger to be used.
   * @param pShutdownManager shutdown notifier to shutdown the invariant generator.
   * @param pShutdownOnSafeManager optional shutdown notifier that will be
   * notified if the invariant generator proves safety.
   * @param pCFA the CFA to run the CPA on.
   * @param additionalAutomata additional specification automata that should be used
   *                           during invariant generation
   *
   * @return a new {@link CPAInvariantGenerator}.
   *
   * @throws InvalidConfigurationException if the configuration is invalid.
   * @throws CPAException if the CPA cannot be created.
   */
  public static InvariantGenerator create(final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownManager pShutdownManager,
      final Optional<ShutdownManager> pShutdownOnSafeManager,
      final CFA pCFA)
          throws InvalidConfigurationException, CPAException {
    return create(pConfig, pLogger, pShutdownManager, pShutdownOnSafeManager, pCFA, Collections.<Automaton>emptyList());
  }

  /**
   * Creates a new {@link CPAInvariantGenerator}.
   *
   * @param pConfig the configuration options.
   * @param pLogger the logger to be used.
   * @param pShutdownManager shutdown notifier to shutdown the invariant generator.
   * @param pShutdownOnSafeManager optional shutdown notifier that will be
   * notified if the invariant generator proves safety.
   * @param pCFA the CFA to run the CPA on.
   *
   * @return a new {@link CPAInvariantGenerator}.
   *
   * @throws InvalidConfigurationException if the configuration is invalid.
   * @throws CPAException if the CPA cannot be created.
   */
  public static InvariantGenerator create(final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownManager pShutdownManager,
      final Optional<ShutdownManager> pShutdownOnSafeManager,
      final CFA pCFA,
      final List<Automaton> additionalAutomata)
          throws InvalidConfigurationException, CPAException {

    InvariantGeneratorOptions options = new InvariantGeneratorOptions();
    pConfig.inject(options);

    CPAInvariantGenerator cpaInvariantGenerator = new CPAInvariantGenerator(
            pConfig,
            pLogger.withComponentName("CPAInvariantGenerator"),
            ShutdownManager.createWithParent(pShutdownManager.getNotifier()),
            pShutdownOnSafeManager,
            1,
            pCFA,
            additionalAutomata);
    InvariantGenerator invariantGenerator = cpaInvariantGenerator;
    final Function<CPAInvariantGenerator, CPAInvariantGenerator> adjust;
    if (options.adjustConditions) {
      adjust = new Function<CPAInvariantGenerator, CPAInvariantGenerator>() {

        @Override
        public CPAInvariantGenerator apply(CPAInvariantGenerator pToAdjust) {
          ConfigurableProgramAnalysis cpa = pToAdjust.cpa;
          LogManager logger = pToAdjust.logger;
          List<AdjustableConditionCPA> conditionCPAs = CPAs.asIterable(cpa).filter(AdjustableConditionCPA.class).toList();
          CPAInvariantGenerator result = pToAdjust;
          try {
            if (adjustConditions(logger, conditionCPAs)) {
              result = new CPAInvariantGenerator(pConfig, pLogger, pShutdownManager, pShutdownOnSafeManager, pToAdjust.iteration + 1, pToAdjust.reachedSetFactory, cpa, pToAdjust.algorithm);
            }
          } catch (InvalidConfigurationException | CPAException e) {
            pLogger.logUserException(Level.WARNING, e, "Creating adjusted invariant generator failed");
          } finally {
            if (result == pToAdjust) {
              CPAs.closeCpaIfPossible(pToAdjust.cpa, pToAdjust.logger);
              CPAs.closeIfPossible(pToAdjust.algorithm, pToAdjust.logger);
            }
          }
          return result;
        }

        private boolean adjustConditions(LogManager pLogger, List<AdjustableConditionCPA> pConditionCPAs) {

          boolean adjusted = false;

          // Adjust precision if at least one CPA can do it.
          for (AdjustableConditionCPA cpa : pConditionCPAs) {
            if (cpa.adjustPrecision()) {
              pLogger.log(Level.INFO, "Adjusting precision for CPA", cpa);
              adjusted = true;
            }
          }
          if (!adjusted) {
            pLogger.log(Level.INFO, "None of the CPAs could adjust precision, "
                + "stopping invariant generation");
          }
          return adjusted;
        }

      };
    } else {
      adjust = new Function<CPAInvariantGenerator, CPAInvariantGenerator>() {

        @Override
        public CPAInvariantGenerator apply(CPAInvariantGenerator pArg0) {
          CPAs.closeCpaIfPossible(pArg0.cpa, pArg0.logger);
          CPAs.closeIfPossible(pArg0.algorithm, pArg0.logger);
          return pArg0;
        }

      };
    }
    invariantGenerator =
        new AdjustableInvariantGenerator<>(
            pShutdownManager.getNotifier(), cpaInvariantGenerator, adjust);
    if (options.async) {
      invariantGenerator =
          new AutoAdjustingInvariantGenerator<>(
              pShutdownManager.getNotifier(), cpaInvariantGenerator, adjust);
    }
    return invariantGenerator;
  }

  private CPAInvariantGenerator(final Configuration config,
      final LogManager pLogger,
      final ShutdownManager pShutdownManager,
      Optional<ShutdownManager> pShutdownOnSafeManager,
      final int pIteration,
      final CFA cfa, List<Automaton> pAdditionalAutomata) throws InvalidConfigurationException, CPAException {
    config.inject(this);
    logger = pLogger;
    shutdownManager = pShutdownManager;
    shutdownOnSafeNotifier = pShutdownOnSafeManager;
    iteration = pIteration;

    Configuration invariantConfig;
    try {
      ConfigurationBuilder configBuilder = Configuration.builder().copyOptionFrom(config, "specification");
      configBuilder.loadFromFile(configFile);
      invariantConfig = configBuilder.build();
    } catch (IOException e) {
      throw new InvalidConfigurationException("could not read configuration file for invariant generation: " + e.getMessage(), e);
    }

    reachedSetFactory = new ReachedSetFactory(invariantConfig, logger);
    cpa =
        new CPABuilder(invariantConfig, logger, shutdownManager.getNotifier(), reachedSetFactory)
            .buildsCPAWithWitnessAutomataAndSpecification(cfa, pAdditionalAutomata);
    algorithm = CPAAlgorithm.create(cpa, logger, invariantConfig, shutdownManager.getNotifier());
  }

  private CPAInvariantGenerator(final Configuration config,
      final LogManager pLogger,
      final ShutdownManager pShutdownManager,
      Optional<ShutdownManager> pShutdownOnSafeManager,
      final int pIteration,
      ReachedSetFactory pReachedSetFactory,
      ConfigurableProgramAnalysis pCPA,
      CPAAlgorithm pAlgorithm) throws InvalidConfigurationException, CPAException {
    config.inject(this);
    logger = pLogger;
    shutdownManager = pShutdownManager;
    shutdownOnSafeNotifier = pShutdownOnSafeManager;
    iteration = pIteration;

    reachedSetFactory = pReachedSetFactory;
    cpa = pCPA;
    algorithm = pAlgorithm;
  }

  @Override
  public void start(final CFANode initialLocation) {
    checkState(invariantGenerationFuture == null);

    Callable<InvariantSupplier> task = new InvariantGenerationTask(initialLocation);
    // create future for lazy synchronous invariant generation
    invariantGenerationFuture = new LazyFutureTask<>(task);

    shutdownManager.getNotifier().registerAndCheckImmediately(shutdownListener);
  }

  @Override
  public void cancel() {
    checkState(invariantGenerationFuture != null);
    shutdownManager.requestShutdown("Invariant generation cancel requested.");
  }

  @Override
  public InvariantSupplier get() throws CPAException, InterruptedException {
    checkState(invariantGenerationFuture != null);

    try {
      return invariantGenerationFuture.get();
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), CPAException.class, InterruptedException.class);
      throw new UnexpectedCheckedException("invariant generation", e.getCause());
    } catch (CancellationException e) {
      shutdownManager.getNotifier().shutdownIfNecessary();
      throw e;
    }
  }

  @Override
  public boolean isProgramSafe() {
    return programIsSafe;
  }

  @Override
  public void injectInvariant(CFANode pLocation, AssumeEdge pAssumption) throws UnrecognizedCodeException {
    InvariantsCPA invariantCPA = CPAs.retrieveCPA(cpa, InvariantsCPA.class);
    if (invariantCPA != null) {
      invariantCPA.injectInvariant(pLocation, pAssumption);
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (cpa instanceof StatisticsProvider) {
      ((StatisticsProvider)cpa).collectStatistics(pStatsCollection);
    }
    algorithm.collectStatistics(pStatsCollection);
    pStatsCollection.add(stats);
  }

  /**
   * {@link InvariantSupplier} that extracts invariants from a {@link ReachedSet}
   * with {@link FormulaReportingState}s.
   */
  private static class ReachedSetBasedInvariantSupplier implements InvariantSupplier {

    private final LogManager logger;
    private final UnmodifiableReachedSet reached;

    private ReachedSetBasedInvariantSupplier(UnmodifiableReachedSet pReached,
        LogManager pLogger) {
      checkArgument(!pReached.hasWaitingState());
      checkArgument(!pReached.isEmpty());
      reached = pReached;
      logger = pLogger;
    }

    @Override
    public BooleanFormula getInvariantFor(CFANode pLocation, FormulaManagerView fmgr, PathFormulaManager pfmgr) {
      BooleanFormulaManager bfmgr = fmgr.getBooleanFormulaManager();
      BooleanFormula invariant = bfmgr.makeBoolean(false);

      for (AbstractState locState : AbstractStates.filterLocation(reached, pLocation)) {
        BooleanFormula f = AbstractStates.extractReportedFormulas(fmgr, locState, pfmgr);
        logger.log(Level.ALL, "Invariant for", pLocation+":", f);

        invariant = bfmgr.or(invariant, f);
      }
      return invariant;
    }
  }

  /**
   * Callable for creating invariants by running the CPAAlgorithm,
   * potentially in a loop with increasing precision.
   * Returns the final invariants,
   * and publishes intermediate results to {@link CPAInvariantGenerator#latestInvariant}.
   */
  private class InvariantGenerationTask implements Callable<InvariantSupplier> {

    private static final String SAFE_MESSAGE = "Invariant generation with abstract interpretation proved specification to hold.";
    private final CFANode initialLocation;

    private InvariantGenerationTask(final CFANode pInitialLocation) {
      initialLocation = checkNotNull(pInitialLocation);
    }

    @Override
    public InvariantSupplier call() throws Exception {
      stats.invariantGeneration.start();
      try {

        shutdownManager.getNotifier().shutdownIfNecessary();
        logger.log(Level.INFO, "Starting iteration", iteration, "of invariant generation with abstract interpretation.");

        return runInvariantGeneration(initialLocation);

      } finally {
        stats.invariantGeneration.stop();
      }
    }

    private InvariantSupplier runInvariantGeneration(CFANode pInitialLocation)
        throws CPAException, InterruptedException {

      ReachedSet taskReached = reachedSetFactory.create();
      taskReached.add(cpa.getInitialState(pInitialLocation, StateSpacePartition.getDefaultPartition()),
          cpa.getInitialPrecision(pInitialLocation, StateSpacePartition.getDefaultPartition()));

      while (taskReached.hasWaitingState()) {
        if (!algorithm.run(taskReached).isSound()) {
          // ignore unsound invariant and abort
          return TrivialInvariantSupplier.INSTANCE;
        }
      }

      if (!from(taskReached).anyMatch(IS_TARGET_STATE)) {
        // program is safe (waitlist is empty, algorithm was sound, no target states present)
        logger.log(Level.INFO, SAFE_MESSAGE);
        programIsSafe = true;
        if (shutdownOnSafeNotifier.isPresent()) {
          shutdownOnSafeNotifier.get().requestShutdown(SAFE_MESSAGE);
        }
      }

      return new ReachedSetBasedInvariantSupplier(
          new UnmodifiableReachedSetWrapper(taskReached), logger);
    }
  }
}