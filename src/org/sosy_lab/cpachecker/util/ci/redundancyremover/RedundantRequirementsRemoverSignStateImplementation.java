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
package org.sosy_lab.cpachecker.util.ci.redundancyremover;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.sign.SIGN;
import org.sosy_lab.cpachecker.cpa.sign.SignState;
import org.sosy_lab.cpachecker.util.ci.redundancyremover.RedundantRequirementsRemover.RedundantRequirementsRemoverImplementation;


public class RedundantRequirementsRemoverSignStateImplementation extends
RedundantRequirementsRemoverImplementation<SignState, SIGN>{

  @Override
  public int compare(SIGN pO1, SIGN pO2) {
    // TODO
    // one of arguments null -> NullPointerException
    // 0 if sign values identical
    // -1 if p02 covers p01
    // 1 if p01 covers p02
    // -1 if p01=MINUS p02=ZERO,PLUS,PLUS0
    // -1 if p01=ZERO p02=PLUS,PLUSMINUS
    // -1 if p01=PLUSMINUS p02=PLUS0
   // -1 if p01=MINUS0 p02=PLUS,PLUS0,PLUSMINUS
    // otherwise 1
    return 0;
  }

  @Override
  protected boolean covers(SIGN pCovering, SIGN pCovered) {
    // TODO return pCovering.covers(pCovered)
    return false;
  }

  @Override
  protected SIGN getAbstractValue(SignState pAbstractState, String pVarOrConst) {
    // TODO
    // if pVarOrConst number, number<0 MINUS, number=0 ZERO, number>0 PLUS
    // otherwise getSignForVariable
    return null;
  }

  @Override
  protected SIGN[] emptyArrayOfSize(int pSize) {
    // TODO similar to RedundantRequirementsRemoverIntervalStateImplementation
    return null;
  }

  @Override
  protected SIGN[][] emptyMatrixOfSize(int pSize) {
    // TODO similar to RedundantRequirementsRemoverIntervalStateImplementation
    return null;
  }

  @Override
  protected SignState extractState(AbstractState pWrapperState) {
    // TODO AbstractStates.extractStateByType....
    return null;
  }

}
