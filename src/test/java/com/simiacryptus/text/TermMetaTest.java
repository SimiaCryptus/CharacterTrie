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

import com.simiacryptus.util.test.EnglishWords;
import com.simiacryptus.util.test.TestDocument;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

public class TermMetaTest {
  int testCount = 1000;
  int modelCount = 15000;

  @Nonnull
  protected Stream<? extends TestDocument> source() {
    return EnglishWords.load().limit(modelCount + testCount);
  }

  //  /**
  //   * Calc compressor ppm.
  //   *
  //   * @throws Exception the exception
  //   */
  //  @Test
  //  @Category(TestCategories.Report.class)
  //  public void calcCompressorPPM() throws Exception {
  //    NotebookOutput log = MarkdownNotebookOutput.get(this);
  //    CharTrieIndex baseTree = new CharTrieIndex();
  //    log.p("Preparing %s documents", modelCount);
  //    source().limit(modelCount).forEach(txt -> {
  //      //com.simiacryptus.ref.wrappers.System.p.println(String.format("Adding %s", txt.title));
  //      baseTree.addDocument(txt.getText());
  //    });
  //    log.p("Indexing %s KB of documents", baseTree.getIndexedSize() / 1024);
  //
  //    Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
  //
  //    int model_minPathWeight = 1;
  //    for (int ppmModelDepth : Arrays.asList(8, 9, 10, 11, 12)) {
  //      for (int encodingContext : Arrays.asList(0, 1, 2, 3, 4, 5)) {
  //        CharTrie ppmTree = baseTree.copy().index(ppmModelDepth, model_minPathWeight);
  //        CharSequence name = String.format("PPM%s_%s", encodingContext, ppmModelDepth);
  //        compressors.put(name, Compressor.buildPPMCompressor(ppmTree, encodingContext));
  //      }
  //    }
  //
  //    TableOutput output = Compressor.evalCompressor(source().skip(modelCount), compressors, true);
  //    //log.p(output.toTextTable());
  //    log.p(output.calcNumberStats().toCSV(true));
  //    log.close();
  //  }
}
