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

import com.simiacryptus.ref.wrappers.RefCollectors;
import com.simiacryptus.ref.wrappers.RefIntStream;
import com.simiacryptus.util.data.SerialArrayList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public @com.simiacryptus.ref.lang.RefAware
class CharTrieIndex extends CharTrie {

  protected final SerialArrayList<CursorData> cursors;
  protected final ArrayList<CharSequence> documents;

  private CharTrieIndex(SerialArrayList<NodeData> nodes, SerialArrayList<CursorData> cursors,
                        ArrayList<CharSequence> documents) {
    super(nodes);
    this.cursors = cursors;
    this.documents = documents;
  }

  public CharTrieIndex(CharTrieIndex copyFrom) {
    this(copyFrom.nodes.copy(), copyFrom.cursors.copy(),
        new ArrayList<>(copyFrom.documents));

  }

  public CharTrieIndex() {
    this(new SerialArrayList<>(NodeType.INSTANCE, new NodeData(NodewalkerCodec.END_OF_STRING, (short) -1, -1, -1, 0)),
        new SerialArrayList<>(CursorType.INSTANCE), new ArrayList<>());
  }

  @Override
  public long getIndexedSize() {
    return documents.isEmpty() ? super.getIndexedSize() : documents.stream().mapToInt(doc -> doc.length()).sum();
  }

  @Override
  public int getMemorySize() {
    return cursors.getMemorySize() + nodes.getMemorySize();
  }

  public static CharTrie indexWords(Collection<CharSequence> documents, int maxLevels,
                                    int minWeight) {
    return create(documents, maxLevels, minWeight, true);
  }

  public static CharTrie indexFulltext(Collection<CharSequence> documents,
                                       int maxLevels, int minWeight) {
    return create(documents, maxLevels, minWeight, false);
  }

  private static CharTrie create(Collection<CharSequence> documents, int maxLevels,
                                 int minWeight, boolean words) {
    List<List<CharSequence>> a = new ArrayList<>();
    List<CharSequence> b = new ArrayList<>();
    int blockSize = 1024 * 1024;
    for (CharSequence s : documents) {
      b.add(s);
      if (b.stream().mapToInt(x -> x.length()).sum() > blockSize) {
        a.add(b);
        b = new ArrayList<>();
      }
    }
    a.add(b);
    return a.parallelStream().map(list -> {
      CharTrieIndex trie = new CharTrieIndex();
      list.forEach(s -> {
        if (words) {
          trie.addDictionary(s);
        } else {
          trie.addDocument(s);
        }
      });
      trie.index(maxLevels, minWeight);
      return (CharTrie) trie;
    }).reduce((l, r) -> l.add(r)).get();
  }

  public CharTrie truncate() {
    return new CharTrie(this);
  }

  public CharTrieIndex index() {
    return index(Integer.MAX_VALUE);
  }

  public CharTrieIndex index(int maxLevels) {
    return index(maxLevels, 0);
  }

  public CharTrieIndex index(int maxLevels, int minWeight) {

    AtomicInteger numberSplit = new AtomicInteger(0);
    int depth = -1;
    do {
      numberSplit.set(0);
      if (0 == ++depth) {
        numberSplit.incrementAndGet();
        root().split();
      } else {
        root().streamDecendents(depth).forEach(node -> {
          TrieNode godparent = node.godparent();
          if (node.getDepth() < maxLevels) {
            if (null == godparent || godparent.getCursorCount() > minWeight) {
              if (node.getChar() != NodewalkerCodec.END_OF_STRING || node.getDepth() == 0) {
                ((IndexNode) node).split();
                numberSplit.incrementAndGet();
              }
            }
          }
        });
      }
    } while (numberSplit.get() > 0);
    return this;
  }

  public int addDictionary(CharSequence document) {
    if (root().getNumberOfChildren() >= 0) {
      throw new IllegalStateException("Tree sorting has begun");
    }
    final int index;
    synchronized (this) {
      index = documents.size();
      documents.add(document);
    }
    cursors.addAll(RefIntStream.range(0, 1).mapToObj(i -> new CursorData(index, i))
        .collect(RefCollectors.toList()));
    nodes.update(0, node -> node.setCursorCount(cursors.length()));
    return index;
  }

  public int addDocument(CharSequence document) {
    if (root().getNumberOfChildren() >= 0) {
      throw new IllegalStateException("Tree sorting has begun");
    }
    final int index;
    synchronized (this) {
      index = documents.size();
      documents.add(document);
    }
    cursors.addAll(RefIntStream.range(0, document.length() + 1)
        .mapToObj(i -> new CursorData(index, i)).collect(RefCollectors.toList()));
    nodes.update(0, node -> node.setCursorCount(cursors.length()));
    return index;
  }

  public CharTrie addAlphabet(CharSequence document) {
    document.chars().mapToObj(i -> new String(Character.toChars(i))).forEach(s -> addDocument(s));
    return this;
  }

  public CharTrieIndex copy() {
    return new CharTrieIndex(this);
  }

  @Override
  public IndexNode root() {
    return new IndexNode(this, (short) 0, 0, null);
  }

  @Override
  public IndexNode traverse(String search) {
    return root().traverse(search);
  }

  @Override
  CharTrieIndex recomputeCursorDetails() {
    return (CharTrieIndex) super.recomputeCursorDetails();
  }

}