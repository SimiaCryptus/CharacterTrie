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

import com.simiacryptus.util.data.SerialType;

import java.nio.ByteBuffer;

@com.simiacryptus.ref.lang.RefAware
class NodeType implements SerialType<NodeData> {

  static NodeType INSTANCE = new NodeType();

  @Override
  public int getSize() {
    return 24;
  }

  @Override
  public NodeData read(ByteBuffer input) {
    return new NodeData(input.getChar(), input.getShort(), input.getInt(), input.getLong(), input.getLong());
  }

  @Override
  public void write(ByteBuffer output, NodeData value) {
    output.putChar(value.token);
    output.putShort(value.numberOfChildren);
    output.putInt(value.firstChildIndex);
    output.putLong(value.cursorCount);
    output.putLong(value.firstCursorIndex);
  }
}