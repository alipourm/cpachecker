/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.constraints;

import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.java.JSimpleType;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.Constraint;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.ConstraintOperand;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.EqualConstraint;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.LessConstraint;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.LessOrEqualConstraint;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.AdditionExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.BinaryAndExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.BinaryNotExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.BinaryOrExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.BinaryXorExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.ConstantConstraintExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.ConstraintExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.DivisionExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.EqualsExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.LessThanExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.LessThanOrEqualExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.LogicalAndExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.LogicalNotExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.LogicalOrExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.ModuloExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.MultiplicationExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.ShiftLeftExpression;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.expressions.ShiftRightExpression;
import org.sosy_lab.cpachecker.cpa.value.type.BooleanValue;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.cpa.value.type.SymbolicValue;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.cpa.value.type.symbolic.SymbolicExpression;
import org.sosy_lab.cpachecker.cpa.value.type.symbolic.SymbolicIdentifier;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.NumeralFormulaManagerView;

/**
 * Creator for {@link Formula}s using only integer values.
 */
public class IntegerFormulaCreator implements FormulaCreator<Formula> {

  private static final boolean SIGNED = true;

  private final FormulaManagerView formulaManager;
  private final NumeralFormulaManagerView<IntegerFormula, IntegerFormula> numeralFormulaManager;
  private final BooleanFormulaManagerView booleanFormulaManager;

  public IntegerFormulaCreator(FormulaManagerView pFormulaManager) {
    formulaManager = pFormulaManager;
    numeralFormulaManager = formulaManager.getIntegerFormulaManager();
    booleanFormulaManager = formulaManager.getBooleanFormulaManager();
  }


  @Override
  public IntegerFormula visit(AdditionExpression pAdd) {
    final IntegerFormula op1 = (IntegerFormula) pAdd.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pAdd.getOperand2().accept(this);

    return numeralFormulaManager.add(op1, op2);
  }

  @Override
  public IntegerFormula visit(BinaryAndExpression pAnd) {
    return handleUnsupportedFormula(pAnd);
  }

  private IntegerFormula handleUnsupportedFormula(ConstraintExpression pFormula) {
    return formulaManager.makeVariable(FormulaType.IntegerType, getVariableNameByFormula(pFormula));
  }

  private String getVariableNameByFormula(ConstraintExpression pFormula) {
    return pFormula.toString();
  }

  @Override
  public IntegerFormula visit(BinaryNotExpression pNot) {
    return handleUnsupportedFormula(pNot);
  }

  @Override
  public IntegerFormula visit(BinaryOrExpression pOr) {
    return handleUnsupportedFormula(pOr);
  }

  @Override
  public IntegerFormula visit(BinaryXorExpression pXor) {
    return handleUnsupportedFormula(pXor);
  }

  @Override
  public Formula visit(ConstantConstraintExpression pConstant) {
    Value value = pConstant.getValue();

    if (value.isNumericValue()) {
      NumericValue valueAsNumeric = (NumericValue) value;
      long longValue = valueAsNumeric.longValue();
      double doubleValue = valueAsNumeric.doubleValue();

      if (doubleValue % 1 == 0 && longValue == doubleValue) {
        return numeralFormulaManager.makeNumber(valueAsNumeric.longValue());
      } else {
        return handleUnsupportedFormula(pConstant);
      }

    } else if (value instanceof BooleanValue) {
      return booleanFormulaManager.makeBoolean(((BooleanValue)value).isTrue());

    } else if (value instanceof SymbolicValue) {
      return ((SymbolicValue) value).accept(this);
    }

    return null; // if we can't handle it, 'abort'
  }

  @Override
  public Formula visit(SymbolicIdentifier pValue) {
    return formulaManager.makeVariable(getFormulaType(pValue.getType()), pValue.toString());
  }

  @Override
  public Formula visit(SymbolicExpression pValue) {
    return pValue.getExpression().accept(this);
  }

  private FormulaType<?> getFormulaType(Type pType) {
    if (pType instanceof JSimpleType) {
      switch (((JSimpleType) pType).getType()) {
        case BOOLEAN:
          return FormulaType.BooleanType;
        case BYTE:
        case CHAR:
        case SHORT:
        case INT:
        case LONG:
        case FLOAT:
        case DOUBLE:
        case UNSPECIFIED:
          return FormulaType.IntegerType;
        default:
          throw new AssertionError("Unhandled type " + pType);
      }
    } else if (pType instanceof CType) {
      return FormulaType.IntegerType;
    } else {
      throw new AssertionError("Unhandled type " + pType);
    }
  }

  @Override
  public IntegerFormula visit(DivisionExpression pDivide) {
    final IntegerFormula op1 = (IntegerFormula) pDivide.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pDivide.getOperand2().accept(this);

    return numeralFormulaManager.divide(op1, op2);
  }

  @Override
  public BooleanFormula visit(EqualsExpression pEqual) {
    final IntegerFormula op1 = (IntegerFormula) pEqual.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pEqual.getOperand2().accept(this);

    return numeralFormulaManager.equal(op1, op2);
  }

  @Override
  public Formula visit(LogicalOrExpression pExpression) {
    return handleUnsupportedFormula(pExpression);
  }

  @Override
  public BooleanFormula visit(LessThanExpression pLessThan) {
    final IntegerFormula op1 = (IntegerFormula) pLessThan.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pLessThan.getOperand2().accept(this);

    return numeralFormulaManager.lessThan(op1, op2);
  }

  @Override
  public BooleanFormula visit(LogicalAndExpression pAnd) {
    final BooleanFormula op1 = (BooleanFormula) pAnd.getOperand1().accept(this);
    final BooleanFormula op2 = (BooleanFormula) pAnd.getOperand2().accept(this);

    return booleanFormulaManager.and(op1, op2);
  }

  @Override
  public BooleanFormula visit(LogicalNotExpression pNot) {
    final BooleanFormula op = (BooleanFormula) pNot.getOperand().accept(this);

    return booleanFormulaManager.not(op);
  }

  @Override
  public Formula visit(LessThanOrEqualExpression pExpression) {
    final Formula op1 = pExpression.getOperand1().accept(this);
    final Formula op2 = pExpression.getOperand2().accept(this);

    return formulaManager.makeLessOrEqual(op1, op2, SIGNED);
  }

  @Override
  public IntegerFormula visit(ModuloExpression pModulo) {
    final IntegerFormula op1 = (IntegerFormula) pModulo.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pModulo.getOperand2().accept(this);

    return numeralFormulaManager.modulo(op1, op2);
  }

  @Override
  public IntegerFormula visit(MultiplicationExpression pMultiply) {
    final IntegerFormula op1 = (IntegerFormula) pMultiply.getOperand1().accept(this);
    final IntegerFormula op2 = (IntegerFormula) pMultiply.getOperand2().accept(this);

    return numeralFormulaManager.multiply(op1, op2);
  }

  @Override
  public IntegerFormula visit(ShiftLeftExpression pShiftLeft) {
    return handleUnsupportedFormula(pShiftLeft);
  }

  @Override
  public IntegerFormula visit(ShiftRightExpression pShiftRight) {
    return handleUnsupportedFormula(pShiftRight);
  }

  @Override
  public BooleanFormula visit(LessConstraint pConstraint) {
    final FinalFormulaCreator creator = new FinalFormulaCreator() {

      @Override
      public BooleanFormula create(Formula pLeft, Formula pRight) {
        return numeralFormulaManager.lessThan((IntegerFormula) pLeft, (IntegerFormula) pRight);
      }
    };

    return (BooleanFormula) transformConstraint(pConstraint, creator);
  }

  /**
   * Transforms a given constraint to a {@link org.sosy_lab.cpachecker.util.predicates.interfaces.Formula}.
   *
   * <p>The type of the resulting formula depends on the given
   * {@link FinalFormulaCreator} object, not the concrete type of the constraint. All other characteristics depend on the
   * constraint, though.
   *
   * @param pConstraint the constraint whose attributes are used for creating the formula
   * @param pCreator the creator that decides what kind of formula is created
   *
   * @return a formula based on the given parameters.
   */
  // We use a pCreator so everything around the concrete formula creation does not have to be redundant
  private Formula transformConstraint(Constraint pConstraint, FinalFormulaCreator pCreator) {
    final Formula op1 = pConstraint.getLeftOperand().accept(this);
    final Formula op2 = pConstraint.getRightOperand().accept(this);

    Formula finalFormula = pCreator.create(op1, op2);

    if (!pConstraint.isPositiveConstraint()) {
      finalFormula = createNot(finalFormula);
    }

    return finalFormula;
  }

  private BooleanFormula createNot(Formula pFormula) {
    return (BooleanFormula) formulaManager.makeNot(pFormula);
  }

  @Override
  public BooleanFormula visit(LessOrEqualConstraint pConstraint) {
    final FinalFormulaCreator creator = new FinalFormulaCreator() {

      @Override
      public BooleanFormula create(Formula pLeft, Formula pRight) {
        return formulaManager.makeLessOrEqual(pLeft, pRight, false);
      }
    };

    return (BooleanFormula) transformConstraint(pConstraint, creator);
  }

  @Override
  public BooleanFormula visit(EqualConstraint pConstraint) {
    final FinalFormulaCreator creator = new FinalFormulaCreator() {

      @Override
      public BooleanFormula create(Formula pLeft, Formula pRight) {
        Formula leftFormula = pLeft;
        Formula rightFormula = pRight;

        if (pLeft instanceof BooleanFormula) {
          rightFormula = createBooleanFormula(pRight);
        } else if (pRight instanceof BooleanFormula) {
          leftFormula = createBooleanFormula(pLeft);
        }

        return formulaManager.makeEqual(leftFormula, rightFormula);
      }
    };

    return (BooleanFormula) transformConstraint(pConstraint, creator);
  }

  private BooleanFormula createBooleanFormula(Formula pFormula) {
    IntegerFormula zeroFormula = numeralFormulaManager.makeNumber(0);

    if (pFormula instanceof IntegerFormula) {
      return formulaManager.makeNot(
          numeralFormulaManager.equal((IntegerFormula) pFormula, zeroFormula));

    } else {
      return (BooleanFormula) pFormula;
    }
  }

  @Override
  public Formula visit(ConstraintOperand pConstraintOperand) {
    return pConstraintOperand.getExpression().accept(this);
  }

  /**
   * Creates a new formula based on two given expressions.
   *
   * The created formula depends on the implementation.
   * This interface is used for creating anonymous classes that are given to {@link #transformConstraint}.
   */
  private interface FinalFormulaCreator {
    Formula create(Formula pLeft, Formula pRight);
  }
}