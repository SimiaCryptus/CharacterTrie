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

package com.simiacryptus.util.test;

import com.simiacryptus.text.CompressionUtil;

/**
 * The type Test document.
 */
public class TestDocument {

  private final CharSequence title;
  private final byte[] text;

  /**
   * Instantiates a new Test document.
   *
   * @param title the title
   * @param text  the text
   */
  public TestDocument(CharSequence title, CharSequence text) {
    this.title = title;
    this.text = CompressionUtil.encodeLZ(text);
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer(getClass().getSimpleName() + "{");
    sb.append("title='").append(getTitle()).append('\'');
    sb.append('}');
    return sb.toString();
  }

  /**
   * Gets text.
   *
   * @return the text
   */
  public String getText() {
    return CompressionUtil.decodeLZToString(text);
  }

  /**
   * Gets title.
   *
   * @return the title
   */
  public CharSequence getTitle() {
    return title;
  }
}
