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
//import com.simiacryptus.util.binary.Bits;
//import com.simiacryptus.util.io.MarkdownNotebookOutput;
//import com.simiacryptus.util.io.NotebookOutput;
//import com.simiacryptus.util.lang.TimedResult;
//import com.simiacryptus.util.test.EnglishWords;
//import com.simiacryptus.util.test.Misspelling;
//import com.simiacryptus.util.test.TestCategories;
//import com.simiacryptus.util.test.TweetSentiment;
//import com.simiacryptus.util.test.WikiArticle;
//import guru.nidi.graphviz.attribute.RankDir;
//import guru.nidi.graphviz.engine.Format;
//import guru.nidi.graphviz.engine.Graphviz;
//import guru.nidi.graphviz.engine.Renderer;
//import guru.nidi.graphviz.model.Graph;
//import guru.nidi.graphviz.model.Label;
//import guru.nidi.graphviz.model.Link;
//import guru.nidi.graphviz.model.LinkTarget;
//import guru.nidi.graphviz.model.Node;
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.experimental.categories.Category;
//
//import java.io.IOException;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//import static guru.nidi.graphviz.model.Factory.*;
//import static guru.nidi.graphviz.model.Link.to;
//
///**
// * The type Trie demo.
// */
//public class TrieDemo {
//
//  /**
//   * Gets url.
//   *
//   * @param url the url
//   * @return the url
//   */
//  public static URL getUrl(String url) {
//    try {
//      return new URL(url);
//    } catch (MalformedURLException e) {
//      throw new RuntimeException(e);
//    }
//  }
//
//  /**
//   * Demo search.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoSearch() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//
//      log.p("This will demonstrate how to use the CharTrieIndex class for searching indexed documents\n");
//
//      log.p("First, we cacheLocal some data into an index:");
//      CharTrieIndex trie = log.code(() -> {
//        return new CharTrieIndex();
//      });
//      Map<Integer, CharSequence> documents = log.code(() -> {
//        return WikiArticle.ENGLISH.stream().limit(100).collect(Collectors.toMap(
//          article -> trie.addDocument(article.getText()),
//          article -> article.getTitle()
//        ));
//      });
//      log.p("And then compute the index node:");
//      log.code(() -> {
//        trie.index(Integer.MAX_VALUE, 1);
//        print(trie);
//      });
//      log.p("Now we can search for a string:");
//      Map<CharSequence, Long> codec = log.code(() -> {
//        IndexNode match = trie.traverse("Computer");
//        System.out.println("Found string matches for " + match.getString());
//        return match.getCursors().map(cursor -> {
//          return documents.get(cursor.getDocumentId());
//        }).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
//      });
//
//    }
//  }
//
//  /**
//   * Demo char tree.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoCharTree() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//
//      log.p("This will demonstrate how to use the CharTrieIndex class for PPM and shared dictionary compression\n");
//
//      log.p("First, we cacheLocal some data into an index:");
//      CharTrie trie = log.code(() -> {
//        CharTrieIndex charTrieIndex = new CharTrieIndex();
//        WikiArticle.ENGLISH.stream().limit(100).forEach(article -> {
//          charTrieIndex.addDocument(article.getText());
//        });
//        charTrieIndex.index(5, 1);
//        return charTrieIndex;
//      });
//      log.p("And then derive a PPM codec:");
//      NodewalkerCodec codec = log.code(() -> {
//        return trie.getCodec();
//      });
//
//      log.p("\n\nThen, we use it to encode strings:");
//      WikiArticle wikiArticle = log.code(() -> {
//        return WikiArticle.ENGLISH.stream().skip(100)
//          .filter(article -> article.getText().length() > 1024 && article.getText().length() < 4096)
//          .findFirst().get();
//      });
//      {
//        CharSequence compressed = log.code(() -> {
//          Bits bits = codec.encodePPM(wikiArticle.getText(), 2);
//          System.out.print("Bit Length: " + bits.bitLength);
//          return bits.toBase64String();
//        });
//
//        log.p("\n\nAnd decompress to verify:");
//        CharSequence uncompressed = log.code(() -> {
//          byte[] bytes = Base64.getDecoder().decode(compressed.toString());
//          return codec.decodePPM(bytes, 2);
//        });
//      }
//
//
//      log.p("\n\nFor faster compression, we can define a dictionary for use apply Deflate:");
//      String dictionary = log.code(() -> {
//        return trie.getGenerator().generateDictionary(8 * 1024, 3, "", 1, true);
//      });
//      log.p("\n\nThen, we use it to encode strings:");
//      String compressed = log.code(() -> {
//        byte[] bits = CompressionUtil.encodeLZ(wikiArticle.getText(), dictionary);
//        System.out.print("Compressed Bytes: " + bits.length);
//        return Base64.getEncoder().encodeToString(bits);
//      });
//
//      log.p("\n\nAnd decompress to verify:");
//      CharSequence uncompressed = log.code(() -> {
//        byte[] bytes = Base64.getDecoder().decode(compressed);
//        return CompressionUtil.decodeLZToString(bytes, dictionary);
//      });
//
//    }
//  }
//
//  /**
//   * Demo tweet generation.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoTweetGeneration() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      int testingSize = 100;
//      int trainingSize = 50000;
//      int minWeight = 0;
//      int groups = trainingSize / 50000;
//      int maxLevels = 7;
//      int lookahead = 1;
//      int dictionarySampleSize = 4 * 1024;
//      int context = 5;
//      log.p("First, we cacheLocal positive and negative sentiment tweets into two seperate models");
//      List<TweetSentiment> tweetsPositive = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      List<TweetSentiment> tweetsNegative = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 0).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      CharTrie triePositive = log.code(() -> {
//        CharTrie charTrie = tweetsPositive.stream().skip(testingSize).limit(trainingSize)
//          .collect(Collectors.groupingByConcurrent(x -> new Random().nextInt(groups), Collectors.toList())).entrySet().stream()
//          .parallel().map(e -> {
//            CharTrieIndex charTrieIndex = new CharTrieIndex();
//            e.getValue().forEach(article -> charTrieIndex.addDocument(article.getText()));
//            charTrieIndex.index(maxLevels, minWeight);
//            return charTrieIndex.truncate();
//          }).reduce(CharTrie::add).get();
//        print(charTrie);
//        return charTrie;
//      });
//      CharTrie trieNegative = log.code(() -> {
//        CharTrie charTrie = tweetsNegative.stream().skip(testingSize).limit(trainingSize)
//          .collect(Collectors.groupingByConcurrent(x -> new Random().nextInt(groups), Collectors.toList())).entrySet().stream()
//          .parallel().map(e -> {
//            CharTrieIndex charTrieIndex = new CharTrieIndex();
//            e.getValue().forEach(article -> charTrieIndex.addDocument(article.getText()));
//            charTrieIndex.index(maxLevels, minWeight);
//            return charTrieIndex.truncate();
//          }).reduce(CharTrie::add).get();
//        print(charTrie);
//        return charTrie;
//      });
//      log.p("These source models produce similar representative texts:");
//      log.code(() -> {
//        return trieNegative.getGenerator().generateDictionary(dictionarySampleSize, context, "", lookahead, true);
//      });
//      log.code(() -> {
//        return triePositive.getGenerator().generateDictionary(dictionarySampleSize, context, "", lookahead, true);
//      });
//      log.p("The tree can also be reversed:");
//      log.code(() -> {
//        return triePositive.reverse().getGenerator().generateDictionary(dictionarySampleSize, context, "", lookahead, true);
//      });
//      log.p("And can be combined apply a variety of operations:");
//      CharTrie trieProduct = log.code(() -> {
//        return triePositive.product(trieNegative);
//      });
//      CharTrie trieSum = log.code(() -> {
//        CharTrie trie = triePositive.add(trieNegative);
//        print(trie);
//        return trie;
//      });
//      CharTrie negativeVector = log.code(() -> {
//        return trieNegative.divide(trieSum, 100);
//      });
//      CharTrie positiveVector = log.code(() -> {
//        return triePositive.divide(trieSum, 100);
//      });
//      log.p("These each produce consistent text extracts:");
//      IntStream.range(0, 3).forEach(l -> {
//        IntStream.range(1, context).forEach(ctx -> {
//          log.code(() -> {
//            System.out.println(String.format("Sum characteristic string apply %s context and %s lookahead", ctx, l));
//            return trieSum.getGenerator().generateDictionary(dictionarySampleSize, ctx, "", l, true);
//          });
//          log.code(() -> {
//            System.out.println(String.format("Product characteristic string apply %s context and %s lookahead", ctx, l));
//            return trieProduct.getGenerator().generateDictionary(dictionarySampleSize, ctx, "", l, true);
//          });
//          log.code(() -> {
//            System.out.println(String.format("Negative characteristic string apply %s context and %s lookahead", ctx, l));
//            return negativeVector.getGenerator().generateDictionary(dictionarySampleSize, ctx, "", l, true);
//          });
//          log.code(() -> {
//            System.out.println(String.format("Positive characteristic string apply %s context and %s lookahead", ctx, l));
//            return positiveVector.getGenerator().generateDictionary(dictionarySampleSize, ctx, "", l, true);
//          });
//        });
//      });
//    }
//  }
//
//  /**
//   * Demo reversal.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoReversal() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      int testingSize = 100;
//      int trainingSize = 50000;
//      int minWeight = 0;
//      int groups = trainingSize / 50000;
//      int maxLevels = 7;
//      int lookahead = 1;
//      int dictionarySampleSize = 4 * 1024;
//      int context = 5;
//      log.p("First, we cacheLocal text into a model");
//      List<TweetSentiment> tweetsPositive = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      CharTrie triePositive = log.code(() -> {
//        CharTrie charTrie = tweetsPositive.stream().skip(testingSize).limit(trainingSize)
//          .collect(Collectors.groupingByConcurrent(x -> new Random().nextInt(groups), Collectors.toList())).entrySet().stream()
//          .parallel().map(e -> {
//            CharTrieIndex charTrieIndex = new CharTrieIndex();
//            e.getValue().forEach(article -> charTrieIndex.addDocument(article.getText()));
//            charTrieIndex.index(maxLevels, minWeight);
//            return charTrieIndex.truncate();
//          }).reduce(CharTrie::add).get();
//        print(charTrie);
//        return charTrie;
//      });
//      log.p("This source model produces representative texts:");
//      log.code(() -> {
//        return triePositive.getGenerator().generateDictionary(dictionarySampleSize, context, "", lookahead, true);
//      });
//      log.p("The tree can also be reversed:");
//      log.code(() -> {
//        return triePositive.reverse().getGenerator().generateDictionary(dictionarySampleSize, context, "", lookahead, true);
//      });
//    }
//  }
//
//  /**
//   * Demo common words.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoCommonWords() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      List<CharSequence> trainingData = WikiArticle.ENGLISH.stream().map(x -> x.getText()).limit(200).collect(Collectors.toList());
//      int minWeight = 5;
//      int maxLevels = 200;
//      log.p("First, we cacheLocal text into a model");
//      CharTrie triePositive = log.code(() -> {
//        CharTrie charTrie = CharTrieIndex.indexFulltext(trainingData, maxLevels, minWeight).truncate();
//        print(charTrie);
//        return charTrie;
//      });
//      for (int penalty = 0; penalty < 10; penalty++) {
//        int _penalty = penalty;
//        log.p("We can then search for high-entropy keywords apply encoding penalty %s:", penalty);
//        log.code(() -> {
//          List<CharSequence> candidates = triePositive.max(node -> {
//            return (node.getDepth() - _penalty) * (node.getCursorCount());
//          }, 1000).map(x -> x.getString()).collect(Collectors.toList());
//          List<CharSequence> filteredKeywords = new ArrayList<>();
//          for (CharSequence keyword : candidates) {
//            if (!filteredKeywords.stream().anyMatch(x -> x.toString().contains(keyword) || keyword.toString().contains(x))) {
//              filteredKeywords.add(keyword);
//            }
//          }
//          return filteredKeywords.stream().map(x -> '"' + x.toString() + '"').collect(Collectors.joining("\n"));
//        });
//      }
//    }
//  }
//
//  /**
//   * Demo markov graph.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoMarkovGraph() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      List<CharSequence> trainingData = Arrays.asList("a cat in the hat that can hat the cat");
//      int minWeight = 1;
//      int maxLevels = Integer.MAX_VALUE;
//      log.p("First, we cacheLocal text into a model");
//      CharTrie trie = log.code(() -> {
//        CharTrie charTrie = CharTrieIndex.indexFulltext(trainingData, maxLevels, minWeight).truncate();
//        print(charTrie);
//        return charTrie;
//      });
//      log.p("The graph:");
//      HashMap<CharSequence, Node> nodes = new HashMap<>();
//      log.code(() -> {
//        Node node = buildNode(trie.root(), maxLevels);
//        Graph graph = graph().directed().generalAttr().with(RankDir.LEFT_TO_RIGHT).with(node);
//        Renderer render = Graphviz.fromGraph(graph).width(1200).render(Format.PNG);
//        return render.toImage();
//      });
//    }
//  }
//
//  private Node buildNode(TrieNode root, int levels) {
//    LinkTarget[] links;
//    if (root.getDepth() > levels) {
//      links = new LinkTarget[]{};
//    }
//    else {
//      links = root.getChildren().filter(n -> n.getChar() != Character.MIN_VALUE).map(child -> {
//        Node childNode = buildNode(child, levels); //node(child.getDebugString()).apply(Label.of(child.getString()));
//        Link linkNode = to(childNode).with(Label.of(Integer.toString((int) child.getCursorCount())));
//        return linkNode;
//
//      }).toArray(i -> new LinkTarget[i]);
//    }
//    return node(root.getDebugString()).with(Label.of('"' + root.getString() + '"')).link(links);
//  }
//
//  /**
//   * Demo tweet classification.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoTweetClassification() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      int testingSize = 1000;
//      int trainingSize = 500000;
//      int minWeight = 1;
//      int groups = trainingSize / 50000;
//      int maxLevels = 7;
//      log.p("First, we cacheLocal positive and negative sentiment tweets into two seperate models");
//      List<TweetSentiment> tweetsPositive = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      List<TweetSentiment> tweetsNegative = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 0).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      CharTrie triePositive = log.code(() -> {
//        CharTrie charTrie = tweetsPositive.stream().skip(testingSize).limit(trainingSize)
//          .collect(Collectors.groupingByConcurrent(x -> new Random().nextInt(groups), Collectors.toList())).entrySet().stream()
//          .parallel().map(e -> {
//            CharTrieIndex charTrieIndex = new CharTrieIndex();
//            e.getValue().forEach(article -> charTrieIndex.addDocument(article.getText()));
//            charTrieIndex.index(maxLevels, minWeight);
//            return charTrieIndex.truncate();
//          }).reduce(CharTrie::add).get();
//        print(charTrie);
//        return charTrie;
//      });
//      CharTrie trieNegative = log.code(() -> {
//        CharTrie charTrie = tweetsNegative.stream().skip(testingSize).limit(trainingSize)
//          .collect(Collectors.groupingByConcurrent(x -> new Random().nextInt(groups), Collectors.toList())).entrySet().stream()
//          .parallel().map(e -> {
//            CharTrieIndex charTrieIndex = new CharTrieIndex();
//            e.getValue().forEach(article -> charTrieIndex.addDocument(article.getText()));
//            charTrieIndex.index(maxLevels, minWeight);
//            return charTrieIndex.truncate();
//          }).reduce(CharTrie::add).get();
//        print(charTrie);
//        return charTrie;
//      });
//      log.p("Each model can be used out-of-the-box to perform classification:");
//      log.code(() -> {
//        NodewalkerCodec codecPositive = triePositive.getCodec();
//        NodewalkerCodec codecNegative = trieNegative.getCodec();
//        double positiveAccuracy = 100.0 * tweetsPositive.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodePos = codecPositive.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
//          return prediction == tweet.category ? 1 : 0;
//        }).average().getAsDouble();
//        double negativeAccuracy = 100.0 * tweetsNegative.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodePos = codecPositive.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
//          return prediction == tweet.category ? 1 : 0;
//        }).average().getAsDouble();
//        return String.format("Accuracy = %.3f%%, %.3f%%", positiveAccuracy, negativeAccuracy);
//      });
//      log.p("Or can be combined apply a variety of operations:");
//      CharTrie trieSum = log.code(() -> {
//        return triePositive.add(trieNegative);
//      });
//      CharTrie negativeVector = log.code(() -> {
//        return trieNegative.divide(trieSum, 100);
//      });
//      CharTrie positiveVector = log.code(() -> {
//        return triePositive.divide(trieSum, 100);
//      });
//      log.p("These composite tries can also be used to perform classification:");
//      log.code(() -> {
//        NodewalkerCodec codecPositive = positiveVector.getCodec();
//        NodewalkerCodec codecNegative = negativeVector.getCodec();
//        double positiveAccuracy = 100.0 * tweetsPositive.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodePos = codecPositive.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
//          return prediction == tweet.category ? 1 : 0;
//        }).average().getAsDouble();
//        double negativeAccuracy = 100.0 * tweetsNegative.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeNeg = codecNegative.encodePPM(tweet.getText(), 2);
//          Bits encodePos = codecPositive.encodePPM(tweet.getText(), 2);
//          int prediction = (encodeNeg.bitLength > encodePos.bitLength) ? 1 : 0;
//          return prediction == tweet.category ? 1 : 0;
//        }).average().getAsDouble();
//        return String.format("Accuracy = %.3f%%, %.3f%%", positiveAccuracy, negativeAccuracy);
//      });
//    }
//  }
//
//  /**
//   * Demo tweet classification tree.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.ResearchCode.class)
//  public void demoTweetClassificationTree() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      int testingSize = 10000;
//      int trainingSize = 5000;
//      log.p("First, we cacheLocal positive and negative sentiment tweets into two seperate models");
//      List<TweetSentiment> tweetsPositive = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 1).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      List<TweetSentiment> tweetsNegative = log.code(() -> {
//        ArrayList<TweetSentiment> list = new ArrayList<>(TweetSentiment.load()
//          .filter(x -> x.category == 0).limit(testingSize + trainingSize).collect(Collectors.toList()));
//        Collections.shuffle(list);
//        return list;
//      });
//      Function<CharSequence, Map<CharSequence, Double>> rule = log.code(() -> {
//        HashMap<CharSequence, List<CharSequence>> map = new HashMap<>();
//        map.put("pos", tweetsPositive.stream().limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()));
//        map.put("neg", tweetsNegative.stream().limit(trainingSize).map(x -> x.getText()).collect(Collectors.toList()));
//        return new ClassificationTree().setVerbose(System.out).categorizationTree(map, 32);
//      });
//      log.code(() -> {
//        return tweetsPositive.stream().skip(trainingSize).map(x -> x.getText()).mapToDouble(str -> {
//          Map<CharSequence, Double> prob = rule.apply(str);
//          System.out.println(String.format("%s -> %s", str, prob));
//          return prob.getOrDefault("pos", 0.0) < 0.5 ? 0.0 : 1.0;
//        }).average().getAsDouble();
//      });
//      log.code(() -> {
//        return tweetsNegative.stream().skip(trainingSize).map(x -> x.getText()).mapToDouble(str -> {
//          Map<CharSequence, Double> prob = rule.apply(str);
//          System.out.println(String.format("%s -> %s", str, prob));
//          return prob.getOrDefault("neg", 0.0) < 0.5 ? 0.0 : 1.0;
//        }).average().getAsDouble();
//      });
//    }
//  }
//
//  /**
//   * Demo language classification.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoLanguageClassification() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      int testingSize = 100;
//      int trainingSize = 5;
//      int minWeight = 1;
//      int maxLevels = 5;
//      int minArticleSize = 4 * 1024;
//      log.p("First, we cacheLocal positive and negative sentiment tweets into two seperate models");
//      List<WikiArticle> english = log.code(() -> {
//        return new ArrayList<>(WikiArticle.ENGLISH.stream().filter(x -> x.getText().length() > minArticleSize)
//          .limit(testingSize + trainingSize).collect(Collectors.toList()));
//      });
//      List<WikiArticle> french = log.code(() -> {
//        return new ArrayList<>(WikiArticle.FRENCH.stream().filter(x -> x.getText().length() > minArticleSize)
//          .limit(testingSize + trainingSize).collect(Collectors.toList()));
//      });
//      CharTrie trieEnglish = log.code(() -> {
//        CharTrie charTrie = CharTrieIndex.indexFulltext(english.subList(0, trainingSize)
//          .stream().map(x -> x.getText()).collect(Collectors.toList()), maxLevels, minWeight);
//        print(charTrie);
//        return charTrie;
//      });
//      CharTrie trieFrench = log.code(() -> {
//        CharTrie charTrie = CharTrieIndex.indexFulltext(french.subList(testingSize, french.size())
//          .stream().map(x -> x.getText()).collect(Collectors.toList()), maxLevels, minWeight);
//        print(charTrie);
//        return charTrie;
//      });
//      log.p("Each model can be used out-of-the-box to perform classification:");
//      log.code(() -> {
//        NodewalkerCodec codecA = trieEnglish.getCodec();
//        NodewalkerCodec codecB = trieFrench.getCodec();
//        double englishAccuracy = 100.0 * english.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          return (encodeB.bitLength > encodeA.bitLength) ? 1 : 0;
//        }).average().getAsDouble();
//        double frenchAccuracy = 100.0 * french.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          return (encodeB.bitLength < encodeA.bitLength) ? 1 : 0;
//        }).average().getAsDouble();
//        return String.format("Accuracy = %.3f%%, %.3f%%", englishAccuracy, frenchAccuracy);
//      });
//      log.p("Or can be combined apply a variety of operations:");
//      CharTrie trieSum = log.code(() -> {
//        return trieEnglish.add(trieFrench);
//      });
//      CharTrie frenchVector = log.code(() -> {
//        return trieFrench.divide(trieSum, 100);
//      });
//      CharTrie englishVector = log.code(() -> {
//        return trieEnglish.divide(trieSum, 100);
//      });
//      log.p("For fun:");
//      log.code(() -> {
//        return trieSum.getGenerator().generateDictionary(1024, 3, "", 0, true);
//      });
//      log.code(() -> {
//        return trieEnglish.product(trieFrench).getGenerator().generateDictionary(1024, 3, "", 0, true);
//      });
//      log.p("These composite tries can also be used to perform classification:");
//      log.code(() -> {
//        NodewalkerCodec codecA = englishVector.getCodec();
//        NodewalkerCodec codecB = frenchVector.getCodec();
//        double englishAccuracy = 100.0 * english.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          return (encodeB.bitLength > encodeA.bitLength) ? 1 : 0;
//        }).average().getAsDouble();
//        double frenchAccuracy = 100.0 * french.stream().limit(testingSize).mapToDouble(tweet -> {
//          Bits encodeB = codecB.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          Bits encodeA = codecA.encodePPM(tweet.getText(), Integer.MAX_VALUE);
//          return (encodeB.bitLength < encodeA.bitLength) ? 1 : 0;
//        }).average().getAsDouble();
//        return String.format("Accuracy = %.3f%%, %.3f%%", englishAccuracy, frenchAccuracy);
//      });
//    }
//  }
//
//  private void print(CharTrie trie) {
//    System.out.println("Total Indexed Document (KB): " + trie.getIndexedSize() / 1024);
//    System.out.println("Total Node Count: " + trie.getNodeCount());
//    System.out.println("Total Index Memory Size (KB): " + trie.getMemorySize() / 1024);
//  }
//
//  /**
//   * Demo compression.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoCompression() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      HashSet<CharSequence> articles = new HashSet<CharSequence>(Arrays.asList("A"));
//
//      log.p("This will demonstrate how to serialize a CharTrie class in compressed format\n");
//
//      log.p("First, we decompose the text into an n-gram node:");
//      List<WikiArticle> articleList = WikiArticle.ENGLISH.stream()
//        .limit(1000).filter(x -> articles.contains(x.getTitle())).limit(articles.size())
//        .collect(Collectors.toList());
//      CharTrieIndex index = log.code(() -> {
//        CharTrieIndex trie = new CharTrieIndex();
//        articleList.forEach(article -> {
//          System.out.println(String.format("Indexing %s", article.getTitle()));
//          trie.addDocument(article.getText());
//        });
//        System.out.println(String.format("Indexing %s bytes of documents",
//          trie.getIndexedSize()));
//        trie.index(5, 1);
//        return trie;
//      });
//      CharTrie trie = index.truncate();
//      log.p("\n\nThen, we compress the node:");
//      byte[] serializedTrie = log.code(() -> {
//        print(trie);
//        byte[] bytes = new ConvolutionalTrieSerializer().serialize(trie.copy());
//        System.out.println(String.format("%s in ram, %s bytes in serialized form, %s%% compression",
//          trie.getMemorySize(), bytes.length, 100 - (bytes.length * 100.0 / trie.getMemorySize())));
//        return bytes;
//      });
//      log.p("Then, we encode the data used to create the dictionary:");
//      List<byte[]> compressedArticles = log.code(() -> {
//        NodewalkerCodec codec = new NodewalkerCodec(trie);
//        return articleList.stream().map(article -> {
//          String text = article.getText();
//          CharSequence title = article.getTitle();
//          TimedResult<Bits> compressed = TimedResult.time(() -> codec.encodePPM(text, Integer.MAX_VALUE));
//          TimedResult<CharSequence> decompressed = TimedResult.time(() -> codec.decodePPM(compressed.result.getBytes(), Integer.MAX_VALUE));
//          System.out.println(String.format("Serialized %s: %s chars -> %s bytes (%s%%) in %s sec; %s",
//            title, article.getText().length(), compressed.result.bitLength / 8.0,
//            compressed.result.bitLength * 100.0 / (8.0 * article.getText().length()),
//            compressed.timeNanos / 1000000000.0, text.equals(decompressed.result) ? "Verified" : "Failed Validation"));
//          return compressed.result.getBytes();
//        }).collect(Collectors.toList());
//      });
//      int totalSize = compressedArticles.stream().mapToInt(x -> x.length).sum();
//      log.p("Compressed %s bytes of documents -> %s (%s dictionary + %s ppm)",
//        index.getIndexedSize(), (totalSize + serializedTrie.length),
//        serializedTrie.length, totalSize);
//
//      log.p("And decompress to verfy data:");
//      log.code(() -> {
//        NodewalkerCodec codec = new NodewalkerCodec(trie);
//        compressedArticles.forEach(article -> {
//          TimedResult<CharSequence> decompressed = TimedResult.time(() -> codec.decodePPM(article, Integer.MAX_VALUE));
//          System.out.println(String.format("Deserialized %s bytes -> %s chars in %s sec",
//            article.length, decompressed.result.length(),
//            decompressed.timeNanos / 1000000000.0));
//        });
//      });
//      log.p("\n\nAnd verify tree structure:");
//      log.code(() -> {
//        CharTrie restored = new ConvolutionalTrieSerializer().deserialize(serializedTrie);
//        boolean verified = restored.root().equals(trie.root());
//        System.out.println(String.format("Tree Verified: %s", verified));
//      });
//    }
//  }
//
//  /**
//   * Scratch.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void scratch() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      log.code(() -> {
//        Assert.assertEquals("testing", TextAnalysis.combine("test", "sting", 2));
//        Assert.assertEquals(null, TextAnalysis.combine("test", "sting", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine("this is a test", "is a", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine("this is a test", "this is", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine("this is a test", " test", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine("is a", "this is a test", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine("this is", "this is a test", 3));
//        Assert.assertEquals("this is a test", TextAnalysis.combine(" test", "this is a test", 3));
//        Assert.assertEquals(null, TextAnalysis.combine("sting", "test", 3));
//        Assert.assertEquals("testing", TextAnalysis.combine("sting", "test", 2));
//      });
//    }
//  }
//
//  /**
//   * Demo wiki summary.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoWikiSummary() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      HashSet<CharSequence> articles = new HashSet<>();
//      Arrays.asList("Alabama", "Alchemy", "Algeria", "Altruism", "Abraham Lincoln", "ASCII", "Apollo", "Alaska").forEach(articles::add);
//      log.p("This will demonstrate how to serialize a CharTrie class in compressed format\n");
//      log.h3("First, we cacheLocal training and testing data:");
//      List<WikiArticle> articleList = log.code(() -> {
//        return WikiArticle.ENGLISH.stream().limit(1000)
//          .filter(x -> articles.contains(x.getTitle())).limit(articles.size())
//          .collect(Collectors.toList());
//      });
//      List<WikiArticle> trainingList = log.code(() -> {
//        return WikiArticle.ENGLISH.stream()
//          .filter(x -> x.getText().length() > 4 * 1024).filter(x -> !articles.contains(x.getTitle()))
//          .limit(1000).collect(Collectors.toList());
//      });
//      log.h3("Then, we decompose the text into an n-gram node:");
//      int depth = 7;
//      CharTrie referenceTrie = log.code(() -> {
//        CharTrie trie = CharTrieIndex.indexFulltext(trainingList.stream().map(x -> x.getText()).collect(Collectors.toList()), depth, 0);
//        print(trie);
//        return trie;
//      });
//      articleList.forEach(testArticle -> {
//        log.h2(testArticle.getTitle());
//        CharTrie articleTrie = log.code(() -> {
//          //CharTrie node = CharTrieIndex.create(Arrays.asList(testArticle.getText()), depth, 0);
//          //print(node);
//          return referenceTrie;//.add(node);
//        });
//        log.h3("Keywords");
//        log.code(() -> {
//          return articleTrie.getAnalyzer().setVerbose(System.out).keywords(testArticle.getText())
//            .stream().map(s -> '"' + s.toString() + '"').collect(Collectors.joining(", "));
//        });
//        log.h3("Tokenization");
//        log.code(() -> {
//          return articleTrie.getAnalyzer().setVerbose(System.out).splitChars(testArticle.getText())
//            .stream().map(s -> '"' + s.toString() + '"').collect(Collectors.joining(", "));
//        });
//      });
//    }
//  }
//
//  /**
//   * Demo wordlist.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoWordlist() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      HashSet<CharSequence> articles = new HashSet<>();
//      Arrays.asList("Alabama", "Alchemy", "Algeria", "Altruism", "Abraham Lincoln", "ASCII", "Apollo", "Alaska").forEach(articles::add);
//      log.p("This will demonstrate how to serialize a CharTrie class in compressed format\n");
//      log.h3("First, we cacheLocal training and testing data:");
//      List<WikiArticle> articleList = log.code(() -> {
//        return WikiArticle.ENGLISH.stream().limit(1000)
//          .filter(x -> articles.contains(x.getTitle())).limit(articles.size())
//          .collect(Collectors.toList());
//      });
//      List<EnglishWords> trainingList = log.code(() -> {
//        return EnglishWords.load().collect(Collectors.toList());
//      });
////            List<WikiArticle> trainingList = log.code(() -> {
////                return WikiArticle.ENGLISH.cacheLocal()
////                        .filter(x -> x.getText().length() > 4 * 1024).filter(x -> !articles.contains(x.getTitle()))
////                        .limit(1000).collect(Collectors.toList());
////            });
//      log.h3("Then, we decompose the text into an n-gram node:");
//      int depth = 7;
//      CharTrie referenceTrie = log.code(() -> {
//        CharTrie trie = CharTrieIndex.indexWords(trainingList.stream().map(x -> x.getText())
//          .collect(Collectors.toList()), depth, 0);
//        print(trie);
//        return trie;
//      });
//      articleList.forEach(testArticle -> {
//        log.h2(testArticle.getTitle());
//        log.h3("Tokenization");
//        List<CharSequence> tokens = log.code(() -> {
//          return referenceTrie.getAnalyzer().setVerbose(System.out).splitMatches(testArticle.getText(), 2);
//        });
//        log.h3("Keywords");
//        log.code(() -> {
//          return tokens.stream().collect(Collectors.groupingBy(x -> x, Collectors.counting())).entrySet().stream()
//            .sorted(Comparator.comparing(x -> -x.getValue()))
//            .map(e -> String.format("%s=%s", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
//        });
//      });
//    }
//  }
//
//  /**
//   * Demo wiki spelling.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoWikiSpelling() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//      log.p("This will demonstrate how to serialize a CharTrie class in compressed format\n");
//      log.h3("First, we cacheLocal training and testing data:");
//
//      List<Misspelling> trainingList = log.code(() -> {
//        return Misspelling.BIRKBECK.load().collect(Collectors.toList());
//      });
//      log.h3("Then, we decompose the text into an n-gram node:");
//      CharTrie referenceTrie = log.code(() -> {
//        List<CharSequence> list = trainingList.stream().map(x -> "|" + x.getTitle() + "|").collect(Collectors.toList());
//        CharTrie trie = CharTrieIndex.indexFulltext(list, Integer.MAX_VALUE, 0);
//        print(trie);
//        return trie;
//      });
//      Collections.shuffle(trainingList);
//      trainingList.stream().limit(20).forEach(testArticle -> {
//        log.p("Spelling check: %s -> %s", testArticle.getText(), testArticle.getTitle());
//        log.code(() -> {
//          return referenceTrie.getAnalyzer().setVerbose(System.out).spelling("|" + testArticle.getText() + "|");
//        });
//      });
//    }
//  }
//
//  /**
//   * Demo serialization.
//   *
//   * @throws IOException the io exception
//   */
//  @Test
//  @Category(TestCategories.Report.class)
//  public void demoSerialization() throws IOException {
//    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
//
//      log.p("This will demonstrate how to serialize a CharTrie class in compressed format\n");
//
//      log.p("First, we cacheLocal some data into an index:");
//      CharTrieIndex index = log.code(() -> {
//        CharTrieIndex charTrieIndex = new CharTrieIndex();
//        WikiArticle.ENGLISH.stream().limit(100).forEach(article -> {
//          charTrieIndex.addDocument(article.getText());
//        });
//        System.out.println(String.format("Indexing %s bytes of documents",
//          charTrieIndex.getIndexedSize()));
//        charTrieIndex.index(6, 1);
//        return charTrieIndex;
//      });
//      CharTrie tree = index.truncate();
//
//      log.p("\n\nThen, we compress the tree:");
//      String serialized = log.code(() -> {
//        byte[] bytes = CompressionUtil.encodeLZ(new ConvolutionalTrieSerializer().serialize(tree.copy()));
//        System.out.println(String.format("%s in ram, %s bytes in serialized form, %s%% compression",
//          tree.getMemorySize(), bytes.length, 100 - (bytes.length * 100.0 / tree.getMemorySize())));
//        return Base64.getEncoder().encodeToString(bytes);
//      });
//
//      log.p("\n\nAnd decompress to verify:");
//      int dictionaryLength = log.code(() -> {
//        byte[] bytes = CompressionUtil.decodeLZ(Base64.getDecoder().decode(serialized));
//        CharTrie restored = new ConvolutionalTrieSerializer().deserialize(bytes);
//        boolean verified = restored.root().equals(tree.root());
//        System.out.println(String.format("Tree Verified: %s", verified));
//        return bytes.length;
//      });
//
//      log.p("Then, we encode the data used to create the dictionary:");
//      log.code(() -> {
//        NodewalkerCodec codec = tree.getCodec();
//        int totalSize = WikiArticle.ENGLISH.stream().limit(100).map(article -> {
//          TimedResult<Bits> compressed = TimedResult.time(() -> codec.encodePPM(article.getText(), 4));
//          System.out.println(String.format("Serialized %s: %s chars -> %s bytes (%s%%) in %s ms",
//            article.getTitle(), article.getText().length(), compressed.result.bitLength / 8.0,
//            compressed.result.bitLength * 100.0 / (8.0 * article.getText().length()),
//            compressed.timeNanos / 1000000.0));
//          return compressed.result.getBytes();
//        }).mapToInt(bytes -> bytes.length).sum();
//        return String.format("Compressed %s KB of documents -> %s KB (%s dictionary + %s ppm)",
//          index.getIndexedSize() / 1024, (totalSize + dictionaryLength) / 1024,
//          dictionaryLength / 1024, totalSize / 1024);
//      });
//
//      log.p("For reference, we encode some sample articles that are NOT in the dictionary:");
//      log.code(() -> {
//        NodewalkerCodec codec = tree.getCodec();
//        WikiArticle.ENGLISH.stream().skip(100).limit(10).forEach(article -> {
//          TimedResult<Bits> compressed = TimedResult.time(() -> codec.encodePPM(article.getText(), 4));
//          System.out.println(String.format("Serialized %s: %s chars -> %s bytes (%s%%) in %s ms",
//            article.getTitle(), article.getText().length(), compressed.result.bitLength / 8.0,
//            compressed.result.bitLength * 100.0 / (8.0 * article.getText().length()),
//            compressed.timeNanos / 1000000.0));
//        });
//      });
//
//    }
//  }
//
//
//}
