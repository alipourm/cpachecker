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
package org.sosy_lab.cpachecker.util.expressions;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;


public class Or implements ExpressionTree {

  private List<ExpressionTree> operands;

  private Or(Collection<? extends ExpressionTree> pOperands) {
    if (pOperands.size() < 2) {
      throw new IllegalArgumentException("Disjunction must have at least two operands.");
    }
    operands = ImmutableList.copyOf(pOperands);
  }

  @Override
  public Iterator<ExpressionTree> iterator() {
    return operands.iterator();
  }

  @Override
  public <T> T accept(ExpressionTreeVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }

  @Override
  public int hashCode() {
    return operands.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (this == pObj) {
      return true;
    }
    if (pObj instanceof Or) {
      return operands.equals(((Or) pObj).operands);
    }
    return false;
  }

  @Override
  public String toString() {
    return ToCodeVisitor.INSTANCE.visit(this);
  }

  public static ExpressionTree of(Collection<? extends ExpressionTree> pOperands) {
    if (pOperands.isEmpty()) {
      throw new IllegalArgumentException("You must provide at least one operand.");
    }
    if (pOperands.size() == 1) {
      return pOperands.iterator().next();
    }
    return new Or(pOperands);
  }

}
