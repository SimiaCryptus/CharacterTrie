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

import com.google.common.collect.Iterators;
import com.simiacryptus.util.data.SerialArrayList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.simiacryptus.text.NodewalkerCodec.*;

/**
 * A character sequence index using a prefix tree, commonly known as a full-text index or as the data structure behind
 * markov chains. This implementation uses serialized fixed-length ephemeral objects and a raw byte data store,
 * preventing object/reference count overhead.
 */
public class CharTrie {
  /**
   * The Nodes.
   */
  protected final SerialArrayList<NodeData> nodes;
  /**
   * The Parent index.
   */
  protected int[] parentIndex = null;
  /**
   * The Godparent index.
   */
  protected int[] godparentIndex = null;
  
  /**
   * Instantiates a new Char trie.
   *
   * @param nodes the nodes
   */
  public CharTrie(SerialArrayList<NodeData> nodes) {
    super();
    this.nodes = nodes;
  }
  
  /**
   * Instantiates a new Char trie.
   */
  public CharTrie() {
    this(new SerialArrayList<>(NodeType.INSTANCE, new NodeData(END_OF_STRING, (short) -1, -1, -1, 0)));
  }
  
  /**
   * Instantiates a new Char trie.
   *
   * @param charTrie the char trie
   */
  public CharTrie(CharTrie charTrie) {
    this(charTrie.nodes.copy());
    this.parentIndex = null == charTrie.parentIndex ? null : Arrays.copyOf(charTrie.parentIndex, charTrie.parentIndex.length);
    this.godparentIndex = null == charTrie.godparentIndex ? null : Arrays.copyOf(charTrie.godparentIndex, charTrie.godparentIndex.length);
  }
  
  /**
   * Reducer bi function.
   *
   * @param fn the fn
   * @return the bi function
   */
  public static BiFunction<CharTrie, CharTrie, CharTrie> reducer(BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    return (left, right) -> left.reduce(right, fn);
  }
  
  /**
   * Root trie node.
   *
   * @return the trie node
   */
  public TrieNode root() {
    return new TrieNode(this, 0, null);
  }
  
  /**
   * Ensure parent index capacity.
   *
   * @param start    the start
   * @param length   the length
   * @param parentId the parent id
   */
  synchronized void ensureParentIndexCapacity(int start, int length, int parentId) {
    int end = start + length;
    if (null == parentIndex) {
      parentIndex = new int[end];
      Arrays.fill(parentIndex, parentId);
    }
    else {
      int newLength = parentIndex.length;
      while (newLength < end) newLength *= 2;
      if (newLength > parentIndex.length) {
        parentIndex = Arrays.copyOfRange(parentIndex, 0, newLength);
        Arrays.fill(parentIndex, end, newLength, -1);
      }
      Arrays.fill(parentIndex, start, end, parentId);
    }
    if (null == godparentIndex) {
      godparentIndex = new int[end];
      Arrays.fill(godparentIndex, -1);
    }
    else {
      int newLength = godparentIndex.length;
      while (newLength < end) newLength *= 2;
      if (newLength > godparentIndex.length) {
        int prevLength = godparentIndex.length;
        godparentIndex = Arrays.copyOfRange(godparentIndex, 0, newLength);
        Arrays.fill(godparentIndex, prevLength, newLength, -1);
      }
    }
  }
  
  /**
   * Reverse char trie.
   *
   * @return the char trie
   */
  public CharTrie reverse() {
    CharTrie result = new CharTrieIndex();
    TreeMap<Character, ? extends TrieNode> childrenMap = root().getChildrenMap();
    reverseSubtree(childrenMap, result.root());
    return result.recomputeCursorDetails();
  }
  
  private void reverseSubtree(TreeMap<Character, ? extends TrieNode> childrenMap, TrieNode destination) {
    String suffix = new StringBuilder(destination.getRawString()).reverse().toString();
    TreeMap<Character, Long> children = new TreeMap<>();
    childrenMap.forEach((token, node) -> {
      TrieNode analog = node.traverse(suffix);
      boolean found = (token + suffix).equals(analog.getRawString());
      if (found) {
        children.put(token, analog.getCursorCount());
      }
    });
    destination.writeChildren(children);
    destination.getChildren().forEach(child -> reverseSubtree(childrenMap, child));
  }
  
  /**
   * Rewrite char trie.
   *
   * @param fn the fn
   * @return the char trie
   */
  public CharTrie rewrite(BiFunction<TrieNode, Map<Character, TrieNode>, TreeMap<Character, Long>> fn) {
    CharTrie result = new CharTrieIndex();
    rewriteSubtree(root(), result.root(), fn);
    return result.recomputeCursorDetails();
  }
  
  private void rewriteSubtree(TrieNode sourceNode, TrieNode destNode, BiFunction<TrieNode, Map<Character, TrieNode>, TreeMap<Character, Long>> fn) {
    CharTrie result = destNode.getTrie();
    TreeMap<Character, ? extends TrieNode> sourceChildren = sourceNode.getChildrenMap();
    TreeMap<Character, Long> newCounts = fn.apply(sourceNode, (Map<Character, TrieNode>) sourceChildren);
    destNode.writeChildren(newCounts);
    TreeMap<Character, ? extends TrieNode> newChildren = destNode.getChildrenMap();
    newCounts.keySet().forEach(key -> {
      if (sourceChildren.containsKey(key)) {
        rewriteSubtree(sourceChildren.get(key), newChildren.get(key), fn);
      }
    });
  }
  
  /**
   * Add char trie.
   *
   * @param z the z
   * @return the char trie
   */
  public CharTrie add(CharTrie z) {
    return reduceSimple(z, (left, right) -> (null == left ? 0 : left) + (null == right ? 0 : right));
  }
  
  /**
   * Product char trie.
   *
   * @param z the z
   * @return the char trie
   */
  public CharTrie product(CharTrie z) {
    return reduceSimple(z, (left, right) -> (null == left ? 0 : left) * (null == right ? 0 : right));
  }
  
  /**
   * Divide char trie.
   *
   * @param z      the z
   * @param factor the factor
   * @return the char trie
   */
  public CharTrie divide(CharTrie z, int factor) {
    return reduceSimple(z, (left, right) -> (null == right ? 0 : ((null == left ? 0 : left) * factor / right)));
  }
  
  /**
   * Reduce simple char trie.
   *
   * @param z  the z
   * @param fn the fn
   * @return the char trie
   */
  public CharTrie reduceSimple(CharTrie z, BiFunction<Long, Long, Long> fn) {
    return reduce(z, (left, right) -> {
      TreeMap<Character, ? extends TrieNode> leftChildren = null == left ? new TreeMap<>() : left.getChildrenMap();
      TreeMap<Character, ? extends TrieNode> rightChildren = null == right ? new TreeMap<>() : right.getChildrenMap();
      Map<Character, Long> map = Stream.of(rightChildren.keySet(), leftChildren.keySet()).flatMap(x -> x.stream()).distinct().collect(Collectors.toMap(c -> c, (Character c) -> {
        assert (null != leftChildren);
        assert (null != rightChildren);
        assert (null != c);
        TrieNode leftChild = leftChildren.get(c);
        Long l = null == leftChild ? null : leftChild.getCursorCount();
        TrieNode rightChild = rightChildren.get(c);
        Long r = null == rightChild ? null : rightChild.getCursorCount();
        return fn.apply(l, r);
      }));
      return new TreeMap<>(map);
    });
  }
  
  /**
   * Reduce char trie.
   *
   * @param right the right
   * @param fn    the fn
   * @return the char trie
   */
  public CharTrie reduce(CharTrie right, BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    CharTrie result = new CharTrieIndex();
    reduceSubtree(root(), right.root(), result.root(), fn);
    return result.recomputeCursorDetails();
  }
  
  /**
   * Recompute cursor details char trie.
   *
   * @return the char trie
   */
  CharTrie recomputeCursorDetails() {
    godparentIndex = new int[getNodeCount()];
    parentIndex = new int[getNodeCount()];
    Arrays.fill(godparentIndex, 0, godparentIndex.length, -1);
    Arrays.fill(parentIndex, 0, parentIndex.length, -1);
    System.gc();
    recomputeCursorTotals(root());
    System.gc();
    recomputeCursorPositions(root(), 0);
    System.gc();
    return this;
  }
  
  private NodeData recomputeCursorTotals(TrieNode node) {
    parentIndex[node.index] = null == node.getParent() ? -1 : node.getParent().index;
    List<NodeData> newChildren = node.getChildren().map(child -> recomputeCursorTotals(child)).collect(Collectors.toList());
    if (newChildren.isEmpty()) return node.getData();
    long cursorCount = newChildren.stream().mapToLong(n -> n.cursorCount).sum();
    assert (0 < cursorCount);
    return node.update(d -> d.setCursorCount(cursorCount));
  }
  
  private void recomputeCursorPositions(TrieNode node, final int position) {
    node.update(n -> n.setFirstCursorIndex(position));
    int childPosition = position;
    Stream<TrieNode> stream = node.getChildren().map(x -> x);
    for (TrieNode child : stream.collect(Collectors.toList())) {
      recomputeCursorPositions(child, childPosition);
      childPosition += child.getCursorCount();
    }
  }
  
  private void reduceSubtree(TrieNode sourceNodeA, TrieNode sourceNodeB, TrieNode destNode, BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    destNode.writeChildren(fn.apply(sourceNodeA, sourceNodeB));
    TreeMap<Character, ? extends TrieNode> sourceChildrenA = null == sourceNodeA ? null : sourceNodeA.getChildrenMap();
    TreeMap<Character, ? extends TrieNode> sourceChildrenB = null == sourceNodeB ? null : sourceNodeB.getChildrenMap();
    destNode.getChildrenMap().forEach((key, newChild) -> {
      boolean containsA = null != sourceChildrenA && sourceChildrenA.containsKey(key);
      boolean containsB = null != sourceChildrenB && sourceChildrenB.containsKey(key);
      if (containsA && containsB) {
        reduceSubtree(sourceChildrenA.get(key), sourceChildrenB.get(key), newChild, fn);
      }
      else if (containsA) {
        reduceSubtree(sourceChildrenA.get(key), null, newChild, fn);
      }
      else if (containsB) {
        reduceSubtree(null, sourceChildrenB.get(key), newChild, fn);
      }
    });
  }
  
  /**
   * Locate a node by finding the maximum prefix match apply the given string
   *
   * @param search the search
   * @return trie node
   */
  public TrieNode traverse(String search) {
    return root().traverse(search);
  }
  
  /**
   * Gets node count.
   *
   * @return the node count
   */
  public int getNodeCount() {
    return nodes.length();
  }
  
  /**
   * Match end trie node.
   *
   * @param search the search
   * @return the trie node
   */
  public TrieNode matchEnd(String search) {
    if (search.isEmpty()) return root();
    int min = 0;
    int max = search.length();
    int i = Math.min(max, 12);
    int winner = -1;
    while (max > min) {
      String attempt = search.substring(search.length() - i);
      TrieNode cursor = traverse(attempt);
      if (cursor.getString().equals(attempt)) {
        min = Math.max(min, i + 1);
        winner = Math.max(winner, i);
      }
      else {
        max = Math.min(max, i - 1);
      }
      i = (3 * max + min) / 4;
    }
    if (winner < 0) return root();
    String matched = search.substring(search.length() - winner);
    return traverse(matched);
  }
  
  /**
   * Match predictor trie node.
   *
   * @param search the search
   * @return the trie node
   */
  public TrieNode matchPredictor(String search) {
    TrieNode cursor = matchEnd(search);
    if (cursor.getNumberOfChildren() > 0) {
      return cursor;
    }
    String string = cursor.getString();
    if (string.isEmpty()) return null;
    return matchPredictor(string.substring(1));
  }
  
  /**
   * Copy char trie.
   *
   * @return the char trie
   */
  public CharTrie copy() {
    return new CharTrie(this);
  }
  
  /**
   * Gets memory size.
   *
   * @return the memory size
   */
  public int getMemorySize() {
    return this.nodes.getMemorySize();
  }
  
  /**
   * Gets indexed size.
   *
   * @return the indexed size
   */
  public long getIndexedSize() {
    return this.nodes.get(0).cursorCount;
  }
  
  /**
   * Gets codec.
   *
   * @return the codec
   */
  public NodewalkerCodec getCodec() {
    return new NodewalkerCodec(this);
  }
  
  /**
   * Gets generator.
   *
   * @return the generator
   */
  public TextGenerator getGenerator() {
    return new TextGenerator(this.truncate().copy());
  }
  
  /**
   * Gets analyzer.
   *
   * @return the analyzer
   */
  public TextAnalysis getAnalyzer() {
    return new TextAnalysis(this.truncate().copy());
  }
  
  /**
   * Truncate char trie.
   *
   * @return the char trie
   */
  protected CharTrie truncate() {
    return this;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    
    CharTrie charTrie = (CharTrie) o;
    
    return nodes.equals(charTrie.nodes);
  }
  
  @Override
  public int hashCode() {
    return nodes.hashCode();
  }
  
  /**
   * Tokens set.
   *
   * @return the set
   */
  public Set<Character> tokens() {
    return root().getChildrenMap().keySet().stream()
      .filter(c -> c != END_OF_STRING && c != FALLBACK && c != ESCAPE)
      .collect(Collectors.toSet());
  }
  
  /**
   * Contains boolean.
   *
   * @param text the text
   * @return the boolean
   */
  public boolean contains(String text) {
    return traverse(text).getString().endsWith(text);
  }
  
  /**
   * Max stream.
   *
   * @param <T>        the type parameter
   * @param fn         the fn
   * @param maxResults the max results
   * @return the stream
   */
  public <T extends Comparable<T>> Stream<TrieNode> max(Function<TrieNode, T> fn, int maxResults) {
    return max(fn, maxResults, root());
  }
  
  private <T extends Comparable<T>> Stream<TrieNode> max(Function<TrieNode, T> fn, int maxResults, TrieNode node) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
      Iterators.mergeSorted(
        Stream.concat(
          Stream.of(Stream.of(node)),
          node.getChildren().map(x -> max(fn, maxResults, x))
        ).map(x -> x.iterator()).collect(Collectors.toList()),
        Comparator.comparing(fn).reversed()),
      Spliterator.ORDERED),
      false).limit(maxResults).collect(Collectors.toList()).stream();
  }
}
