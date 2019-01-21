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

import com.simiacryptus.util.binary.BitInputStream;
import com.simiacryptus.util.binary.BitOutputStream;
import com.simiacryptus.util.binary.Bits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The type Convolutional trie serializer.
 */
public class ConvolutionalTrieSerializer {
  private PrintStream verbose = null;

  /**
   * Serialize byte [ ].
   *
   * @param charTrie the char trie
   * @return the byte [ ]
   */
  public byte[] serialize(CharTrie charTrie) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      try (BitOutputStream out = new BitOutputStream(buffer)) {
        int level = 0;
        while (serialize(charTrie.root(), out, level++) > 0) {
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return buffer.toByteArray();
  }

  private int serialize(TrieNode root, BitOutputStream out, int level) {
    AtomicInteger nodesWritten = new AtomicInteger(0);
    if (0 == level) {
      TreeMap<Character, ? extends TrieNode> children = root.getChildrenMap();
      try {
        int size = children.size();
        out.writeVarLong(size);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      children.forEach((token, child) -> {
        try {
          if (null != verbose) verbose.println(String.format("Write token %s", child.getChar()));
          out.write(child.getChar());
          out.writeVarLong(null == child ? 0 : child.getCursorCount());
          nodesWritten.incrementAndGet();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    } else {
      HashMap<CharSequence, Integer> godchildCounters = new HashMap<>();
      root.streamDecendents(level).forEach(node -> {
        AtomicLong nodeCounter = new AtomicLong();
        TrieNode godparent = node.getDepth() == 0 ? root : node.godparent();
        TreeMap<Character, ? extends TrieNode> godchildren = godparent.getChildrenMap();
        TreeMap<Character, ? extends TrieNode> children = node.getChildrenMap();
        try {
          out.writeBoundedLong(children.size(), godchildren.size());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        if (null != verbose) {
          verbose.println(String.format("Recursing %s apply godparent %s and %s of %s potential children %s", node.getDebugString(), godparent.getDebugString(), children.size(), godchildren.size(), godchildren.keySet()));
        }
        godchildren.forEach((token, godchild) -> {
          int godchildAdj = godchildCounters.getOrDefault(godchild.getDebugString(), 0);
          TrieNode child = children.get(token);
          try {
            long upperBound = getUpperBound(node, nodeCounter, godchild, godchildAdj);
            if (upperBound > 0) {
              if (null == child) {
                if (null != verbose) {
                  verbose.println(String.format("Write ZERO token %s", node.getDebugString() + godchild.getDebugToken()));
                }
                out.write(Bits.ZERO);
              } else {
                out.write(Bits.ONE);
                long childCount = child.getCursorCount();
                assert (childCount <= upperBound);
                assert (childCount > 0);
                Bits bits = out.writeBoundedLong(childCount, upperBound);
                if (null != verbose) {
                  verbose.println(String.format("Write token %s = %s/%s -> %s", node.getDebugString() + godchild.getDebugToken(), childCount, upperBound, bits));
                }
                nodesWritten.incrementAndGet();
                nodeCounter.addAndGet(childCount);
                godchildCounters.put(godchild.getDebugString(), (int) (godchildAdj + childCount));
                //godchild.decrementCursorCount(childCount);
              }
            } else {
              if (null != verbose) {
                verbose.println(String.format("Implicit ZERO token %s", node.getDebugString() + godchild.getDebugToken()));
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      });
    }
    return nodesWritten.get();

  }

  /**
   * Deserialize char trie.
   *
   * @param bytes the bytes
   * @return the char trie
   */
  public CharTrie deserialize(byte[] bytes) {
    CharTrie trie = new CharTrie();
    BitInputStream in = new BitInputStream(new ByteArrayInputStream(bytes));
    int level = 0;
    while (deserialize(trie.root(), in, level++) > 0) {
    }
    trie.recomputeCursorDetails();
    return trie;
  }

  private long deserialize(TrieNode root, BitInputStream in, int level) {
    AtomicLong nodesRead = new AtomicLong(0);
    if (0 == level) {
      try {
        long numberOfChildren = in.readVarLong();
        TreeMap<Character, Long> children = new TreeMap<>();
        for (int i = 0; i < numberOfChildren; i++) {
          char c = (char) in.read(16).toLong();
          long cnt = in.readVarLong();
          if (null != verbose) verbose.println(String.format("Read char %s = %s", c, cnt));
          children.put(c, cnt);
          nodesRead.incrementAndGet();
        }
        root.writeChildren(children);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      HashMap<CharSequence, Integer> godchildCounters = new HashMap<>();
      root.streamDecendents(level).forEach(node -> {
        AtomicLong nodeCounter = new AtomicLong();
        TrieNode godparent = node.getDepth() == 0 ? root : node.godparent();
        assert (1 >= node.getDepth() || node.getString().substring(1).equals(godparent.getString()));
        TreeMap<Character, ? extends TrieNode> godchildren = godparent.getChildrenMap();
        TreeMap<Character, Long> children = new TreeMap<>();
        final long numberOfChildren;
        try {
          numberOfChildren = in.readBoundedLong(godchildren.size());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        if (null != verbose) {
          verbose.println(String.format("Recursing %s apply godparent %s and %s of %s potential children %s", node.getDebugString(), godparent.getDebugString(), numberOfChildren, godchildren.size(), godchildren.keySet()));
        }
        godchildren.forEach((token, godchild) -> {
          try {
            int godchildAdj = godchildCounters.getOrDefault(godchild.getDebugString(), 0);
            long upperBound = getUpperBound(node, nodeCounter, godchild, godchildAdj);
            if (upperBound > 0) {
              if (!in.readBool()) {
                if (null != verbose) {
                  verbose.println(String.format("Read ZERO token %s, input buffer = %s", node.getDebugString() + godchild.getDebugToken(), in.peek(24)));
                }
              } else {
                long childCount = in.readBoundedLong(upperBound);
                if (null != verbose) {
                  verbose.println(String.format("Read token %s = %s/%s, input buffer = %s", node.getDebugString() + godchild.getDebugToken(), childCount, upperBound, in.peek(24)));
                }
                assert (childCount >= 0);
                children.put(token, childCount);
                nodesRead.incrementAndGet();
                nodeCounter.addAndGet((int) childCount);
                godchildCounters.put(godchild.getDebugString(), (int) (godchildAdj + childCount));
              }
            } else {
              if (null != verbose) {
                verbose.println(String.format("Implicit ZERO token %s, input buffer = %s", node.getDebugString() + godchild.getDebugToken(), in.peek(24)));
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        node.writeChildren(children);
      });
    }

    return nodesRead.get();
  }

  /**
   * Gets upper bound.
   *
   * @param currentParent      the current parent
   * @param currentChildren    the current children
   * @param godchildNode       the godchild node
   * @param godchildAdjustment the godchild adjustment
   * @return the upper bound
   */
  protected long getUpperBound(TrieNode currentParent, AtomicLong currentChildren, TrieNode godchildNode, int godchildAdjustment) {
    return Math.min(
        currentParent.getCursorCount() - currentChildren.get(),
        godchildNode.getCursorCount() - godchildAdjustment);
  }

  /**
   * Gets verbose.
   *
   * @return the verbose
   */
  public PrintStream getVerbose() {
    return verbose;
  }

  /**
   * Sets verbose.
   *
   * @param verbose the verbose
   * @return the verbose
   */
  public ConvolutionalTrieSerializer setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }
}
