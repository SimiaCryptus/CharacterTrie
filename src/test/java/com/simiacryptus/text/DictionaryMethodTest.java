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

import com.simiacryptus.util.TableOutput;
import com.simiacryptus.util.io.CompressionUtil;
import com.simiacryptus.util.io.MarkdownNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.test.Shakespeare;
import com.simiacryptus.util.test.TestCategories;
import com.simiacryptus.util.test.TestDocument;
import com.simiacryptus.util.test.TweetSentiment;
import com.simiacryptus.util.test.WikiArticle;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The type Dictionary method test.
 */
public class DictionaryMethodTest {
  
  /**
   * Dictionaries tweets.
   *
   * @throws Exception the exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void dictionariesTweets() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      int modelCount = 10000;
      int testCount = 100;
      log.p("This notebook uses a variety of methods to generate compression dictionaries for a database of Tweets\n");
      test(log, () -> TweetSentiment.load().limit(modelCount + testCount), modelCount);
    }
  }
  
  /**
   * Dictionaries shakespeare.
   *
   * @throws Exception the exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void dictionariesShakespeare() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      int modelCount = 100;
      int testCount = 100;
      log.p("This notebook uses a variety of methods to generate compression dictionaries for a database of Shakespeare text\n");
      test(log, () -> Shakespeare.load().limit(modelCount + testCount), modelCount);
    }
  }
  
  /**
   * Dictionaries wiki.
   *
   * @throws Exception the exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void dictionariesWiki() throws Exception {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      int modelCount = 100;
      int testCount = 100;
      log.p("This notebook uses a variety of methods to generate compression dictionaries for a database of Wikipedia articles\n");
      test(log, () -> WikiArticle.ENGLISH.stream().limit(modelCount + testCount), modelCount);
    }
  }
  
  private void test(NotebookOutput log, Supplier<Stream<? extends TestDocument>> source, int modelCount) {
    CharTrieIndex baseTree = new CharTrieIndex();
    source.get().limit(modelCount).forEach(txt -> baseTree.addDocument(txt.getText()));
    Map<String, Compressor> compressors = new LinkedHashMap<>();
    addCompressors(log, compressors, baseTree, 4, 2, 3);
    addCompressors(log, compressors, baseTree, 5, 2, 3);
    addCompressors(log, compressors, baseTree, 6, 2, 3);
    Stream<TestDocument> limit = source.get().limit(modelCount).map(x -> x);
    addWordCountCompressor(log, compressors, limit.collect(Collectors.toList()));
    Compressor.addGenericCompressors(compressors);
    TableOutput output = Compressor.evalCompressor(source.get().skip(modelCount), compressors, true);
    //log.p(output.toTextTable());
    log.p(output.calcNumberStats().toTextTable());
  }
  
  private void addWordCountCompressor(NotebookOutput log, Map<String, Compressor> compressors, List<? extends TestDocument> content) {
    Map<String, Long> wordCounts = content.stream().flatMap(c -> Arrays.stream(c.getText().replaceAll("[^\\w\\s]", "").split(" +")))
      .map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
    String dictionary = wordCounts.entrySet().stream()
      .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
        .thenComparing(Comparator.comparingLong(e -> -e.getKey().length())))
      .map(x -> x.getKey()).reduce((a, b) -> a + " " + b).get().substring(0, 8 * 1024);
    String key = "LZ8k_commonWords";
    int dictSampleSize = 512;
    log.p("Common Words Dictionary %s: %s...\n", key, dictionary.length() > dictSampleSize ? (dictionary.substring(0, dictSampleSize) + "...") : dictionary);
    compressors.put(key, new Compressor() {
      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeLZ(text, dictionary);
      }
      
      @Override
      public String uncompress(byte[] data) {
        return CompressionUtil.decodeLZToString(data, dictionary);
      }
    });
  }
  
  private void addCompressors(NotebookOutput log, Map<String, Compressor> compressors, CharTrieIndex baseTree, final int dictionary_context, final int dictionary_lookahead, int model_minPathWeight) {
    CharTrie dictionaryTree = baseTree.copy().index(dictionary_context + dictionary_lookahead, model_minPathWeight);
    String genDictionary = dictionaryTree.copy().getGenerator().generateDictionary(8 * 1024, dictionary_context, "", dictionary_lookahead, true);
    String keyDictionary = String.format("LZ8k_%s_%s_%s_generateDictionary", dictionary_context, dictionary_lookahead, model_minPathWeight);
    int dictSampleSize = 512;
    log.p("Adding Compressor %s: %s...\n", keyDictionary, genDictionary.length() > dictSampleSize ? (genDictionary.substring(0, dictSampleSize) + "...") : genDictionary);
    compressors.put(keyDictionary, new Compressor() {
      
      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeLZ(text, genDictionary);
      }
      
      @Override
      public String uncompress(byte[] data) {
        return CompressionUtil.decodeLZToString(data, genDictionary);
      }
    });
    String genMarkov = dictionaryTree.copy().getGenerator().generateMarkov(8 * 1024, dictionary_context, "");
    String keyMarkov = String.format("LZ8k_%s_%s_%s_generateMarkov", dictionary_context, dictionary_lookahead, model_minPathWeight);
    log.p("Adding Compressor %s: %s...\n", keyMarkov, genMarkov.length() > dictSampleSize ? (genMarkov.substring(0, dictSampleSize) + "...") : genMarkov);
    compressors.put(keyMarkov, new Compressor() {
      
      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeLZ(text, genMarkov);
      }
      
      @Override
      public String uncompress(byte[] data) {
        return CompressionUtil.decodeLZToString(data, genMarkov);
      }
    });
  }
  
  
}
