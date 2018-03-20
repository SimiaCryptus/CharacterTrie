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

import com.simiacryptus.util.io.CompressionUtil;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The enum Language model.
 */
public enum LanguageModel {
  /**
   * English language model.
   */
  English("English.trie"),
  /**
   * French language model.
   */
  French("French.trie"),
  /**
   * German language model.
   */
  German("German.trie");
  
  private final String resource;
  private volatile CharTrie trie;
  
  LanguageModel(String resource) {
    this.resource = resource;
  }
  
  /**
   * Match language model.
   *
   * @param text the text
   * @return the language model
   */
  public static LanguageModel match(String text) {
    return Arrays.stream(LanguageModel.values()).min(Comparator.comparing(
      model -> model.getTrie().getCodec().encodePPM(text, 2).bitLength
    )).get();
  }
  
  /**
   * Gets trie.
   *
   * @return the trie
   */
  public CharTrie getTrie() {
    if (null == trie) {
      synchronized (this) {
        if (null == trie) {
          final byte[] bytes;
          try {
            bytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream(resource));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          trie = new ConvolutionalTrieSerializer().deserialize(CompressionUtil.decodeLZ(bytes));
        }
      }
    }
    return trie;
  }
  
}
