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

@com.simiacryptus.ref.lang.RefAware
class CursorData {
  int documentId;
  int position;

  public CursorData(int documentId, int position) {
    this.documentId = documentId;
    this.position = position;
  }

  public CursorData setDocumentId(int documentId) {
    this.documentId = documentId;
    return this;
  }

  public CursorData setPosition(int position) {
    this.position = position;
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

    CursorData that = (CursorData) o;

    if (documentId != that.documentId) {
      return false;
    }
    return position == that.position;
  }

  @Override
  public int hashCode() {
    int result = documentId;
    result = 31 * result + position;
    return result;
  }

}