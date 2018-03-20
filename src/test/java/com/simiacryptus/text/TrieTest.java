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
import com.simiacryptus.util.data.DoubleStatistics;
import com.simiacryptus.util.io.CompressionUtil;
import com.simiacryptus.util.test.TestCategories;
import com.simiacryptus.util.test.TweetSentiment;
import com.simiacryptus.util.test.WikiArticle;
import junit.framework.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The type Trie test.
 */
public class TrieTest {
  /**
   * The constant outPath.
   */
  public static final File outPath = new File("src/site/resources/");
  /**
   * The constant outBaseUrl.
   */
  public static final URL outBaseUrl = getUrl("https://simiacryptus.github.io/utilities/java-util/");
  
  /**
   * Gets url.
   *
   * @param url the url
   * @return the url
   */
  public static URL getUrl(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
  
  private static void testRow(int maxLevels, int minWeight, Stream<String> documents) {
    CharTrieIndex tree = new CharTrieIndex();
    long startTime = System.currentTimeMillis();
    documents.forEach(i -> tree.addDocument(i));
    tree.index(maxLevels, minWeight);
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println(
      String.format("%s\t%s\t%s KB\t%s sec\t%s KB\t%s KB", maxLevels, minWeight, tree.getIndexedSize() / 1024,
        elapsed / 1000., tree.getMemorySize() / 1024, tree.truncate().getMemorySize() / 1024));
  }
  
  private static Map<String, Object> evaluateDictionary(List<String> sentances, String dictionary, Map<String, Object> map) {
    Arrays.asList(1, 4, 16, 32).stream().forEach(k -> {
      DoubleStatistics statistics = sentances.stream().map(line -> {
        int length0 = CompressionUtil.encodeLZ(line, "").length;
        int lengthK = CompressionUtil.encodeLZ(line, dictionary.substring(0, Math.min(k * 1024, dictionary.length()))).length;
        return (length0 - lengthK) * 1.0;
      }).collect(DoubleStatistics.COLLECTOR);
      map.put(String.format("%sk.sum", k), (int) statistics.getSum() / 1024);
      map.put(String.format("%sk.avg", k), (int) statistics.getAverage());
      map.put(String.format("%sk.stddev", k), (int) statistics.getStandardDeviation());
    });
    return map;
  }
  
  /**
   * Test functionality.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testFunctionality() {
    CharTrieIndex tree = new CharTrieIndex();
    tree.addDocument("a quick brown fox jumped over the lazy dog");
    tree.addDocument("this is a test. this is only a test. - nikola tesla");
    tree.index(3);
    Assert.assertEquals(8, tree.traverse("t").getCursorCount());
    Assert.assertEquals("t", tree.traverse("t").getString());
    Assert.assertEquals("te", tree.traverse("te").getString());
    Assert.assertEquals(3, tree.traverse("te").getCursorCount());
    Assert.assertEquals(1, tree.traverse("dog").getCursorCount());
    Assert.assertEquals("dog", tree.traverse("dog").getString());
    Assert.assertEquals(1, tree.traverse("do").getCursorCount());
    Assert.assertEquals(6, tree.traverse("o").getCursorCount());
    Assert.assertEquals(3, tree.traverse("test").getCursorCount());
    Assert.assertEquals("tes", tree.traverse("test").getString());
    Assert.assertEquals(2, tree.traverse("this ").getCursorCount());
    Assert.assertEquals(1, tree.traverse(" dog").getCursorCount());
    Assert.assertEquals(1, tree.traverse("dog").getCursorCount());
    Assert.assertEquals(1, tree.traverse("a quick").getCursorCount());
  }
  
  /**
   * Test performance.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testPerformance() {
    CharTrieIndex tree = new CharTrieIndex();
    IntStream.range(0, 30000).parallel().forEach(i -> tree.addDocument(UUID.randomUUID().toString()));
    tree.index();
    System.out.println(String.format("tree.getIndexedSize = %s", tree.getIndexedSize()));
    System.out.println(String.format("tree.getMemorySize = %s", tree.getMemorySize()));
    System.out.println(String.format("tree.truncate.getMemorySize = %s", tree.truncate().getMemorySize()));
  }
  
  /**
   * Test performance matrix.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testPerformanceMatrix() {
    for (int count = 100; count < 50000; count *= 2) {
      for (int maxLevels = 1; maxLevels < 64; maxLevels = Math.max(maxLevels * 4, maxLevels + 1)) {
        for (int minWeight = 1; minWeight < 64; minWeight *= 4) {
          testRow(maxLevels, minWeight,
            IntStream.range(0, count).parallel().mapToObj(i -> UUID.randomUUID().toString()));
        }
      }
    }
    
  }
  
  /**
   * Test dictionary generation meta parameters.
   *
   * @throws IOException the io exception
   */
  @Test
  @Ignore
  @Category(TestCategories.ResearchCode.class)
  public void testDictionaryGenerationMetaParameters() {
    TableOutput output = new TableOutput();
    final String content;
    try (Scanner scanner = new Scanner(getClass().getClassLoader().getResourceAsStream("earthtomoon.txt"))) {
      content = scanner.useDelimiter("\\Z").next().replaceAll("[ \n\r\t]+", " ");
    }
    List<String> sentances = Arrays.stream(content.split("\\.+")).map(line -> line.trim() + ".")
      .filter(line -> line.length() > 12).collect(Collectors.toList());
    
    int size = 32 * 1024;
    int sampleLength = 80;
    
    Map<String, Long> wordCounts = Arrays.stream(content.replaceAll("[^\\w\\s]", "").split(" +")).map(s -> s.trim())
      .filter(s -> !s.isEmpty()).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
    
    {
      String commonTerms = wordCounts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
          .thenComparing(Comparator.comparingLong(e -> -e.getKey().length())))
        .map(x -> x.getKey()).reduce((a, b) -> a + " " + b).get().substring(0, size);
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("type", "CommonTerm");
      evaluateDictionary(sentances, commonTerms, map);
      map.put("sampleTxt", commonTerms.substring(0, sampleLength));
      output.putRow(map);
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    for (int encodingPenalty = -4; encodingPenalty < 4; encodingPenalty++) {
      int _encodingPenalty = encodingPenalty;
      String meritTerms = wordCounts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(
          e -> -e.getValue() * (e.getKey().length() - _encodingPenalty))
          .thenComparing(Comparator.comparingLong(e -> -e.getKey().length())))
        .map(x -> x.getKey()).reduce((a, b) -> a + " " + b).get().substring(0, size);
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("type", "MeritTerm");
      map.put("encodingPenalty", encodingPenalty);
      evaluateDictionary(sentances, meritTerms, map);
      map.put("sampleTxt", meritTerms.substring(0, sampleLength));
      output.putRow(map);
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    {
      String uncommonTerms = wordCounts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> e.getValue())
          .thenComparing(Comparator.comparingLong(e -> e.getKey().length())))
        .map(x -> x.getKey()).reduce((a, b) -> a + " " + b).get().substring(0, size);
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("type", "UncommonTerm,");
      evaluateDictionary(sentances, uncommonTerms, map);
      map.put("sampleTxt", uncommonTerms.substring(0, sampleLength));
      output.putRow(map);
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    CharTrieIndex tree = new CharTrieIndex();
    long startTime = System.currentTimeMillis();
    // sentances.stream().forEach(i->tree.addDocument(i));
    tree.addDocument(content);
    int maxLevels = 10;
    int minWeight = 0;
    tree.index(maxLevels, minWeight).truncate();
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println(String.format("Built index in time = %s sec", elapsed / 1000.));
    System.out.println(String.format("tree.getIndexedSize = %s KB", tree.getIndexedSize() / 1024));
    System.out.println(String.format("tree.getMemorySize = %s KB", tree.getMemorySize() / 1024));
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    for (int context = 0; context < maxLevels; context++) {
      CharTrie copy = tree.copy();
      for (int attempt = 0; attempt < 1; attempt++) {
        String dictionary = copy.getGenerator().generateMarkov(size, context, ".");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "generateMarkov");
        map.put("context", context);
        evaluateDictionary(sentances, dictionary, map);
        map.put("sampleTxt", dictionary.substring(0, Math.min(sampleLength, dictionary.length())));
        output.putRow(map);
      }
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    for (int lookahead = 0; lookahead < 3; lookahead++) {
      for (int context = 0; context < maxLevels - lookahead; context++) {
        for (int attempt = 0; attempt < 1; attempt++) {
          String dictionary = tree.copy().getGenerator().generateDictionary(size, context, ".", lookahead, true);
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("type", "generateDictionary1");
          map.put("context", context);
          map.put("lookahead", lookahead);
          evaluateDictionary(sentances, dictionary, map);
          map.put("sampleTxt", dictionary.substring(0, Math.min(sampleLength, dictionary.length())));
          output.putRow(map);
          System.gc();
        }
      }
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
    
    for (int lookahead = 0; lookahead < 3; lookahead++) {
      for (int context = 0; context < maxLevels - lookahead; context++) {
        String dictionary = tree.getGenerator().generateDictionary(size, context, ".", lookahead, false);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "generateDictionary2");
        map.put("context", context);
        map.put("lookahead", lookahead);
        evaluateDictionary(sentances, dictionary, map);
        map.put("sampleTxt", dictionary.substring(0, Math.min(sampleLength, dictionary.length())));
        output.putRow(map);
      }
    }
    
    System.out.println(output.toTextTable());
    output = new TableOutput();
  }
  
  /**
   * Test model metric.
   *
   * @throws IOException the io exception
   */
  @Test
  @Ignore
  @Category(TestCategories.ResearchCode.class)
  public void testModelMetric() {
    Map<String, String> content = new HashMap<>();
    for (String name : Arrays.asList("earthtomoon.txt", "20000leagues.txt", "macbeth.txt", "randj.txt")) {
      content.put(name, new Scanner(getClass().getClassLoader().getResourceAsStream(name)).useDelimiter("\\Z").next()
        .replaceAll("[ \n\r\t]+", " "));
    }
    
    String characterSet = content.values().stream().flatMapToInt(s -> s.chars()).distinct()
      .mapToObj(c -> new String(Character.toChars(c))).sorted().collect(Collectors.joining(""));
    System.out.println("Character Set:" + characterSet);
    int maxLevels = 5;
    int minWeight = 1;
    double smoothness = 1.0;
    int sampleSize = 64 * 1024;
    
    long startTime = System.currentTimeMillis();
    Map<String, CharTrie> trees = new HashMap<>();
    Map<String, String> dictionaries = new HashMap<>();
    for (Map.Entry<String, String> e : content.entrySet()) {
      CharTrieIndex tree = new CharTrieIndex();
      tree.addDocument(characterSet);
      tree.addDocument(e.getValue());
      tree.index(maxLevels, minWeight).truncate();
      trees.put(e.getKey(), tree);
      dictionaries.put(e.getKey(), tree.copy().getGenerator().generateDictionary(16 * 1024, 5, "", 1, true));
      System.out.println(
        String.format("Indexing %s; \ntree.getIndexedSize = %s KB", e.getKey(), tree.getIndexedSize() / 1024));
      System.out.println(String.format("tree.getMemorySize = %s KB", tree.getMemorySize() / 1024));
    }
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println(String.format("Built index in time = %s sec", elapsed / 1000.));
    
    System.out.println("\nMCMC Similarity Measures:");
    TableOutput output1 = new TableOutput();
    for (Map.Entry<String, CharTrie> ea : trees.entrySet()) {
      for (Map.Entry<String, CharTrie> eb : trees.entrySet()) {
        String str = ea.getValue().getGenerator().generateMarkov(sampleSize, maxLevels - 1, "");
        Double crossEntropy = eb.getValue().getAnalyzer().entropy(str) / str.length();
        HashMap<String, Object> map = new HashMap<>();
        map.put("source", ea.getKey());
        map.put("model", eb.getKey());
        map.put("crossEntropy", crossEntropy);
        output1.putRow(map);
      }
    }
    System.out.println(output1.toTextTable());
    
  }
  
  /**
   * Calc wiki coords.
   *
   * @throws Exception the exception
   */
  @Test
  @Ignore
  @Category(TestCategories.ResearchCode.class)
  public void calcWikiCoords() throws Exception {
    
    int minArticleLength = 8 * 1024;
    int maxLevels = 7;
    int minWeight = 1;
    int chunkSize = 256;
    int dictionaryLength = 32 * 1024;
    int dictionaryCount = 20;
    int articleCount = 1000;
    double selectivity = 0.1;
    
    Map<String, String> articles = WikiArticle.ENGLISH.stream().filter(x -> x.getText().length() > minArticleLength)
      .filter(x -> selectivity > Math.random()).limit(Math.max(articleCount, dictionaryCount))
      .collect(Collectors.toMap(d -> d.getTitle(), d -> d.getText()));
    
    String characterSet = articles.values().stream().flatMapToInt(s -> s.chars()).distinct()
      .mapToObj(c -> new String(Character.toChars(c))).sorted().collect(Collectors.joining(""));
    System.out.println("Character Set:" + characterSet);
    
    Stream<Map.Entry<String, String>> stream = articles.entrySet().stream().limit(dictionaryCount);
    Map<String, CharTrie> models = stream
      .collect(Collectors.toMap((Map.Entry<String, String> d) -> d.getKey(), (Map.Entry<String, String> d) -> {
        String article = d.getValue();
        String title = d.getKey();
        CharTrieIndex tree = new CharTrieIndex();
        tree.addDocument(characterSet);
        tree.addDocument(article);
        tree.index(maxLevels, minWeight).truncate();
        System.out.println(
          String.format("Indexing %s; \ntree.getIndexedSize = %s KB", title, tree.getIndexedSize() / 1024));
        System.out.println(String.format("tree.getMemorySize = %s KB", tree.getMemorySize() / 1024));
        return tree;
      }));
    Map<String, String> dictionaries = models.entrySet().stream()
      .collect(Collectors.toMap((Map.Entry<String, CharTrie> d) -> d.getKey(), (Map.Entry<String, CharTrie> d) -> {
        return d.getValue().copy().getGenerator().generateDictionary(dictionaryLength, 5, "", 1, true);
      }));
    
    TableOutput output = new TableOutput();
    articles.entrySet().stream().limit(articleCount).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()))
      .forEach((dataTitle, article) -> {
        HashMap<String, Object> map = new LinkedHashMap<>();
        map.put("dataTitle", dataTitle);
        dictionaries.forEach((modelTitle, dictionary) -> {
          int sumA = IntStream.range(0, article.length() / chunkSize)
            .mapToObj(i -> article.substring(i * chunkSize, Math.min(article.length(), (i + 1) * chunkSize)))
            .mapToInt(chunk -> CompressionUtil.encodeLZ(chunk, "").length).sum();
          int sumB = IntStream.range(0, article.length() / chunkSize)
            .mapToObj(i -> article.substring(i * chunkSize, Math.min(article.length(), (i + 1) * chunkSize)))
            .mapToInt(chunk -> CompressionUtil.encodeLZ(chunk, dictionary).length).sum();
          double bytes = (sumA - sumB) * 1.0 / sumA;
          map.put(modelTitle.replaceAll("[^01-9a-zA-Z]", "_"), bytes);
        });
        output.putRow(map);
      });
    System.out.println(output.toTextTable());
    String outputDirName = "wikiTopics/";
    output.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
  }
  
  /**
   * Calc tweet vectors.
   *
   * @throws Exception the exception
   */
  @Test
  @Ignore
  @Category(TestCategories.ResearchCode.class)
  public void calcTweetVectors() throws Exception {
    
    int minArticleLength = 8;
    int maxLevels = 7;
    int minWeight = 1;
    int chunkSize = 256;
    int dictionaryLength = 32 * 1024;
    int dictionaryCount = 20;
    int articleCount = 1000;
    double selectivity = 0.1;
    
    List<String> articles = TweetSentiment.load().filter(x -> x.getText().length() > minArticleLength)
      .filter(x -> selectivity > Math.random()).limit(Math.max(articleCount, dictionaryCount)).map(t -> t.getText())
      .collect(Collectors.toList());
    
    String characterSet = articles.stream().flatMapToInt(s -> s.chars()).distinct()
      .mapToObj(c -> new String(Character.toChars(c))).sorted().collect(Collectors.joining(""));
    System.out.println("Character Set:" + characterSet);
    
    Map<String, CharTrie> models = articles.stream().limit(dictionaryCount)
      .collect(Collectors.toMap((String d) -> d, (String text) -> {
        CharTrieIndex tree = new CharTrieIndex();
        tree.addDocument(characterSet);
        tree.addDocument(text);
        tree.index(maxLevels, minWeight).truncate();
        System.out
          .println(String.format("Indexing %s; \ntree.getIndexedSize = %s KB", text, tree.getIndexedSize() / 1024));
        System.out.println(String.format("tree.getMemorySize = %s KB", tree.getMemorySize() / 1024));
        return tree;
      }));
    Map<String, String> dictionaries = models.entrySet().stream()
      .collect(Collectors.toMap((Map.Entry<String, CharTrie> d) -> d.getKey(), (Map.Entry<String, CharTrie> d) -> {
        return d.getValue().copy().getGenerator().generateDictionary(dictionaryLength, 5, "", 1, true);
      }));
    
    TableOutput output = new TableOutput();
    articles.stream().limit(articleCount).collect(Collectors.toList()).forEach((text) -> {
      HashMap<String, Object> map = new LinkedHashMap<>();
      map.put("text", text);
      dictionaries.forEach((modelTitle, dictionary) -> {
        int sumA = IntStream.range(0, text.length() / chunkSize)
          .mapToObj(i -> text.substring(i * chunkSize, Math.min(text.length(), (i + 1) * chunkSize)))
          .mapToInt(chunk -> CompressionUtil.encodeLZ(chunk, "").length).sum();
        int sumB = IntStream.range(0, text.length() / chunkSize)
          .mapToObj(i -> text.substring(i * chunkSize, Math.min(text.length(), (i + 1) * chunkSize)))
          .mapToInt(chunk -> CompressionUtil.encodeLZ(chunk, dictionary).length).sum();
        double bytes = (sumA - sumB) * 1.0 / sumA;
        map.put(Integer.toHexString(modelTitle.hashCode()), bytes);
      });
      output.putRow(map);
    });
    // System.p.println(output.toTextTable());
    String outputDirName = "tweets/";
    output.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
  }
  
  /**
   * Test tweet generation.
   *
   * @throws Exception the exception
   */
  @Test
  @Ignore
  @Category(TestCategories.ResearchCode.class)
  public void testTweetGeneration() {
    
    int maxLevels = 6;
    int minWeight = 1;
    int modelCount = 100000;
    int articleCount = 100;
    int lookahead = 1;
    
    CharTrieIndex tree_good = new CharTrieIndex();
    TweetSentiment.load().filter(x -> x.category == 1).limit(modelCount).map(t -> t.getText())
      .forEach(txt -> tree_good.addDocument(">>>" + txt));
    System.out.println(String.format("Indexing %s positive tweets; \ntree.getIndexedSize = %s KB", modelCount,
      tree_good.getIndexedSize() / 1024));
    tree_good.index(maxLevels, minWeight).truncate();
    System.out.println(String.format("tree.getMemorySize = %s KB", tree_good.getMemorySize() / 1024));
    
    CharTrieIndex tree_bad = new CharTrieIndex();
    TweetSentiment.load().filter(x -> x.category == 0).limit(modelCount).map(t -> t.getText())
      .forEach(txt -> tree_bad.addDocument(">>>" + txt));
    System.out.println(String.format("Indexing %s negative tweets; \ntree.getIndexedSize = %s KB", modelCount,
      tree_bad.getIndexedSize() / 1024));
    tree_bad.index(maxLevels, minWeight).truncate();
    System.out.println(String.format("tree.getMemorySize = %s KB", tree_bad.getMemorySize() / 1024));
    
    TableOutput output = new TableOutput();
    IntStream.range(0, articleCount).forEach(i -> {
      HashMap<String, Object> goodRow = new LinkedHashMap<>();
      goodRow.put("type", "good");
      goodRow.put("text", tree_good.getGenerator().generateDictionary(256, maxLevels - 1, ">>>", lookahead, true, true).substring(3).replaceAll("\u0000", "\n\t"));
      output.putRow(goodRow);
      HashMap<String, Object> badRow = new LinkedHashMap<>();
      badRow.put("type", "bad");
      badRow.put("text", tree_bad.getGenerator().generateDictionary(256, maxLevels - 1, ">>>", lookahead, true, true).substring(3).replaceAll("\u0000", "\n\t"));
      output.putRow(badRow);
    });
    System.out.println(output.toTextTable());
  }
  
  /**
   * Calc sentence coords.
   *
   * @throws IOException the io exception
   */
  @Test
  @Ignore
  public void calcSentenceCoords() throws IOException {
    Map<String, String> content = new HashMap<>();
    for (String name : Arrays.asList("earthtomoon.txt", "20000leagues.txt", "macbeth.txt", "randj.txt")) {
      content.put(name, new Scanner(getClass().getClassLoader().getResourceAsStream(name)).useDelimiter("\\Z").next()
        .replaceAll("[ \n\r\t]+", " "));
    }
    String allContent = content.values().stream().collect(Collectors.joining("\n"));
    List<String> sentances = Arrays.stream(allContent.split("\\.+")).map(line -> line.trim() + ".")
      .filter(line -> line.length() > 12).collect(Collectors.toList());
    Collections.shuffle(sentances);
    
    String characterSet = content.values().stream().flatMapToInt(s -> s.chars()).distinct()
      .mapToObj(c -> new String(Character.toChars(c))).sorted().collect(Collectors.joining(""));
    System.out.println("Character Set:" + characterSet);
    
    int maxLevels = 7;
    int minWeight = 1;
    double smoothness = 1.0;
    
    long startTime = System.currentTimeMillis();
    Map<String, CharTrie> trees = new HashMap<>();
    Map<String, String> dictionaries = new HashMap<>();
    for (Map.Entry<String, String> e : content.entrySet()) {
      CharTrieIndex tree = new CharTrieIndex();
      tree.addDocument(characterSet);
      tree.addDocument(e.getValue());
      tree.index(maxLevels, minWeight).truncate();
      trees.put(e.getKey(), tree);
      dictionaries.put(e.getKey(), tree.copy().getGenerator().generateDictionary(16 * 1024, 5, "", 1, true));
      System.out.println(
        String.format("Indexing %s; \ntree.getIndexedSize = %s KB", e.getKey(), tree.getIndexedSize() / 1024));
      System.out.println(String.format("tree.getMemorySize = %s KB", tree.getMemorySize() / 1024));
    }
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println(String.format("Built index in time = %s sec", elapsed / 1000.));
    
    TableOutput output1 = new TableOutput();
    TableOutput output2 = new TableOutput();
    sentances.stream().limit(1000).forEach(s -> {
      HashMap<String, Object> map2 = new LinkedHashMap<>();
      // map2.put("rawBits", s.length() * 8);
      // map2.put("decompressBits0", CharTreeUtil.compress("", s).length * 8);
      content.keySet().stream().forEach(key -> {
        CharTrie tree = trees.get(key);
        String dictionary = dictionaries.get(key);
        HashMap<String, Object> map1 = new LinkedHashMap<>();
        map1.put("key", key);
        map1.put("rawBits", s.length() * 8);
        map1.put("decompressBits0", CompressionUtil.encodeLZ(s, "").length * 8);
        int decompressBits1 = CompressionUtil.encodeLZ(s, dictionary.substring(0, 1024)).length * 8;
        map1.put(".decompressBits1", decompressBits1);
        // map2.put(key+".decompressBits1", decompressBits1);
        int decompressBits4 = CompressionUtil.encodeLZ(s, dictionary.substring(0, 4 * 1024)).length * 8;
        map1.put(".decompressBits4", decompressBits4);
        // map2.put(key+".decompressBits4", decompressBits4);
        int decompressBits16 = CompressionUtil.encodeLZ(s, dictionary.substring(0, 16 * 1024)).length * 8;
        map1.put(".decompressBits16", decompressBits16);
        // map2.put(key+".decompressBits16", decompressBits16);
        double ppmBits = tree.getAnalyzer().entropy(s);
        map1.put(".ppmBits", ppmBits);
        // map2.put(key+".ppmBits", ppmBits);
        map1.put(".bitsPerChar", ppmBits / s.length());
        map2.put(key + ".bitsPerChar", ppmBits / s.length());
        map1.put("txt", s.substring(0, Math.min(s.length(), 120)));
        output1.putRow(map1);
      });
      map2.put("txt", s);
      output2.putRow(map2);
    });
    // System.p.println(output1.toTextTable());
    String outputDirName = "sentenceClassification/";
    output2.writeProjectorData(new File(outPath, outputDirName), new URL(outBaseUrl, outputDirName));
  }
  
}
