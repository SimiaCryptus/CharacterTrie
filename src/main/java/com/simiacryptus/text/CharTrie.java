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

import com.google.common.collect.Iterators;
import com.simiacryptus.util.data.SerialArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.simiacryptus.text.NodewalkerCodec.*;

public class CharTrie {
  protected final SerialArrayList<NodeData> nodes;
  @Nullable
  protected int[] parentIndex = null;
  @Nullable
  protected int[] godparentIndex = null;

  public CharTrie(SerialArrayList<NodeData> nodes) {
    super();
    this.nodes = nodes;
  }

  public CharTrie() {
    this(new SerialArrayList<>(NodeType.INSTANCE, new NodeData(END_OF_STRING, (short) -1, -1, -1, 0)));
  }

  public CharTrie(@Nonnull CharTrie charTrie) {
    this(charTrie.nodes.copy());
    this.parentIndex = null == charTrie.parentIndex ? null
        : Arrays.copyOf(charTrie.parentIndex, charTrie.parentIndex.length);
    this.godparentIndex = null == charTrie.godparentIndex ? null
        : Arrays.copyOf(charTrie.godparentIndex, charTrie.godparentIndex.length);
  }

  @Nonnull
  public TextAnalysis getAnalyzer() {
    return new TextAnalysis(this.truncate().copy());
  }

  @Nonnull
  public NodewalkerCodec getCodec() {
    return new NodewalkerCodec(this);
  }

  @Nonnull
  public TextGenerator getGenerator() {
    return new TextGenerator(this.truncate().copy());
  }

  public long getIndexedSize() {
    return this.nodes.get(0).cursorCount;
  }

  public int getMemorySize() {
    return this.nodes.getMemorySize();
  }

  public int getNodeCount() {
    return nodes.length();
  }

  @Nonnull
  public static BiFunction<CharTrie, CharTrie, CharTrie> reducer(
      @Nonnull BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    return (left, right) -> left.reduce(right, fn);
  }

  @Nullable
  public TrieNode root() {
    return new TrieNode(this, 0, null);
  }

  @Nonnull
  public CharTrie reverse() {
    CharTrie result = new CharTrieIndex();
    TreeMap<Character, ? extends TrieNode> childrenMap = root().getChildrenMap();
    reverseSubtree(childrenMap, result.root());
    return result.recomputeCursorDetails();
  }

  @Nonnull
  public CharTrie rewrite(@Nonnull BiFunction<TrieNode, Map<Character, TrieNode>, TreeMap<Character, Long>> fn) {
    CharTrie result = new CharTrieIndex();
    rewriteSubtree(root(), result.root(), fn);
    return result.recomputeCursorDetails();
  }

  @Nonnull
  public CharTrie add(@Nonnull CharTrie z) {
    return reduceSimple(z, (left, right) -> (null == left ? 0 : left) + (null == right ? 0 : right));
  }

  @Nonnull
  public CharTrie product(@Nonnull CharTrie z) {
    return reduceSimple(z, (left, right) -> (null == left ? 0 : left) * (null == right ? 0 : right));
  }

  @Nonnull
  public CharTrie divide(@Nonnull CharTrie z, int factor) {
    return reduceSimple(z, (left, right) -> null == right ? 0 : (null == left ? 0 : left) * factor / right);
  }

  @Nonnull
  public CharTrie reduceSimple(@Nonnull CharTrie z, @Nonnull BiFunction<Long, Long, Long> fn) {
    return reduce(z, (left, right) -> {
      TreeMap<Character, ? extends TrieNode> leftChildren = null == left ? new TreeMap<>() : left.getChildrenMap();
      TreeMap<Character, ? extends TrieNode> rightChildren = null == right ? new TreeMap<>() : right.getChildrenMap();
      Map<Character, Long> map = Stream.of(rightChildren.keySet(), leftChildren.keySet()).flatMap(x -> x.stream())
          .distinct().collect(Collectors.toMap(c -> c, (Character c) -> {
            assert null != c;
            TrieNode leftChild = leftChildren.get(c);
            Long l = null == leftChild ? null : leftChild.getCursorCount();
            TrieNode rightChild = rightChildren.get(c);
            Long r = null == rightChild ? null : rightChild.getCursorCount();
            return fn.apply(l, r);
          }));
      return new TreeMap<>(map);
    });
  }

  @Nonnull
  public CharTrie reduce(@Nonnull CharTrie right, @Nonnull BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    CharTrie result = new CharTrieIndex();
    reduceSubtree(root(), right.root(), result.root(), fn);
    return result.recomputeCursorDetails();
  }

  public TrieNode traverse(@Nonnull String search) {
    return root().traverse(search);
  }

  @Nullable
  public TrieNode matchEnd(@Nonnull String search) {
    if (search.isEmpty())
      return root();
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
      } else {
        max = Math.min(max, i - 1);
      }
      i = (3 * max + min) / 4;
    }
    if (winner < 0)
      return root();
    String matched = search.substring(search.length() - winner);
    return traverse(matched);
  }

  @Nullable
  public TrieNode matchPredictor(@Nonnull String search) {
    TrieNode cursor = matchEnd(search);
    assert cursor != null;
    if (cursor.getNumberOfChildren() > 0) {
      return cursor;
    }
    String string = cursor.getString();
    if (string.isEmpty())
      return null;
    return matchPredictor(string.substring(1));
  }

  @Nonnull
  public CharTrie copy() {
    return new CharTrie(this);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CharTrie charTrie = (CharTrie) o;

    return nodes.equals(charTrie.nodes);
  }

  @Override
  public int hashCode() {
    return nodes.hashCode();
  }

  public Set<Character> tokens() {
    return root().getChildrenMap().keySet().stream().filter(c -> c != END_OF_STRING && c != FALLBACK && c != ESCAPE)
        .collect(Collectors.toSet());
  }

  public boolean contains(@Nonnull String text) {
    return traverse(text).getString().endsWith(text);
  }

  public <T extends Comparable<T>> Stream<TrieNode> max(@Nonnull Function<TrieNode, T> fn, int maxResults) {
    return max(fn, maxResults, root());
  }

  synchronized void ensureParentIndexCapacity(int start, int length, int parentId) {
    int end = start + length;
    if (null == parentIndex) {
      parentIndex = new int[end];
      Arrays.fill(parentIndex, parentId);
    } else {
      int newLength = parentIndex.length;
      while (newLength < end)
        newLength *= 2;
      if (newLength > parentIndex.length) {
        parentIndex = Arrays.copyOfRange(parentIndex, 0, newLength);
        Arrays.fill(parentIndex, end, newLength, -1);
      }
      Arrays.fill(parentIndex, start, end, parentId);
    }
    if (null == godparentIndex) {
      godparentIndex = new int[end];
      Arrays.fill(godparentIndex, -1);
    } else {
      int newLength = godparentIndex.length;
      while (newLength < end)
        newLength *= 2;
      if (newLength > godparentIndex.length) {
        int prevLength = godparentIndex.length;
        godparentIndex = Arrays.copyOfRange(godparentIndex, 0, newLength);
        Arrays.fill(godparentIndex, prevLength, newLength, -1);
      }
    }
  }

  @Nonnull
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

  @Nonnull
  protected CharTrie truncate() {
    return this;
  }

  private void reverseSubtree(@Nonnull TreeMap<Character, ? extends TrieNode> childrenMap, @Nonnull TrieNode destination) {
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

  private void rewriteSubtree(@Nonnull TrieNode sourceNode, @Nonnull TrieNode destNode,
                              @Nonnull BiFunction<TrieNode, Map<Character, TrieNode>, TreeMap<Character, Long>> fn) {
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

  @javax.annotation.Nullable
  private NodeData recomputeCursorTotals(@Nonnull TrieNode node) {
    assert parentIndex != null;
    parentIndex[node.index] = null == node.getParent() ? -1 : node.getParent().index;
    List<NodeData> newChildren = node.getChildren().map(child -> recomputeCursorTotals(child))
        .collect(Collectors.toList());
    if (newChildren.isEmpty())
      return node.getData();
    long cursorCount = newChildren.stream().mapToLong(n -> n.cursorCount).sum();
    assert 0 < cursorCount;
    return node.update(d -> d.setCursorCount(cursorCount));
  }

  private void recomputeCursorPositions(@Nonnull TrieNode node, final int position) {
    node.update(n -> n.setFirstCursorIndex(position));
    int childPosition = position;
    Stream<TrieNode> stream = node.getChildren().map(x -> x);
    for (TrieNode child : stream.collect(Collectors.toList())) {
      recomputeCursorPositions(child, childPosition);
      childPosition += child.getCursorCount();
    }
  }

  private void reduceSubtree(@Nullable TrieNode sourceNodeA, @Nullable TrieNode sourceNodeB, @Nonnull TrieNode destNode,
                             @Nonnull BiFunction<TrieNode, TrieNode, TreeMap<Character, Long>> fn) {
    destNode.writeChildren(fn.apply(sourceNodeA, sourceNodeB));
    TreeMap<Character, ? extends TrieNode> sourceChildrenA = null == sourceNodeA ? null : sourceNodeA.getChildrenMap();
    TreeMap<Character, ? extends TrieNode> sourceChildrenB = null == sourceNodeB ? null : sourceNodeB.getChildrenMap();
    destNode.getChildrenMap().forEach((key, newChild) -> {
      boolean containsA = null != sourceChildrenA && sourceChildrenA.containsKey(key);
      boolean containsB = null != sourceChildrenB && sourceChildrenB.containsKey(key);
      if (containsA && containsB) {
        reduceSubtree(sourceChildrenA.get(key), sourceChildrenB.get(key), newChild, fn);
      } else if (containsA) {
        reduceSubtree(sourceChildrenA.get(key), null, newChild, fn);
      } else if (containsB) {
        reduceSubtree(null, sourceChildrenB.get(key), newChild, fn);
      }
    });
  }

  private <T extends Comparable<T>> Stream<TrieNode> max(@Nonnull Function<TrieNode, T> fn, int maxResults, @Nonnull TrieNode node) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
        Iterators
            .mergeSorted(Stream.concat(Stream.of(Stream.of(node)), node.getChildren().map(x -> max(fn, maxResults, x)))
                .map(x -> x.iterator()).collect(Collectors.toList()), Comparator.comparing(fn).reversed()),
        Spliterator.ORDERED), false).limit(maxResults).collect(Collectors.toList()).stream();
  }
}
