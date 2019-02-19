///*
// * Copyright (c) 2018 by Andrew Charneski.
// *
// * The author licenses this file to you under the
// * Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance
// * with the License.  You may obtain a copy
// * of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing,
// * software distributed under the License is distributed on an
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// * KIND, either express or implied.  See the License for the
// * specific language governing permissions and limitations
// * under the License.
// */
//
//package com.simiacryptus.text;
//
//import com.simiacryptus.notebook.TableOutput;
//import com.simiacryptus.notebook.MarkdownNotebookOutput;
//import com.simiacryptus.notebook.NotebookOutput;
//import com.simiacryptus.util.test.TestCategories;
//import com.simiacryptus.util.test.TestDocument;
//import com.simiacryptus.util.test.TweetSentiment;
//import com.simiacryptus.util.test.WikiArticle;
//import org.junit.Test;
//import org.junit.experimental.categories.Category;
//
//import java.io.File;
//import java.net.URL;
//import java.util.Arrays;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.stream.Stream;
//
///**
// * The type Model meta test.
// */
//public abstract class ModelMetaTest {
//
//  /**
//   * The constant outPath.
//   */
//  public static final File outPath = new File("src/site/resources/");
//  /**
//   * The constant outBaseUrl.
//   */
//  public static final URL outBaseUrl = TrieTest.getUrl("https://simiacryptus.github.io/utilities/java-util/");
//
//  /**
//   * Source stream.
//   *
//   * @return the stream
//   */
//  protected abstract Stream<? extends TestDocument> source();
//
//  /**
//   * Gets model count.
//   *
//   * @return the model count
//   */
//  public abstract int getModelCount();
//
//  /**
//   * Calc shared dictionaries lz.
//   *
//   * @throws Exception the exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void calcSharedDictionariesLZ() throws Exception {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      CharTrieIndex baseTree = new CharTrieIndex();
//      log.p("Preparing %s documents", getModelCount());
//      source().limit(getModelCount()).forEach(txt -> {
//        //System.p.println(String.format("Adding %s", txt.title));
//        baseTree.addDocument(txt.getText());
//      });
//      log.p("Indexing %s KB of documents", baseTree.getIndexedSize() / 1024);
//      Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
//
//      for (int dictionary_context : Arrays.asList(4, 5, 6)) {
//        int model_minPathWeight = 3;
//        int dictionary_lookahead = 2;
//        log.p("Generating dictionaries");
//        CharTrie dictionaryTree = baseTree.copy().index(dictionary_context + dictionary_lookahead, model_minPathWeight);
//
//        compressors.put(String.format("LZ8k_%s", dictionary_context), new Compressor() {
//          String dictionary = dictionaryTree.copy().getGenerator().generateDictionary(8 * 1024, dictionary_context, "", dictionary_lookahead, true);
//
//          @Override
//          public byte[] compress(String text) {
//            return CompressionUtil.encodeLZ(text, dictionary);
//          }
//
//          @Override
//          public CharSequence uncompress(byte[] data) {
//            return CompressionUtil.decodeLZToString(data, dictionary);
//          }
//        });
//      }
//
//      TableOutput output = Compressor.evalCompressor(source().skip(getModelCount()), compressors, true);
//      //log.p(output.toTextTable());
//      log.p(output.calcNumberStats().toCSV(true));
//    }
//  }
//
//  /**
//   * Calc shared dictionaries bz.
//   *
//   * @throws Exception the exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void calcSharedDictionariesBZ() throws Exception {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      CharTrieIndex baseTree = new CharTrieIndex();
//      log.p("Preparing %s documents", getModelCount());
//      source().limit(getModelCount()).forEach(txt -> {
//        //System.p.println(String.format("Adding %s", txt.title));
//        baseTree.addDocument(txt.getText());
//      });
//      log.p("Indexing %s KB of documents", baseTree.getIndexedSize() / 1024);
//      Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
//
//      for (int dictionary_context : Arrays.asList(4, 6, 8)) {
//        int model_minPathWeight = 3;
//        int dictionary_lookahead = 2;
//        log.p("Generating dictionaries");
//        CharTrie dictionaryTree = baseTree.copy().index(dictionary_context + dictionary_lookahead, model_minPathWeight);
//
//        compressors.put(String.format("BZ64k_%s", dictionary_context), new Compressor() {
//          String dictionary = dictionaryTree.copy().getGenerator().generateDictionary(64 * 1024, dictionary_context, "", dictionary_lookahead, true);
//
//          @Override
//          public byte[] compress(String text) {
//            return CompressionUtil.encodeBZ(text, dictionary);
//          }
//
//          @Override
//          public CharSequence uncompress(byte[] data) {
//            return CompressionUtil.decodeBZ(data, dictionary);
//          }
//        });
//      }
//      TableOutput output = Compressor.evalCompressor(source().skip(getModelCount()), compressors, true);
//      //log.p(output.toTextTable());
//      log.p(output.calcNumberStats().toCSV(true));
//    }
//  }
//
//  /**
//   * Calc compressor ppm.
//   *
//   * @throws Exception the exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void calcCompressorPPM() throws Exception {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      CharTrieIndex baseTree = new CharTrieIndex();
//      log.p("Preparing %s documents", getModelCount());
//      source().limit(getModelCount()).forEach(txt -> {
//        //System.p.println(String.format("Adding %s", txt.title));
//        baseTree.addDocument(txt.getText());
//      });
//      log.p("Indexing %s KB of documents", baseTree.getIndexedSize() / 1024);
//
//      Map<CharSequence, Compressor> compressors = new LinkedHashMap<>();
//
//      int model_minPathWeight = 1;
//      for (int ppmModelDepth : Arrays.asList(4, 6, 8)) {
//        for (int encodingContext : Arrays.asList(1, 2, 3)) {
//          CharTrie ppmTree = baseTree.copy().index(ppmModelDepth, model_minPathWeight);
//          CharSequence name = String.format("PPM%s_%s", encodingContext, ppmModelDepth);
//          compressors.put(name, Compressor.buildPPMCompressor(ppmTree, encodingContext));
//        }
//      }
//
//      TableOutput output = Compressor.evalCompressor(source().skip(getModelCount()), compressors, true);
//      //log.p(output.toTextTable());
//      log.p(output.calcNumberStats().toCSV(true));
//    }
//  }
//
//  /**
//   * The type Tweets.
//   */
//  public static class Tweets extends ModelMetaTest {
//    /**
//     * The Test count.
//     */
//    int testCount = 100;
//
//    @Override
//    protected Stream<? extends TestDocument> source() {
//      return TweetSentiment.load().limit(getModelCount() + testCount);
//    }
//
//    @Override
//    public int getModelCount() {
//      return 100000;
//    }
//
//  }
//
//  /**
//   * The type Wikipedia.
//   */
//  public static class Wikipedia extends ModelMetaTest {
//    /**
//     * The Test count.
//     */
//    int testCount = 100;
//
//    @Override
//    protected Stream<? extends TestDocument> source() {
//      return WikiArticle.ENGLISH.stream().limit(getModelCount() + testCount);
//    }
//
//    @Override
//    public int getModelCount() {
//      return 100;
//    }
//  }
//
//
//}
