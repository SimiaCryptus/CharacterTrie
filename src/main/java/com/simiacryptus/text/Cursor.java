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

public @com.simiacryptus.ref.lang.RefAware
class Cursor {
  final CursorData data;
  private final CharTrieIndex charTrieIndex;
  private final short depth;

  public Cursor(CharTrieIndex charTrieIndex, CursorData data, short depth) {
    this.charTrieIndex = charTrieIndex;
    this.data = data;
    this.depth = depth;
  }

  public CharSequence getDocument() {
    return this.charTrieIndex.documents.get(data.documentId);
  }

  public int getDocumentId() {
    return data.documentId;
  }

  public int getPosition() {
    return data.position + depth - 1;
  }

  public char getToken() {
    int index = getPosition();
    CharSequence document = getDocument();
    return index >= document.length() ? NodewalkerCodec.END_OF_STRING : document.charAt(index);
  }

  public boolean hasNext() {
    return (getPosition() + 1) < getDocument().length();
  }

  public Cursor next() {
    return new Cursor(this.charTrieIndex, data, (short) (depth + 1));
  }
}