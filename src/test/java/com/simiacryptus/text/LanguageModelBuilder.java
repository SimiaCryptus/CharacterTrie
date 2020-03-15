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
//import com.simiacryptus.notebook.MarkdownNotebookOutput;
//import com.simiacryptus.notebook.NotebookOutput;
//import com.simiacryptus.util.test.TestCategories;
//import com.simiacryptus.util.test.WikiArticle;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Tag;
//
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
///**
// * The type Language model builder.
// */
//public class LanguageModelBuilder {
//
//  private final int trainingSize = 1000;
//  private final int minWeight = 0;
//  private final int maxLevels = 4;
//  private final int minArticleSize = 4 * 1024;
//
//  private static void print(CharTrie trie) {
//    com.simiacryptus.ref.wrappers.System.out.println("Total Indexed Document (KB): " + trie.getIndexedSize() / 1024);
//    com.simiacryptus.ref.wrappers.System.out.println("Total Node Count: " + trie.getNodeCount());
//    com.simiacryptus.ref.wrappers.System.out.println("Total Index Memory Size (KB): " + trie.getMemorySize() / 1024);
//  }
//
//  /**
//   * Build language models.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Tag("Report")
//  public void buildLanguageModels() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get("buildLanguageModels")) {
//      process(log, "English", WikiArticle.ENGLISH.stream());
//      process(log, "French", WikiArticle.FRENCH.stream());
//      process(log, "German", WikiArticle.GERMAN.stream());
//    }
//  }
//
//  private void process(NotebookOutput log, String languageName, Stream<WikiArticle> load) {
//    log.p("\n\n");
//    log.h2(languageName);
//    CharTrie trie = log.code(() -> {
//      List<CharSequence> data = load.map(x -> x.getText()).filter(x -> x.length() > minArticleSize)
//        .skip(100)
//        .map(str -> str.replaceAll("\\{\\{.*\\}\\}", ""))
//        .map(str -> str.replaceAll("\\[\\[.*\\]\\]", ""))
//        .map(str -> str.replaceAll("\\[[^\\]]*\\]", ""))
//        .map(str -> str.replaceAll("\\{[^\\}]*\\}", ""))
//        .map(str -> str.replaceAll("\\<[^\\>]*\\>", ""))
//        .limit(trainingSize).collect(Collectors.toList());
//      CharTrie charTrie = CharTrieIndex.indexFulltext(data, maxLevels, minWeight);
//      print(charTrie);
//      return charTrie;
//    });
//    log.code(() -> {
//      try (FileOutputStream fos = new FileOutputStream("src/main/resources/" + languageName + ".trie")) {
//        byte[] serialized = CompressionUtil.encodeLZ(new ConvolutionalTrieSerializer().serialize(trie));
//        com.simiacryptus.ref.wrappers.System.out.println(String.format("Serialized tree to %s kb", serialized.length / 1024));
//        fos.write(serialized);
//      } catch (IOException e) {
//        throw new RuntimeException(e);
//      }
//    });
//  }
//
//
//}
