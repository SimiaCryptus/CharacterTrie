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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The type Text generator.
 */
public class TextGenerator {

  private final CharTrie inner;

  /**
   * Instantiates a new Text generator.
   *
   * @param inner the inner
   */
  TextGenerator(CharTrie inner) {
    this.inner = inner;
  }

  /**
   * Generate markov string.
   *
   * @param length  the length
   * @param context the context
   * @param seed    the seed
   * @return the string
   */
  public String generateMarkov(int length, int context, String seed) {
    String str = seed;
    while (str.length() < length) {
      String prefix = str.substring(Math.max(str.length() - context, 0), str.length());
      TrieNode node = inner.matchPredictor(prefix);
      long cursorCount = node.getCursorCount();
      long fate = CompressionUtil.random.nextLong() % cursorCount;
      String next = null;
      Stream<TrieNode> stream = node.getChildren().map(x -> x);
      List<TrieNode> children = stream.collect(Collectors.toList());
      for (TrieNode child : children) {
        fate -= child.getCursorCount();
        if (fate <= 0) {
          if (child.getChar() != NodewalkerCodec.END_OF_STRING) {
            next = child.getToken();
          }
          break;
        }
      }
      if (null != next) {
        str += next;
      } else {
        break;
      }
    }
    return str;
  }

  /**
   * Generate dictionary string.
   *
   * @param length      the length
   * @param context     the context
   * @param seed        the seed
   * @param lookahead   the lookahead
   * @param destructive the destructive
   * @return the string
   */
  public String generateDictionary(int length, int context, final String seed, int lookahead, boolean destructive) {
    return generateDictionary(length, context, seed, lookahead, destructive, false);
  }

  /**
   * Generate dictionary string.
   *
   * @param length          the length
   * @param context         the context
   * @param seed            the seed
   * @param lookahead       the lookahead
   * @param destructive     the destructive
   * @param terminateAtNull the terminate at null
   * @return the string
   */
  public String generateDictionary(int length, int context, final String seed, int lookahead, boolean destructive, boolean terminateAtNull) {
    String str = seed;
    String prefix = "";
    while (str.length() < length) {
      TrieNode node = prefix.isEmpty() ? inner.root() : inner.matchPredictor(prefix);
      if (null == node) {
        prefix = prefix.substring(1);
      }
      TrieNode nextNode = maxNextNode(node, lookahead);
      if (null == nextNode) break;
      if (destructive) nextNode.removeCursorCount();
      String next = nextNode.getString(node);
      str += next;
      prefix = str.substring(Math.max(str.length() - context, 0), str.length());
      if (next.isEmpty()) {
        if (prefix.isEmpty()) {
          break;
        } else {
          prefix = prefix.substring(1);
        }
      }
      if (nextNode.getChar() == NodewalkerCodec.END_OF_STRING) {
        if (terminateAtNull) {
          break;
        } else {
          prefix = "";
        }
      }
    }
    return str.substring(0, Math.min(length, str.length()));
  }

  private Map<Character, Double> lookahead(TrieNode node, double smoothness) {
    HashMap<Character, Double> map = new HashMap<>();
    lookahead(node, map, 1.0, smoothness);
    return map;
  }

  private void lookahead(TrieNode node, HashMap<Character, Double> map, double factor, double smoothness) {
    if (0 < factor) {
      node.getChildren().forEach(child -> {
        map.put(child.getChar(), factor * child.getCursorCount() + map.getOrDefault(child.getToken(), 0.0));
      });
      if (null != node.getParent()) {
        lookahead(inner.matchPredictor(node.getString().substring(1)), map,
            factor * (smoothness / (smoothness + node.getCursorCount())), smoothness);
      }
    }
  }

  private TrieNode maxNextNode(TrieNode node, int lookahead) {
    Stream<TrieNode> childStream = node.getChildren().map(x -> x);
    for (int level = 0; level < lookahead; level++) {
      childStream = childStream.flatMap(child -> child.hasChildren() ? child.getChildren() : Stream.of(child));
    }
    TrieNode result = childStream.max(Comparator.comparingLong(x -> x.getCursorCount())).orElse(null);
    if (null == result) {
      if (lookahead > 0) {
        return maxNextNode(node, lookahead - 1);
      }
      TrieNode godparent = node.godparent();
      if (null != godparent) {
        return maxNextNode(godparent, lookahead);
      }
    }
    return result;
  }

}
