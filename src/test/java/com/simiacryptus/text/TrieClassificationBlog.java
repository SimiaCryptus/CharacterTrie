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
import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.io.MarkdownNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.test.TestCategories;
import com.simiacryptus.util.test.TweetSentiment;
import com.simiacryptus.util.test.WikiArticle;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The type Trie classification blog.
 */
public class TrieClassificationBlog {
  
  private static void print(CharTrie trie) {
    System.out.println("Total Indexed Document (KB): " + trie.getIndexedSize() / 1024);
    System.out.println("Total Node Count: " + trie.getNodeCount());
    System.out.println("Total Index Memory Size (KB): " + trie.getMemorySize() / 1024);
  }
  
  /**
   * Language detection ppm.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void language_detection_ppm() throws IOException {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      log.h1("Language Detection via PPM Compression");
      int testingSize = 100;
      int trainingSize = 100;
      int minWeight = 1;
      int maxLevels = 5;
      int minArticleSize = 4 * 1024;
      log.p("First, we cache English and French wikipedia articles into two collections");
      List<WikiArticle> english = log.code(() -> {
        return new ArrayList<>(WikiArticle.ENGLISH.stream().filter(x -> x.getText().length() > minArticleSize)
          .limit(testingSize + trainingSize).collect(Collectors.toList()));
      });
      List<WikiArticle> french = log.code(() -> {
        return new ArrayList<>(WikiArticle.FRENCH.stream().filter(x -> x.getText().length() > minArticleSize)
          .limit(testingSize + trainingSize).collect(Collectors.toList()));
      });
      log.p("Then, we process each into separate language models");
      CharTrie trieEnglish = log.code(() -> {
        CharTrie charTrie = CharTrieIndex.indexFulltext(english.subList(0, trainingSize)
          .stream().map(x -> x.getText()).collect(Collectors.toList()), maxLevels, minWeight);
        print(charTrie);
        return charTrie;
      });
      CharTrie trieFrench = log.code(() -> {
        CharTrie charTrie = CharTrieIndex.indexFulltext(french.subList(testingSize, french.size())
          .stream().map(x -> x.getText()).collect(Collectors.toList()), maxLevels, minWeight);
        print(charTrie);
        return charTrie;
      });
      log.p("The models can be used to perform PPM-based classification apply excellent results:");
      log.code(() -> {
        NodewalkerCodec codecA = trieEnglish.getCodec();
        NodewalkerCodec codecB = trieFrench.getCodec();
        double englishAccuracy = 100.0 * english.stream().limit(testingSize).mapToDouble(tweet -> {
          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          return (encodeB.bitLength > encodeA.bitLength) ? 1 : 0;
        }).average().getAsDouble();
        double frenchAccuracy = 100.0 * french.stream().limit(testingSize).mapToDouble(tweet -> {
          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          return (encodeB.bitLength < encodeA.bitLength) ? 1 : 0;
        }).average().getAsDouble();
        return String.format("Accuracy = %.3f%%, %.3f%%", englishAccuracy, frenchAccuracy);
      });
    }
  }
  
  /**
   * Prebuilt language models.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void prebuilt_language_models() throws IOException {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      log.h1("Language Detection using prebuilt models");
      TableOutput table = new TableOutput();
      evaluateLanguage(log, "English", WikiArticle.ENGLISH, table);
      evaluateLanguage(log, "French", WikiArticle.FRENCH, table);
      evaluateLanguage(log, "German", WikiArticle.GERMAN, table);
      log.h3("Results");
      log.p(table.toTextTable());
    }
  }
  
  private void evaluateLanguage(NotebookOutput log, String sourceLanguage, WikiArticle.WikiDataLoader sourceData, TableOutput table) {
    int testingSize = 100;
    int minArticleSize = 4 * 1024;
    log.h3(sourceLanguage);
    log.p("Loading %s articles of %s to test language classification...", testingSize, sourceLanguage);
    log.code(() -> {
      sourceData.stream()
        .map(x -> x.getText()).filter(x -> x.length() > minArticleSize).limit(testingSize)
        .collect(Collectors.toList()).parallelStream()
        .map(x -> LanguageModel.match(x)).collect(Collectors.groupingBy(x -> x, Collectors.counting()))
        .forEach((language, count) ->
        {
          HashMap<String, Object> row = new HashMap<>();
          row.put("Source", sourceLanguage);
          row.put("Predicted", language.name());
          row.put("Count", count);
          table.putRow(row);
        });
    });
  }
  
  /**
   * Sentiment analysis ppm.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void sentiment_analysis_ppm() throws IOException {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      log.h1("Sentiment Analysis via PPM Compression");
      int testingSize = 10000;
      int trainingSize = 100000;
      int minWeight = 1;
      int maxLevels = 5;
      log.p("\n\n\n");
      log.p("First, we cache positive and negative sentiment tweets into two separate models");
      List<TweetSentiment> tweetsPositive = log.code(() -> {
        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
        Collections.shuffle(list);
        return list;
      });
      List<TweetSentiment> tweetsNegative = log.code(() -> {
        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
          .filter(x -> x.category == 0).limit(testingSize + trainingSize).collect(Collectors.toList()));
        Collections.shuffle(list);
        return list;
      });
      CharTrie triePositive = log.code(() -> {
        CharTrie charTrie = CharTrieIndex.indexFulltext(
          tweetsPositive.stream().skip(testingSize).limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()),
          maxLevels, minWeight);
        print(charTrie);
        return charTrie;
      });
      CharTrie trieNegative = log.code(() -> {
        CharTrie charTrie = CharTrieIndex.indexFulltext(
          tweetsNegative.stream().skip(testingSize).limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()),
          maxLevels, minWeight);
        print(charTrie);
        return charTrie;
      });
      log.p("\n\n\n");
      log.p("Each model can be used to perform classification, which does not work very well in this dataset:");
      log.code(() -> {
        NodewalkerCodec codecPositive = triePositive.getCodec();
        NodewalkerCodec codecNegative = trieNegative.getCodec();
        double positiveAccuracy = 100.0 * tweetsPositive.stream().limit(testingSize).mapToDouble(tweet -> {
          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          Bits encodePos = codecPositive.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
          return prediction == tweet.category ? 1 : 0;
        }).average().getAsDouble();
        double negativeAccuracy = 100.0 * tweetsNegative.stream().limit(testingSize).mapToDouble(tweet -> {
          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          Bits encodePos = codecPositive.encodePPM(tweet.getText(), Integer.MAX_VALUE);
          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
          return prediction == tweet.category ? 1 : 0;
        }).average().getAsDouble();
        return String.format("Accuracy = %.3f%% apply positive sentiment, %.3f%% apply negative sentiment",
          positiveAccuracy, negativeAccuracy);
      });
    }
  }
  
  /**
   * Sentiment analysis decision tree.
   *
   * @throws IOException the io exception
   */
  @Test
  @Category(TestCategories.Report.class)
  public void sentiment_analysis_decision_tree() throws IOException {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      log.h1("Sentiment Analysis using a Decision Tree");
      int testingSize = 1000;
      int trainingSize = 50000;
      log.p("First, we cache positive and negative sentiment tweets into two seperate models");
      List<TweetSentiment> tweetsPositive = log.code(() -> {
        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
        Collections.shuffle(list);
        return list;
      });
      List<TweetSentiment> tweetsNegative = log.code(() -> {
        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
          .filter(x -> x.category == 0).limit(testingSize + trainingSize).collect(Collectors.toList()));
        Collections.shuffle(list);
        return list;
      });
      Function<String, Map<String, Double>> rule = log.code(() -> {
        HashMap<String, List<String>> map = new HashMap<>();
        map.put("Positive", tweetsPositive.stream().limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()));
        map.put("Negative", tweetsNegative.stream().limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()));
        return new ClassificationTree().setVerbose(System.out).categorizationTree(map, 32);
      });
      TableOutput table = new TableOutput();
      log.code(() -> {
        return tweetsPositive.stream().skip(trainingSize).map(x -> x.getText()).mapToDouble(str -> {
          Map<String, Double> prob = rule.apply(str);
          HashMap<String, Object> row = new LinkedHashMap<>();
          row.put("Category", "Positive");
          row.put("Prediction", prob.entrySet().stream().max(Comparator.comparing(x -> x.getValue())).get().getKey());
          prob.forEach((category, count) -> row.put(category + "%", count * 100));
          row.put("Text", str);
          table.putRow(row);
          return prob.getOrDefault("Positive", 0.0) < 0.5 ? 0.0 : 1.0;
        }).average().getAsDouble() * 100.0 + "%";
      });
      log.code(() -> {
        return tweetsNegative.stream().skip(trainingSize).map(x -> x.getText()).mapToDouble(str -> {
          Map<String, Double> prob = rule.apply(str);
          HashMap<String, Object> row = new LinkedHashMap<>();
          row.put("Category", "Negative");
          row.put("Prediction", prob.entrySet().stream().max(Comparator.comparing(x -> x.getValue())).get().getKey());
          prob.forEach((category, count) -> row.put(category + "%", count * 100));
          row.put("Text", str);
          table.putRow(row);
          return prob.getOrDefault("Negative", 0.0) < 0.5 ? 0.0 : 1.0;
        }).average().getAsDouble() * 100.0 + "%";
      });
      //log.p(table.toTextTable());
    }
  }
  
  
}
