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

import com.simiacryptus.ref.lang.RefUtil;
import com.simiacryptus.ref.wrappers.RefArrays;
import com.simiacryptus.ref.wrappers.RefComparator;
import org.apache.commons.compress.utils.IOUtils;

import javax.annotation.Nonnull;
import java.io.IOException;

public enum LanguageModel {
  English("English.trie"), French("French.trie"), German("German.trie");

  private final String resource;
  private volatile CharTrie trie;

  LanguageModel(String resource) {
    this.resource = resource;
  }

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

  @Nonnull
  public static LanguageModel match(@Nonnull String text) {
    return RefUtil.get(RefArrays.stream(LanguageModel.values())
        .min(RefComparator
            .comparing(model -> model.getTrie().getCodec().encodePPM(text, 2).bitLength)));
  }

}
