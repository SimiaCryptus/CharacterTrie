/*
 * Copyright (c) 2019 by Andrew Charneski.
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

import com.simiacryptus.ref.lang.RefAware;

class NodeData {
  // Primary data defining the tree
  char token;
  short numberOfChildren;
  int firstChildIndex;
  // Associated data to be stored for each node
  long cursorCount;
  long firstCursorIndex;

  public NodeData(char token, short numberOfChildren, int firstChildIndex, long cursorCount, long firstCursorIndex) {
    this.token = token;
    this.numberOfChildren = numberOfChildren;
    this.firstChildIndex = firstChildIndex;
    this.cursorCount = cursorCount;
    this.firstCursorIndex = firstCursorIndex;
  }

  public NodeData setCursorCount(long cursorCount) {
    this.cursorCount = cursorCount;
    return this;
  }

  public NodeData setFirstChildIndex(int firstChildIndex) {
    this.firstChildIndex = firstChildIndex;
    return this;
  }

  public NodeData setFirstCursorIndex(long firstCursorIndex) {
    this.firstCursorIndex = firstCursorIndex;
    return this;
  }

  public NodeData setNumberOfChildren(short numberOfChildren) {
    this.numberOfChildren = numberOfChildren;
    return this;
  }

  public NodeData setToken(char token) {
    this.token = token;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NodeData nodeData = (NodeData) o;

    if (token != nodeData.token) {
      return false;
    }
    if (numberOfChildren != nodeData.numberOfChildren) {
      return false;
    }
    if (firstChildIndex != nodeData.firstChildIndex) {
      return false;
    }
    if (cursorCount != nodeData.cursorCount) {
      return false;
    }
    return firstCursorIndex == nodeData.firstCursorIndex;
  }

  @Override
  public int hashCode() {
    int result = token;
    result = 31 * result + (int) numberOfChildren;
    result = 31 * result + firstChildIndex;
    result = 31 * result + Long.hashCode(cursorCount);
    result = 31 * result + Long.hashCode(firstCursorIndex);
    return result;
  }

}