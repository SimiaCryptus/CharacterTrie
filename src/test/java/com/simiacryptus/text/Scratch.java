/*
 * Copyright (c) 2018 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.text;

import org.apfloat.Apfloat;
import org.apfloat.ApfloatMath;
import org.apfloat.Apint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The type Scratch.
 */
public class Scratch {
  /**
   * Solve zero apfloat.
   *
   * @param fn  the fn
   * @param min the min
   * @param max the max
   * @return the apfloat
   */
  public static Apfloat solveZero(final Function<Apfloat, Apfloat> fn, Apfloat min, Apfloat max) {
    Apfloat zero = new Apfloat(0);
    Apfloat minVal = fn.apply(min);
    Apfloat maxVal = fn.apply(max);
    Apfloat current = minVal.add(maxVal).divide(new Apint(2));
    Apfloat currentVal = fn.apply(current);
    while (min.compareTo(max) < 0) {
      if (currentVal.compareTo(zero) == 0) break;
      if (minVal.compareTo(zero) == currentVal.compareTo(zero)) {
        min = current;
      }
      else {
        max = current;
      }
      current = minVal.add(maxVal).divide(new Apint(2));
      currentVal = fn.apply(current);
    }
    return current;
  }
  
  /**
   * The type Position.
   */
  public static class Position {
    /**
     * The Space.
     */
    Apfloat[] space;
    /**
     * The Time.
     */
    Apfloat time;
  
    /**
     * Instantiates a new Position.
     *
     * @param time  the time
     * @param space the space
     */
    public Position(Apfloat time, Apfloat... space) {
      this.space = space;
      this.time = time;
    }
  
    /**
     * Distance apfloat.
     *
     * @param to the to
     * @return the apfloat
     */
    public Apfloat distance(Position to) {
      assert to.space.length == space.length;
      Apfloat total = ApfloatMath.pow(to.time.subtract(time), 2).negate();
      for (int i = 0; i < space.length; i++) {
        total = total.add(ApfloatMath.pow(to.space[i].subtract(space[i]), 2));
      }
      return total;
    }
  
    /**
     * Add position.
     *
     * @param to the to
     * @return the position
     */
    public Position add(Position to) {
      assert to.space.length == space.length;
      
      Apfloat[] newSpace = new Apfloat[space.length];
      for (int i = 0; i < space.length; i++) {
        newSpace[i] = to.space[i].add(space[i]);
      }
      return new Position(to.time.add(time), newSpace);
    }
  
    /**
     * Multiply position.
     *
     * @param factor the factor
     * @return the position
     */
    public Position multiply(Apfloat factor) {
      Apfloat[] newSpace = new Apfloat[space.length];
      for (int i = 0; i < space.length; i++) {
        newSpace[i] = space[i].multiply(factor);
      }
      return new Position(time.multiply(factor), newSpace);
    }
  }
  
  /**
   * The type World line.
   */
  public static class WorldLine {
    /**
     * The Positions.
     */
    List<Position> positions = new ArrayList<>();
  
    /**
     * Position at index position.
     *
     * @param index the index
     * @return the position
     */
    public Position positionAtIndex(Apfloat index) {
      Apint intPart = index.truncate();
      Apfloat aFrac = index.frac();
      Apfloat bFrac = new Apfloat(1, aFrac.precision()).subtract(aFrac);
      Position a = positions.get(intPart.intValue());
      Position b = positions.get(intPart.intValue() + 1);
      return a.multiply(aFrac).add(b.multiply(bFrac));
    }
  
    /**
     * Connected point position.
     *
     * @param to the to
     * @return the position
     */
    public Position connectedPoint(Position to) {
      return positionAtIndex(solveZero(i -> {
        return positionAtIndex(i).distance(to);
      }, new Apfloat(0), new Apfloat(positions.size())));
    }
    
  }
  
}
