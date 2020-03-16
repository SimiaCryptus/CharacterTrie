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

import com.simiacryptus.notebook.MarkdownNotebookOutput;
import com.simiacryptus.notebook.NotebookOutput;
import com.simiacryptus.notebook.TableOutput;
import com.simiacryptus.util.test.TestDocument;
import com.simiacryptus.util.test.WikiArticle;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class ModelClusterTest {

  public static final File outPath = new File("src/site/resources/");
  public static final URL outBaseUrl = TrieTest.getUrl("https://simiacryptus.github.io/utilities/java-util/");

  public abstract int getModelCount();

  @Test
  @Tag("ResearchCode")
  public void clusterSharedDictionariesLZ() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(new File("clusterSharedDictionariesLZ"))) {

      int dictionary_context = 7;
      int model_minPathWeight = 3;
      int dictionary_lookahead = 2;
      AtomicInteger index = new AtomicInteger(0);
      Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
      source().parallel().limit(getModelCount()).forEach(text -> {
        CharTrieIndex baseTree = new CharTrieIndex();
        baseTree.addDocument(text.getText());
        CharTrie dictionaryTree = baseTree.copy().index(dictionary_context + dictionary_lookahead, model_minPathWeight);
        int i = index.incrementAndGet();
        compressors.put(String.format("LZ_%s", i), new Compressor() {
          @Nonnull
          String dictionary = dictionaryTree.copy().getGenerator().generateDictionary(8 * 1024, dictionary_context, "",
              dictionary_lookahead, true);

          @Nonnull
          @Override
          public byte[] compress(String text) {
            return CompressionUtil.encodeLZ(text, dictionary);
          }

          @Nonnull
          @Override
          public CharSequence uncompress(@Nonnull byte[] data) {
            return CompressionUtil.decodeLZToString(data, dictionary);
          }
        });

        compressors.put(String.format("LZ_raw_%s", i), new Compressor() {
          @Nonnull
          String dictionary = text.getText();

          @Nonnull
          @Override
          public byte[] compress(String text) {
            return CompressionUtil.encodeLZ(text, dictionary);
          }

          @Nonnull
          @Override
          public CharSequence uncompress(@Nonnull byte[] data) {
            return CompressionUtil.decodeLZToString(data, dictionary);
          }
        });
      });

      TableOutput output = Compressor.evalCompressorCluster(source().skip(getModelCount()), compressors, true);
      log.p(output.toCSV(true));
      log.p(output.calcNumberStats().toCSV(true));
      log.close();
      String outputDirName = String.format("cluster_%s_LZ/", getClass().getSimpleName());
      output.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
    }
  }

  @Test
  @Tag("ResearchCode")
  public void calcCompressorPPM() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(new File("calcCompressorPPM"))) {
      int ppmModelDepth = 6;
      int model_minPathWeight = 3;
      AtomicInteger index = new AtomicInteger(0);
      int encodingContext = 2;

      log.p("Generating Compressor Models");
      Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
      source().parallel().limit(getModelCount()).forEach(text -> {
        CharTrieIndex tree = new CharTrieIndex();
        tree.addDocument(text.getText());
        tree = tree.index(ppmModelDepth, model_minPathWeight);
        CharSequence name = String.format("PPM_%s", index.incrementAndGet());
        Compressor ppmCompressor = Compressor.buildPPMCompressor(tree, encodingContext);
        synchronized (compressors) {
          compressors.put(name, ppmCompressor);
        }
        log.p("Completed Model %s", name);
      });

      log.p("Calculating Metrics Table");
      TableOutput output = Compressor.evalCompressorCluster(source().skip(getModelCount()), compressors, true);
      log.p(output.calcNumberStats().toCSV(true));
      String outputDirName = String.format("cluster_%s_PPM/", getClass().getSimpleName());
      output.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
    }
  }

  @Test
  @Disabled
  @Tag("ResearchCode")
  public void calcEntropyPPM() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(new File("calcEntropyPPM"))) {
      int ppmModelDepth = 6;
      int model_minPathWeight = 3;
      AtomicInteger index = new AtomicInteger(0);
      int encodingContext = 2;

      log.p("Generating Compressor Models");
      Map<CharSequence, Function<TestDocument, Double>> compressors = new LinkedHashMap<>();
      source().parallel().limit(getModelCount()).forEach(text -> {
        CharTrieIndex tree = new CharTrieIndex();
        tree.addDocument(text.getText());
        tree = tree.index(ppmModelDepth, model_minPathWeight);
        CharSequence name = String.format("ENT_%s", index.incrementAndGet());
        TextAnalysis analysis = tree.getAnalyzer();
        Function<TestDocument, Double> ppmCompressor = t -> analysis.entropy(t.getText());
        synchronized (compressors) {
          compressors.put(name, ppmCompressor);
        }
        log.p("Completed Model %s", name);
      });

      log.p("Calculating Metrics Table");
      TableOutput output = Compressor.evalCluster(source().skip(getModelCount()), compressors, true);
      log.p(output.calcNumberStats().toCSV(true));
      String outputDirName = String.format("cluster_%s_Entropy/", getClass().getSimpleName());
      output.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
    }
  }

  @Nonnull
  protected abstract Stream<? extends TestDocument> source();

  public static class Wikipedia extends ModelClusterTest {
    int testCount = 1000;

    @Override
    public int getModelCount() {
      return 20;
    }

    @Nonnull
    @Override
    protected Stream<? extends TestDocument> source() {
      return WikiArticle.ENGLISH.stream().filter(wikiArticle -> {
        int kb = wikiArticle.getText().length() / 1024;
        return kb > 50 && kb < 150;
      }).limit(getModelCount() + testCount);
    }
  }

}
