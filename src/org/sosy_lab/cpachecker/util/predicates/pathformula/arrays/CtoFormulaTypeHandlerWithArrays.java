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
package org.sosy_lab.cpachecker.util.predicates.pathformula.arrays;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaTypeHandler;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.FormulaEncodingOptions;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;


public class CtoFormulaTypeHandlerWithArrays extends CtoFormulaTypeHandler {

  public CtoFormulaTypeHandlerWithArrays(LogManager pLogger, FormulaEncodingOptions pOptions,
      MachineModel pMachineModel, FormulaManagerView pFmgr) {
    super(pLogger, pOptions, pMachineModel, pFmgr);
  }

  @Override
  public FormulaType<?> getFormulaTypeFromCType(CType pType) {
    if (pType instanceof CArrayType) {
      final CArrayType at = (CArrayType) pType;
      FormulaType<?> arrayIndexType = getFormulaTypeFromCType(
          machineModel.getPointerEquivalentSimpleType()); // TODO: Is this correct?
      FormulaType<?> arrayElementType = getFormulaTypeFromCType(at.getType());
      return FormulaType.getArrayType(arrayIndexType, arrayElementType);
    }

    return super.getFormulaTypeFromCType(pType);
  }
}
