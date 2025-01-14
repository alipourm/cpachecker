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
package org.sosy_lab.cpachecker.util.predicates.smt;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import java.util.Collection;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.TestLogManager;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.solver.FormulaManagerFactory.Solvers;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.ArrayFormula;
import org.sosy_lab.solver.api.BitvectorFormulaManager;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.FormulaType.NumeralType;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.solver.api.NumeralFormulaManager;
import org.sosy_lab.solver.test.SolverBasedTest0;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@RunWith(Parameterized.class)
public class FormulaManagerViewTest extends SolverBasedTest0 {

  @Parameters(name="{0}")
  public static Object[] getAllSolvers() {
    return Solvers.values();
  }

  @Parameter(0)
  public Solvers solver;

  @Override
  protected Solvers solverToUse() {
    return solver;
  }

  private FormulaManagerView mgrv;
  private BooleanFormulaManagerView bmgrv;
  private NumeralFormulaManagerView<IntegerFormula, IntegerFormula> imgrv;

  @Before
  public void setUp() throws InvalidConfigurationException {
    Configuration viewConfig = Configuration.builder()
        .copyFrom(config)
        // use only theory supported by all solvers:
        .setOption("cpa.predicate.encodeBitvectorAs", "INTEGER")
        .setOption("cpa.predicate.encodeFloatAs", "INTEGER")
        .build();
    mgrv = new FormulaManagerView(factory.getFormulaManager(),
        viewConfig, TestLogManager.getInstance());
    bmgrv = mgrv.getBooleanFormulaManager();
    imgrv = mgrv.getIntegerFormulaManager();
  }

  private BooleanFormula stripNot(BooleanFormula f) {
    return bmgrv.isNot(f) ? (BooleanFormula)mgr.getUnsafeFormulaManager().getArg(f, 0) : f;
  }

  @Test
  public void testExtractDisjuncts() {
    BooleanFormula atom1 = imgr.equal(imgr.makeVariable("a"), imgr.makeNumber(1));
    BooleanFormula atom2 = imgr.greaterThan(imgr.makeVariable("b"), imgr.makeNumber(2));
    BooleanFormula atom3 = imgr.greaterOrEquals(imgr.makeVariable("c"), imgr.makeNumber(3));
    BooleanFormula atom4 = imgr.lessThan(imgr.makeVariable("d"), imgr.makeNumber(4));
    BooleanFormula atom5 = imgr.lessOrEquals(imgr.makeVariable("e"), imgr.makeNumber(5));

    BooleanFormula f = bmgrv.and(ImmutableList.of(
        bmgrv.or(atom1, atom2), bmgrv.not(bmgrv.or(atom1, atom3)), atom4, atom5));

    assertThat(mgrv.extractDisjuncts(f))
        .containsExactly(bmgrv.or(atom1, atom2), bmgrv.or(atom1, atom3), stripNot(atom4), stripNot(atom5));
  }

  @Test
  public void testExtractLiterals() {
    BooleanFormula atom1 = imgr.equal(imgr.makeVariable("a"), imgr.makeNumber(1));
    BooleanFormula atom2 = imgr.greaterThan(imgr.makeVariable("b"), imgr.makeNumber(2));
    BooleanFormula atom3 = imgr.greaterOrEquals(imgr.makeVariable("c"), imgr.makeNumber(3));
    BooleanFormula atom4 = imgr.lessThan(imgr.makeVariable("d"), imgr.makeNumber(4));
    BooleanFormula atom5 = imgr.lessOrEquals(imgr.makeVariable("e"), imgr.makeNumber(5));

    BooleanFormula f = bmgrv.and(ImmutableList.of(
        bmgrv.or(atom1, atom2), bmgrv.not(bmgrv.or(atom1, atom3)), atom4, atom5));

    assertThat(mgrv.extractLiterals(f))
        .containsExactly(atom1, atom2, bmgrv.not(bmgrv.or(atom1, atom3)), atom4, atom5);

    // TODO: this should really be the following (c.f. FormulaManagerView.extractLiterals)
//    assertThat(mgrv.extractLiterals(f, false))
//        .containsExactly(atom1, atom2, atom3, atom4, atom5);
  }

  @Test
  public void testExtractAtoms() {
    BooleanFormula atom1 = imgr.equal(imgr.makeVariable("a"), imgr.makeNumber(1));
    BooleanFormula atom2 = imgr.greaterThan(imgr.makeVariable("b"), imgr.makeNumber(2));
    BooleanFormula atom3 = imgr.greaterOrEquals(imgr.makeVariable("c"), imgr.makeNumber(3));
    BooleanFormula atom4 = imgr.lessThan(imgr.makeVariable("d"), imgr.makeNumber(4));
    BooleanFormula atom5 = imgr.lessOrEquals(imgr.makeVariable("e"), imgr.makeNumber(5));

    BooleanFormula f = bmgrv.or(ImmutableList.of(
        bmgrv.and(atom1, atom2), bmgrv.and(atom1, atom3), atom4, atom5));

    assertThat(mgrv.extractAtoms(f, false))
        .containsExactly(stripNot(atom1), stripNot(atom2), stripNot(atom3), stripNot(atom4), stripNot(atom5));
  }

  private void testExtractAtoms_SplitEqualities(
      BooleanFormula atom1, BooleanFormula atom1ineq,
      BooleanFormula atom2, BooleanFormula atom3,
      BooleanFormula atom4, BooleanFormula atom5) throws SolverException, InterruptedException {

    BooleanFormula f = bmgrv.or(ImmutableList.of(
        bmgrv.and(atom1, atom2), bmgrv.and(atom1, atom3), atom4, atom5));

    Collection<BooleanFormula> atoms = mgrv.extractAtoms(f, true);
    Set<BooleanFormula> expected = ImmutableSet.of(stripNot(atom1), stripNot(atom2), stripNot(atom3), stripNot(atom4), stripNot(atom5));

    // Assert that atoms contains all of atom1-5
    // and another atom that is equivalent to atom1ineq
    assertThat(atoms).hasSize(6);
    assertThat(atoms).containsAllIn(expected);

    atoms.removeAll(expected);
    BooleanFormula remainingAtom = Iterables.getOnlyElement(atoms);
    assertThatFormula(remainingAtom).isEquivalentTo(stripNot(atom1ineq));
  }

  private <T extends NumeralFormula> void testExtractAtoms_SplitEqualities_numeral(
      NumeralFormulaManager<T, ? extends T> nmgr) throws SolverException, InterruptedException {

    BooleanFormula atom1 = nmgr.equal(nmgr.makeVariable("a"), nmgr.makeNumber(1));
    BooleanFormula atom1ineq = nmgr.lessOrEquals(nmgr.makeVariable("a"), nmgr.makeNumber(1));
    BooleanFormula atom2 = nmgr.greaterThan(nmgr.makeVariable("b"), nmgr.makeNumber(2));
    BooleanFormula atom3 = nmgr.greaterOrEquals(nmgr.makeVariable("c"), nmgr.makeNumber(3));
    BooleanFormula atom4 = nmgr.lessThan(nmgr.makeVariable("d"), nmgr.makeNumber(4));
    BooleanFormula atom5 = nmgr.lessOrEquals(nmgr.makeVariable("e"), nmgr.makeNumber(5));

    testExtractAtoms_SplitEqualities(atom1, atom1ineq, atom2, atom3, atom4, atom5);
  }

  @Test
  public void testExtractAtoms_SplitEqualities_int() throws SolverException, InterruptedException {
    testExtractAtoms_SplitEqualities_numeral(imgr);
  }

  @Test
  public void testExtractAtoms_SplitEqualities_rat() throws SolverException, InterruptedException {
    requireRationals();
    testExtractAtoms_SplitEqualities_numeral(rmgr);
  }

  private void testExtractAtoms_SplitEqualities_bitvectors(BitvectorFormulaManager bvmgr) throws SolverException, InterruptedException {
    BooleanFormula atom1 = bvmgr.equal(bvmgr.makeVariable(32, "a"), bvmgr.makeBitvector(32, 1));
    BooleanFormula atom1ineq = bvmgr.lessOrEquals(bvmgr.makeVariable(32, "a"), bvmgr.makeBitvector(32, 1), false);
    BooleanFormula atom2 = bvmgr.greaterThan(bvmgr.makeVariable(32, "b"), bvmgr.makeBitvector(32, 2), false);
    BooleanFormula atom3 = bvmgr.greaterOrEquals(bvmgr.makeVariable(32, "c"), bvmgr.makeBitvector(32, 3), false);
    BooleanFormula atom4 = bvmgr.lessThan(bvmgr.makeVariable(32, "d"), bvmgr.makeBitvector(32, 4), false);
    BooleanFormula atom5 = bvmgr.lessOrEquals(bvmgr.makeVariable(32, "e"), bvmgr.makeBitvector(32, 5), false);

    testExtractAtoms_SplitEqualities(atom1, atom1ineq, atom2, atom3, atom4, atom5);
  }

  @Test
  public void testExtractAtoms_SplitEqualities_bv() throws SolverException, InterruptedException {
    requireBitvectors();
    testExtractAtoms_SplitEqualities_bitvectors(bvmgr);
  }

  @Test
  public void testExtractAtoms_SplitEqualities_bvReplaceByInt() throws SolverException, InterruptedException {
    // BitvectorFormulaManagerView here!
    testExtractAtoms_SplitEqualities_bitvectors(mgrv.getBitvectorFormulaManager());
  }

  @Test
  public void testUnInstantiateQuantifiersAndArrays() throws SolverException, InterruptedException {
    requireQuantifiers();
    requireArrays();

    IntegerFormula _0 = imgrv.makeNumber(0);
    IntegerFormula _i = imgrv.makeVariable("i");
    IntegerFormula _i1 = imgrv.makeVariable("i@1");
    IntegerFormula _j = imgrv.makeVariable("j");
    IntegerFormula _j1 = imgrv.makeVariable("j@1");
    IntegerFormula _x = imgrv.makeVariable("x");

    ArrayFormulaManagerView amgrv = mgrv.getArrayFormulaManager();
    ArrayFormula<IntegerFormula, IntegerFormula> _b =
        amgrv.makeArray("b", NumeralType.IntegerType, NumeralType.IntegerType);

    BooleanFormula _b_at_x_NOTEQ_0 = bmgrv.not(imgrv.equal(amgrv.select(_b, _x), _0));

    QuantifiedFormulaManagerView qmv = mgrv.getQuantifiedFormulaManager();
    BooleanFormula instantiated =
        qmv.forall(
            Lists.newArrayList(_x),
            bmgrv.and(
                Lists.newArrayList(
                    _b_at_x_NOTEQ_0, imgrv.greaterOrEquals(_x, _j1), imgrv.lessOrEquals(_x, _i1))));

    BooleanFormula uninstantiated =
        qmv.forall(
            Lists.newArrayList(_x),
            bmgrv.and(
                Lists.newArrayList(
                    _b_at_x_NOTEQ_0, imgrv.greaterOrEquals(_x, _j), imgrv.lessOrEquals(_x, _i))));

    SSAMapBuilder ssaBuilder = SSAMap.emptySSAMap().builder();
    ssaBuilder.setIndex("i", CNumericTypes.INT, 1);
    ssaBuilder.setIndex("j", CNumericTypes.INT, 1);

    testUnInstantiate(instantiated, uninstantiated, ssaBuilder);
  }

  @Test
  public void testUnInstantiate() throws SolverException, InterruptedException {
    IntegerFormula _0 = imgrv.makeNumber(0);
    IntegerFormula _1 = imgrv.makeNumber(1);
    IntegerFormula _i = imgrv.makeVariable("i");
    IntegerFormula _i1 = imgrv.makeVariable("i@1");
    IntegerFormula _j = imgrv.makeVariable("j");
    IntegerFormula _j1 = imgrv.makeVariable("j@1");
    IntegerFormula _x = imgrv.makeVariable("x");
    IntegerFormula _x1 = imgrv.makeVariable("x@1");

    BooleanFormula _inst1 = imgrv.equal(imgrv.add(_1, _j1), imgrv.add(_0, _i1));
    BooleanFormula _inst2 =
        imgrv.equal(imgrv.add(_1, imgrv.subtract(_0, _i1)), imgrv.add(imgrv.add(_0, _x1), _i1));
    BooleanFormula _inst3 = bmgrv.and(Lists.newArrayList(_inst1, _inst2, bmgrv.not(_inst1)));

    BooleanFormula _uinst1 = imgrv.equal(imgrv.add(_1, _j), imgrv.add(_0, _i));
    BooleanFormula _uinst2 =
        imgrv.equal(imgrv.add(_1, imgrv.subtract(_0, _i)), imgrv.add(imgrv.add(_0, _x), _i));
    BooleanFormula _uinst3 = bmgrv.and(Lists.newArrayList(_uinst1, _uinst2, bmgrv.not(_uinst1)));

    SSAMapBuilder ssaBuilder = SSAMap.emptySSAMap().builder();
    ssaBuilder.setIndex("i", CNumericTypes.INT, 1);
    ssaBuilder.setIndex("j", CNumericTypes.INT, 1);
    ssaBuilder.setIndex("x", CNumericTypes.INT, 1);

    testUnInstantiate(_inst3, _uinst3, ssaBuilder);
  }

  private void testUnInstantiate(
      BooleanFormula pInstantiated, BooleanFormula pUninstantiated, SSAMapBuilder pSsaBuilder)
      throws SolverException, InterruptedException {
    BooleanFormula r1 = mgrv.instantiate(pUninstantiated, pSsaBuilder.build());
    assertThatFormula(r1).isEquivalentTo(pInstantiated);
    assertThat(r1.toString()).isEqualTo(pInstantiated.toString());

    BooleanFormula r2 = mgrv.uninstantiate(pInstantiated);
    assertThatFormula(r2).isEquivalentTo(pUninstantiated);
    assertThat(r2.toString()).isEqualTo(pUninstantiated.toString());
  }
}
