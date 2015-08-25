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
package org.sosy_lab.cpachecker.cpa.automaton;

import org.sosy_lab.cpachecker.core.interfaces.Property;


public class AutomatonSafetyProperty implements Property {

  private final String propertyDescription;
  private final AutomatonInternalState automatonState;

  public AutomatonSafetyProperty(String pViolatedPropertyDescription, AutomatonInternalState pInternalState) {
    this.propertyDescription = pViolatedPropertyDescription;
    this.automatonState = pInternalState;
  }

  public AutomatonInternalState getAutomatonState() {
    return automatonState;
  }

  @Override
  public String toString() {
    return propertyDescription;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((automatonState == null) ? 0 : automatonState.hashCode());
    result = prime * result + ((propertyDescription == null) ? 0 : propertyDescription.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AutomatonSafetyProperty other = (AutomatonSafetyProperty) obj;
    if (automatonState == null) {
      if (other.automatonState != null) {
        return false;
      }
    } else if (!automatonState.equals(other.automatonState)) {
      return false;
    }
    if (propertyDescription == null) {
      if (other.propertyDescription != null) {
        return false;
      }
    } else if (!propertyDescription.equals(other.propertyDescription)) {
      return false;
    }
    return true;
  }

}