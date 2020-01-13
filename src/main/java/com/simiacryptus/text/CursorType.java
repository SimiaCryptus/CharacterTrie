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
import com.simiacryptus.util.data.SerialType;

import java.nio.ByteBuffer;

class CursorType implements SerialType<CursorData> {

  static CursorType INSTANCE = new CursorType();

  @Override
  public int getSize() {
    return 8;
  }

  @Override
  public CursorData read(ByteBuffer input) {
    return new CursorData(input.getInt(), input.getInt());
  }

  @Override
  public void write(ByteBuffer output, CursorData value) {
    output.putInt(value.documentId);
    output.putInt(value.position);
  }
}