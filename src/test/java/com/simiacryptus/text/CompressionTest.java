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
import com.simiacryptus.ref.lang.RefAware;
import com.simiacryptus.ref.wrappers.RefAssert;
import com.simiacryptus.ref.wrappers.RefLinkedHashMap;
import com.simiacryptus.ref.wrappers.RefMap;
import com.simiacryptus.ref.wrappers.RefStream;
import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.test.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.function.Supplier;

public @RefAware
class CompressionTest {

  static void addSharedDictionaryCompressors(RefMap<CharSequence, Compressor> compressors,
                                             final CharTrieIndex baseTree, final int dictionary_lookahead, final int dictionary_context,
                                             int model_minPathWeight) {
    CharTrie dictionaryTree = baseTree.copy().index(dictionary_context + dictionary_lookahead, model_minPathWeight);
    compressors.put("LZ8k", new Compressor() {
      String dictionary = dictionaryTree.copy().getGenerator().generateDictionary(8 * 1024, dictionary_context, "",
          dictionary_lookahead, true);

      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeLZ(text, dictionary);
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return CompressionUtil.decodeLZToString(data, dictionary);
      }
    });

    compressors.put("BZ64k", new Compressor() {
      String dictionary = dictionaryTree.copy().getGenerator().generateDictionary(64 * 1024, dictionary_context, "",
          dictionary_lookahead, true);

      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeBZ(text, dictionary);
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return CompressionUtil.decodeBZ(data, dictionary);
      }
    });
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testPPMCompression_Basic() {
    CharTrieIndex tree = new CharTrieIndex();
    tree.addDocument("ababababab");
    tree = tree.index(2, 0);
    NodewalkerCodec codec = tree.getCodec();
    codec.setVerbose(System.out);
    String txt = "ab ba";
    Bits encoded = codec.encodePPM(txt, 1);
    CharSequence decoded = codec.decodePPM(encoded.getBytes(), 1);
    RefAssert.assertEquals(txt, decoded);
  }

  @Test
  @Category(TestCategories.ResearchCode.class)
  public void testPPMCompression_Tweets() {
    long articleCount = 1000;
    long modelCount = 10000;
    int encodingContext = 3;
    int modelDepth = 9;

    final CharTrieIndex tree = new CharTrieIndex();
    TweetSentiment.load().skip(articleCount).limit(modelCount).map(t -> t.getText())
        .forEach(txt -> tree.addDocument(txt));
    CharTrie modelTree = tree.index(modelDepth, 0);
    NodewalkerCodec codec = modelTree.getCodec();
    TweetSentiment.load().limit(articleCount).map(t -> t.getText()).forEach(txt -> {
      try {
        Bits encoded = codec.encodePPM(txt, encodingContext);
        CharSequence decoded = codec.decodePPM(encoded.getBytes(), encodingContext);
        RefAssert.assertEquals(txt, decoded);
        System.out
            .println(String.format("Verified \"%s\" - %s chars -> %s bits", txt, txt.length(), encoded.bitLength));
      } catch (Throwable e) {
        synchronized (modelTree) {
          System.out.println(String.format("Error encoding \"%s\" - %s", txt, e.getMessage()));
          try {
            NodewalkerCodec codec2 = codec.setVerbose(System.out);
            Bits encoded = codec2.encodePPM(txt, encodingContext);
            CharSequence decoded = codec2.decodePPM(encoded.getBytes(), encodingContext);
            RefAssert.assertEquals(txt, decoded);
            System.out
                .println(String.format("Verified \"%s\" - %s chars -> %s bits", txt, txt.length(), encoded.bitLength));
            throw e;
          } catch (Throwable e2) {
            throw e2;
            //System.p.println(String.format("Error encoding \"%s\" - %s", txt, e2.getMessage()));
            //throw new RuntimeException(e);
          }
        }
      }
    });
  }

  @Test
  @Category(TestCategories.Report.class)
  public void calcTweetCompression() throws Exception {
    int ppmModelDepth = 9;
    int model_minPathWeight = 3;
    int dictionary_lookahead = 2;
    int dictionary_context = 6;
    int encodingContext = 2;
    int modelCount = 10000;
    int testCount = 100;
    Supplier<RefStream<? extends TestDocument>> source = () -> TweetSentiment.load()
        .limit(modelCount + testCount);

    try (NotebookOutput log = MarkdownNotebookOutput.get("calcTweetCompression")) {
      RefMap<CharSequence, Compressor> compressors = buildCompressors(source,
          ppmModelDepth, model_minPathWeight, dictionary_lookahead, dictionary_context, encodingContext, modelCount);
      TableOutput output = Compressor.evalCompressor(source.get().skip(modelCount), compressors, true);
      //log.p(output.toTextTable());
      log.p(output.calcNumberStats().toCSV(true));
    }
  }

  @Test
  @Category(TestCategories.Report.class)
  public void calcTermCompression() throws Exception {
    int ppmModelDepth = 10;
    int model_minPathWeight = 0;
    int dictionary_lookahead = 2;
    int dictionary_context = 6;
    int encodingContext = 3;
    int modelCount = 15000;
    int testCount = 100;
    Supplier<RefStream<? extends TestDocument>> source = () -> EnglishWords.load()
        .limit(modelCount + testCount);
    NotebookOutput log = MarkdownNotebookOutput.get("calcTermCompression");
    RefMap<CharSequence, Compressor> compressors = buildCompressors(source, ppmModelDepth,
        model_minPathWeight, dictionary_lookahead, dictionary_context, encodingContext, modelCount);
    TableOutput output = Compressor.evalCompressor(source.get().skip(modelCount), compressors, true);
    //log.p(output.toTextTable());
    log.p(output.calcNumberStats().toCSV(true));
    log.close();
  }

  @Test
  @Category(TestCategories.Report.class)
  public void calcWikiCompression() throws Exception {
    int ppmModelDepth = 9;
    int model_minPathWeight = 3;
    int dictionary_lookahead = 2;
    int dictionary_context = 6;
    int encodingContext = 2;
    int modelCount = 100;
    int testCount = 100;
    Supplier<RefStream<? extends TestDocument>> source = () -> WikiArticle.ENGLISH
        .stream().filter(x -> x.getText().length() > 8 * 1024).limit(modelCount + testCount);

    NotebookOutput log = MarkdownNotebookOutput.get("calcWikiCompression");
    RefMap<CharSequence, Compressor> compressors = buildCompressors(source, ppmModelDepth,
        model_minPathWeight, dictionary_lookahead, dictionary_context, encodingContext, modelCount);
    TableOutput output = Compressor.evalCompressor(source.get().skip(modelCount), compressors, true);
    //log.p(output.toTextTable());
    log.p(output.calcNumberStats().toCSV(true));
    log.close();
  }

  protected RefMap<CharSequence, Compressor> buildCompressors(
      Supplier<RefStream<? extends TestDocument>> source, int ppmModelDepth,
      int model_minPathWeight, final int dictionary_lookahead, final int dictionary_context, final int encodingContext,
      int modelCount) {
    RefMap<CharSequence, Compressor> compressors = new RefLinkedHashMap<>();
    Compressor.addGenericCompressors(compressors);
    System.out.println(String.format("Preparing %s documents", modelCount));
    CharTrieIndex baseTree = new CharTrieIndex();
    source.get().limit(modelCount).forEach(txt -> {
      //System.p.println(String.format("Adding %s", txt.title));
      baseTree.addDocument(txt.getText());
    });
    System.out.println(String.format("Indexing %s KB of documents", baseTree.getIndexedSize() / 1024));
    baseTree.index(ppmModelDepth, model_minPathWeight);
    System.out.println(String.format("Generating dictionaries"));
    addSharedDictionaryCompressors(compressors, baseTree, dictionary_lookahead, dictionary_context,
        model_minPathWeight);
    compressors.put("PPM" + encodingContext, Compressor.buildPPMCompressor(baseTree, encodingContext));
    return compressors;
  }

}
