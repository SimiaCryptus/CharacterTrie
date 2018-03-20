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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The type Text analysis.
 */
public class TextAnalysis {
  
  /**
   * The constant DEFAULT_THRESHOLD.
   */
  public static final double DEFAULT_THRESHOLD = Math.log(15);
  private final CharTrie inner;
  private PrintStream verbose = null;
  
  /**
   * Instantiates a new Text analysis.
   *
   * @param inner the inner
   */
  TextAnalysis(CharTrie inner) {
    this.inner = inner;
  }
  
  /**
   * Combine string.
   *
   * @param left       the left
   * @param right      the right
   * @param minOverlap the min overlap
   * @return the string
   */
  public static String combine(String left, String right, int minOverlap) {
    if (left.length() < minOverlap) return null;
    if (right.length() < minOverlap) return null;
    int bestOffset = Integer.MAX_VALUE;
    for (int offset = minOverlap - left.length(); offset < right.length() - minOverlap; offset++) {
      boolean match = true;
      for (int posLeft = Math.max(0, -offset); posLeft < Math.min(left.length(), right.length() - offset); posLeft++) {
        if (left.charAt(posLeft) != right.charAt(posLeft + offset)) {
          match = false;
          break;
        }
      }
      if (match) {
        if (Math.abs(bestOffset) > Math.abs(offset)) bestOffset = offset;
      }
    }
    if (bestOffset < Integer.MAX_VALUE) {
      String combined = left;
      if (bestOffset > 0) {
        combined = right.substring(0, bestOffset) + combined;
      }
      if (left.length() + bestOffset < right.length()) {
        combined = combined + right.substring(left.length() + bestOffset);
      }
      return combined;
    }
    else {
      return null;
    }
  }
  
  private static double entropy(TrieNode tokenNode, TrieNode contextNode) {
    return -0.0 + (null == contextNode ? Double.POSITIVE_INFINITY : (-Math.log(tokenNode.getCursorCount() * 1.0 / contextNode.getCursorCount())));
  }
  
  /**
   * Keywords list.
   *
   * @param source the source
   * @return the list
   */
  public List<String> keywords(final String source) {
    Map<String, Long> wordCounts = splitChars(source, DEFAULT_THRESHOLD).stream().collect(Collectors.groupingBy(x -> x, Collectors.counting()));
    wordCounts = aggregateKeywords(wordCounts);
    return wordCounts.entrySet().stream().filter(x -> x.getValue() > 1)
      .sorted(Comparator.comparing(x -> -entropy(x.getKey()) * Math.pow(x.getValue(), 0.3)))
      .map(e -> {
        if (isVerbose()) {
          verbose.println(String.format("KEYWORD: \"%s\" - %s * %.3f / %s", e.getKey(), e.getValue(), entropy(e.getKey()), e.getKey().length()));
        }
        return e.getKey();
      }).collect(Collectors.toList());
  }
  
  private Map<String, Long> aggregateKeywords(Map<String, Long> wordCounts) {
    Map<String, Long> accumulator = new HashMap<>();
    wordCounts.forEach((key, count) -> {
      boolean added = false;
      for (Map.Entry<String, Long> e : accumulator.entrySet()) {
        String combine = combine(key, e.getKey(), 4);
        if (null != combine) {
          accumulator.put(combine, e.getValue() + count);
          accumulator.remove(e.getKey());
          added = true;
          break;
        }
      }
      if (!added) {
        accumulator.put(key, count);
      }
    });
    if (wordCounts.size() > accumulator.size()) {
      return aggregateKeywords(accumulator);
    }
    else {
      return accumulator;
    }
  }
  
  /**
   * Spelling double.
   *
   * @param source the source
   * @return the double
   */
  public double spelling(final String source) {
    assert (source.startsWith("|"));
    assert (source.endsWith("|"));
    WordSpelling original = new WordSpelling(source);
    WordSpelling corrected = IntStream.range(0, 1).mapToObj(i -> buildCorrection(original)).min(Comparator.comparingDouble(x -> x.sum)).get();
    return corrected.sum;
  }
  
  private WordSpelling buildCorrection(WordSpelling wordSpelling) {
    int timesWithoutImprovement = 0;
    int maxCorrections = 10;
    int trials = 10;
    if (null != verbose) verbose.println(String.format("START: \"%s\"\t%.5f", wordSpelling.text, wordSpelling.sum));
    while (timesWithoutImprovement++ < 100) {
      WordSpelling _wordSpelling = wordSpelling;
      ToDoubleFunction<WordSpelling> fitness = mutant -> mutant.sum * 1.0 / mutant.text.length();
      WordSpelling mutant = wordSpelling.mutate().filter(x -> {
        if (!x.text.startsWith("|")) return false;
        return x.text.endsWith("|");
      }).min(Comparator.comparingDouble(fitness::applyAsDouble)).get();
      if (fitness.applyAsDouble(mutant) < fitness.applyAsDouble(wordSpelling)) {
        if (null != verbose) verbose.println(String.format("IMPROVEMENT: \"%s\"\t%.5f", mutant.text, mutant.sum));
        wordSpelling = mutant;
        timesWithoutImprovement = 0;
        if (maxCorrections-- <= 0) break;
      }
      else {
        //if(null!=verbose) verbose.println(String.format("REJECT: \"%s\"\t%.5f", mutant.text, mutant.sum));
      }
      if (inner.contains(wordSpelling.text)) {
        if (null != verbose) verbose.println(String.format("WORD: \"%s\"\t%.5f", mutant.text, mutant.sum));
        break;
      }
    }
    return wordSpelling;
  }
  
  /**
   * Split matches list.
   *
   * @param text    the text
   * @param minSize the min size
   * @return the list
   */
  public List<String> splitMatches(String text, int minSize) {
    TrieNode node = inner.root();
    List<String> matches = new ArrayList<>();
    String accumulator = "";
    for (int i = 0; i < text.length(); i++) {
      short prevDepth = node.getDepth();
      TrieNode prevNode = node;
      node = node.getContinuation(text.charAt(i));
      if (null == node) node = inner.root();
      if (!accumulator.isEmpty() && (node.getDepth() < prevDepth || (prevNode.hasChildren() && node.getDepth() == prevDepth))) {
        if (accumulator.length() > minSize) {
          matches.add(accumulator);
          node = ((Optional<TrieNode>) inner.root().getChild(text.charAt(i))).orElse(inner.root());
        }
        accumulator = "";
      }
      else if (!accumulator.isEmpty()) {
        accumulator += text.charAt(i);
      }
      else if (accumulator.isEmpty() && node.getDepth() > prevDepth) {
        accumulator = node.getString();
      }
    }
    List<String> tokenization = new ArrayList<>();
    for (String match : matches) {
      int index = text.indexOf(match);
      assert (index >= 0);
      if (index > 0) tokenization.add(text.substring(0, index));
      tokenization.add(text.substring(index, index + match.length()));
      text = text.substring(index + match.length());
    }
    tokenization.add(text);
    return tokenization;
  }
  
  /**
   * Split chars list.
   *
   * @param source    the source
   * @param threshold the threshold
   * @return the list
   */
  public List<String> splitChars(final String source, double threshold) {
    List<String> output = new ArrayList<>();
    int wordStart = 0;
    double aposterioriNatsPrev = 0;
    boolean isIncreasing = false;
    double prevLink = 0;
    for (int i = 1; i < source.length(); i++) {
      String priorText = source.substring(0, i);
      TrieNode priorNode = getMaxentPrior(priorText);
      double aprioriNats = entropy(priorNode, priorNode.getParent());
      
      String followingText = source.substring(i - 1, source.length());
      TrieNode followingNode = getMaxentPost(followingText);
      TrieNode godparent = followingNode.godparent();
      double aposterioriNats = entropy(followingNode, godparent);
      
      //double jointNats = getJointNats(priorNode, followingNode);
      double linkNats = aprioriNats + aposterioriNatsPrev;
      if (isVerbose()) {
        verbose.println(String.format("%10s\t%10s\t%s",
          '"' + priorNode.getString().replaceAll("\n", "\\n") + '"',
          '"' + followingNode.getString().replaceAll("\n", "\\n") + '"',
          Arrays.asList(aprioriNats, aposterioriNats, linkNats
          ).stream().map(x -> String.format("%.4f", x)).collect(Collectors.joining("\t"))));
      }
      String word = i < 2 ? "" : source.substring(wordStart, i - 2);
      if (isIncreasing && linkNats < prevLink && prevLink > threshold && word.length() > 2) {
        wordStart = i - 2;
        output.add(word);
        if (isVerbose()) verbose.println(String.format("Recognized token \"%s\"", word));
        prevLink = linkNats;
        aposterioriNatsPrev = aposterioriNats;
        isIncreasing = false;
      }
      else {
        if (linkNats > prevLink) isIncreasing = true;
        prevLink = linkNats;
        aposterioriNatsPrev = aposterioriNats;
      }
    }
    return output;
  }
  
  private TrieNode getMaxentPost(String followingText) {
    TrieNode followingNode = this.inner.traverse(followingText);
    TrieNode godparent1 = followingNode.godparent();
    double aposterioriNats1 = entropy(followingNode, godparent1);
    while (followingText.length() > 1) {
      String followingText2 = followingText.substring(0, followingText.length() - 1);
      TrieNode followingNode2 = this.inner.traverse(followingText2);
      TrieNode godparent2 = followingNode2.godparent();
      double aposterioriNats2 = entropy(followingNode2, godparent2);
      if (aposterioriNats2 < aposterioriNats1) {
        aposterioriNats1 = aposterioriNats2;
        followingNode = followingNode2;
        followingText = followingText2;
      }
      else {
        break;
      }
    }
    return followingNode;
  }
  
  private TrieNode getMaxentPrior(String priorText) {
    TrieNode priorNode = this.inner.matchEnd(priorText);
    double aprioriNats1 = entropy(priorNode, priorNode.getParent());
    while (priorText.length() > 1) {
      String priorText2 = priorText.substring(1);
      TrieNode priorNode2 = this.inner.matchEnd(priorText2);
      double aprioriNats2 = entropy(priorNode2, priorNode2.getParent());
      if (aprioriNats2 < aprioriNats1) {
        aprioriNats1 = aprioriNats2;
        priorText = priorText2;
        priorNode = priorNode2;
      }
      else {
        break;
      }
    }
    return priorNode;
  }
  
  private double getJointNats(TrieNode priorNode, TrieNode followingNode) {
    Map<Character, Long> code = getJointExpectation(priorNode, followingNode);
    double sumOfProduct = code.values().stream().mapToDouble(x -> x).sum();
    double product = followingNode.getCursorCount() * priorNode.getCursorCount();
    return -Math.log(product / sumOfProduct);
  }
  
  private Map<Character, Long> getJointExpectation(TrieNode priorNode, TrieNode followingNode) {
    TrieNode priorParent = priorNode.getParent();
    TreeMap<Character, ? extends TrieNode> childrenMap = null == priorParent ? inner.root().getChildrenMap() : priorParent.getChildrenMap();
    String followingString = followingNode.getString();
    String postContext = followingString.isEmpty() ? "" : followingString.substring(1);
    return childrenMap.keySet().stream().collect(Collectors.toMap(x -> x, token -> {
      TrieNode altFollowing = inner.traverse(token + postContext);
      long a = altFollowing.getString().equals(token + postContext) ? altFollowing.getCursorCount() : 0;
      TrieNode parent = priorParent;
      long b = childrenMap.get(token).getCursorCount();
      return a * b;
    }));
  }
  
  /**
   * Entropy double.
   *
   * @param source the source
   * @return the double
   */
  public double entropy(final String source) {
    double output = 0;
    for (int i = 1; i < source.length(); i++) {
      TrieNode node = this.inner.matchEnd(source.substring(0, i));
      Optional<? extends TrieNode> child = node.getChild(source.charAt(i));
      while (!child.isPresent()) {
        output += Math.log(1.0 / node.getCursorCount());
        node = node.godparent();
        child = node.getChild(source.charAt(i));
      }
      output += Math.log(child.get().getCursorCount() * 1.0 / node.getCursorCount());
    }
    return -output / Math.log(2);
  }
  
  /**
   * Is verbose boolean.
   *
   * @return the boolean
   */
  public boolean isVerbose() {
    return null != verbose;
  }
  
  /**
   * Sets verbose.
   *
   * @param verbose the verbose
   * @return the verbose
   */
  public TextAnalysis setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }
  
  /**
   * Split chars list.
   *
   * @param text the text
   * @return the list
   */
  public List<String> splitChars(String text) {
    return splitChars(text, DEFAULT_THRESHOLD);
  }
  
  /**
   * The type Word spelling.
   */
  public class WordSpelling {
    private final double[] linkNatsArray;
    private final List<TrieNode> leftNodes;
    private final List<TrieNode> rightNodes;
    private final String text;
    private final Random random = new Random();
    /**
     * The Sum.
     */
    double sum = 0;
    
    /**
     * Instantiates a new Word spelling.
     *
     * @param source the source
     */
    public WordSpelling(final String source) {
      this.text = source;
      linkNatsArray = new double[source.length()];
      leftNodes = new ArrayList<>(source.length());
      rightNodes = new ArrayList<>(source.length());
      TrieNode priorNode = inner.root();
      double aposterioriNatsPrev = 0;
      for (int i = 1; i <= source.length(); i++) {
        priorNode = priorNode.getContinuation(source.charAt(i - 1));
        double aprioriNats = entropy(priorNode, priorNode.getParent());
        TrieNode followingNode = inner.traverse(source.substring(i - 1, source.length()));
        leftNodes.add(priorNode);
        rightNodes.add(followingNode);
        double aposterioriNats = entropy(followingNode, followingNode.godparent());
        Map<Character, Long> code = getJointExpectation(priorNode, followingNode);
        double sumOfProduct = code.values().stream().mapToDouble(x1 -> x1).sum();
        double product = followingNode.getCursorCount() * priorNode.getCursorCount();
        double jointNats = -Math.log(product / sumOfProduct);
        linkNatsArray[i - 1] = jointNats;
        sum += jointNats;
//        double linkNats = aprioriNats + aposterioriNatsPrev;
//        if(isVerbose()) verbose.println(String.format("%10s\t%10s\t%s",
//                '"' + priorNode.getString().replaceAll("\n","\\n") + '"',
//                '"' + followingNode.getString().replaceAll("\n","\\n") + '"',
//                Arrays.asList(aprioriNats, aposterioriNats, linkNats, jointNats
//                ).stream().apply(x->String.format("%.4f",x)).collect(Collectors.joining("\t"))));
        aposterioriNatsPrev = aposterioriNats;
      }
      double sumLinkNats = Arrays.stream(linkNatsArray).sum();
      for (int i = 0; i < linkNatsArray.length; i++) linkNatsArray[i] /= sumLinkNats;
    }
    
    /**
     * Mutate stream.
     *
     * @return the stream
     */
    public Stream<WordSpelling> mutate() {
      return IntStream.range(0, linkNatsArray.length).mapToObj(x -> x)
        .sorted(Comparator.comparingDouble(i1 -> linkNatsArray[i1]))
        .flatMap(i -> mutateAt(i));
//      double fate = Math.random();
//      for (int i=0;i<linkNatsArray.length;i++) {
//        fate -= linkNatsArray[i];
//        if(fate < 0) {
//          //if(null!=verbose) verbose.print("MUTATE at " + i);
//          return mutateAt(i);
//        }
//      }
//      return this;
    }
    
    private Stream<WordSpelling> mutateAt(int pos) {
      //int fate = random.nextInt(6);
      //if(null!=verbose) verbose.print(" operation#" + fate);
      return IntStream.range(0, 6).mapToObj(x -> x).flatMap(fate -> {
        if (fate == 0) {
          return mutateDeletion(pos);
        }
        else if (fate == 1) {
          return mutateSubstitution(pos);
        }
        else if (fate == 2) {
          return mutateAddLeft(pos);
        }
        else if (fate == 3) {
          return mutateAddRight(pos);
        }
        else if (fate == 4) {
          return mutateSwapLeft(pos);
        }
        else if (fate == 5) {
          return mutateSwapRight(pos);
        }
        else {
          return Stream.empty();
        }
      });
    }
    
    private Stream<WordSpelling> mutateSwapRight(int pos) {
      if (text.length() - 1 <= pos) return Stream.empty();
      char[] charArray = text.toCharArray();
      char temp = charArray[pos + 1];
      charArray[pos + 1] = charArray[pos];
      charArray[pos] = temp;
      //if(null!=verbose) verbose.println("  swap right");
      return Stream.of(new WordSpelling(new String(charArray)));
    }
    
    private Stream<WordSpelling> mutateSwapLeft(int pos) {
      if (0 >= pos) return Stream.empty();
      char[] charArray = text.toCharArray();
      char temp = charArray[pos - 1];
      charArray[pos - 1] = charArray[pos];
      charArray[pos] = temp;
      //if(null!=verbose) verbose.println("  swap categoryWeights");
      return Stream.of(new WordSpelling(new String(charArray)));
    }
    
    private Stream<WordSpelling> mutateAddRight(int pos) {
      Stream<Character> newCharStream = pick(getJointExpectation((text.length() - 1 <= pos) ? inner.root() : leftNodes.get(pos + 1), rightNodes.get(pos)));
      //if(null!=verbose) verbose.println("  mutate right: " + newChar);
      return newCharStream.map(newChar -> new WordSpelling(text.substring(0, pos) + newChar + text.substring(pos)));
    }
    
    private Stream<WordSpelling> mutateAddLeft(int pos) {
      Stream<Character> newCharStream = pick(getJointExpectation(leftNodes.get(pos), (0 >= pos) ? inner.root() : rightNodes.get(pos - 1)));
      //if(null!=verbose) verbose.println("  mutate categoryWeights: " + newChar);
      return newCharStream.map(newChar -> new WordSpelling(text.substring(0, pos) + newChar + text.substring(pos)));
    }
    
    private Stream<WordSpelling> mutateSubstitution(int pos) {
      Stream<Character> newCharStream = pick(getJointExpectation(leftNodes.get(pos), rightNodes.get(pos)));
      return newCharStream.map(newChar -> {
        char[] charArray = text.toCharArray();
        charArray[pos] = newChar;
        //if(null!=verbose) verbose.println("  mutate in place: " + newChar);
        return new WordSpelling(new String(charArray));
      });
    }
    
    private Stream<Character> pick(Map<Character, Long> weights) {
      return weights.entrySet().stream().sorted(Comparator.comparingLong(e -> e.getValue())).map(e -> e.getKey());
    }
    
    private Stream<WordSpelling> mutateDeletion(int pos) {
      //if(null!=verbose) verbose.println("  deletion");
      return Stream.of(new WordSpelling(text.substring(0, pos) + text.substring(pos + 1)));
    }
    
    
  }
}
