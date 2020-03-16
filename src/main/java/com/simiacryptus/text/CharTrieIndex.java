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

import com.simiacryptus.util.data.SerialArrayList;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CharTrieIndex extends CharTrie {

  protected final SerialArrayList<CursorData> cursors;
  protected final ArrayList<CharSequence> documents;

  private CharTrieIndex(SerialArrayList<NodeData> nodes, SerialArrayList<CursorData> cursors,
                        ArrayList<CharSequence> documents) {
    super(nodes);
    this.cursors = cursors;
    this.documents = documents;
  }

  public CharTrieIndex(@Nonnull CharTrieIndex copyFrom) {
    this(copyFrom.nodes.copy(), copyFrom.cursors.copy(), new ArrayList<>(copyFrom.documents));
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

  @Nonnull
  public static CharTrie indexWords(@Nonnull Collection<CharSequence> documents, int maxLevels, int minWeight) {
    return create(documents, maxLevels, minWeight, true);
  }

  @Nonnull
  public static CharTrie indexFulltext(@Nonnull Collection<CharSequence> documents, int maxLevels, int minWeight) {
    return create(documents, maxLevels, minWeight, false);
  }

  @Nonnull
  public static CharTrie create(@Nonnull Collection<CharSequence> documents, int maxLevels, int minWeight, boolean words) {
    return create(documents, maxLevels, minWeight, getCursorInit(words));
  }

  @Nonnull
  public static CharTrie create(@Nonnull Collection<CharSequence> documents, int maxLevels, int minWeight, @NotNull Function<CharSequence, IntStream> cursorSeeds) {
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
        trie.addDocument(s, cursorSeeds.apply(s));
      });
      trie.index(maxLevels, minWeight);
      return (CharTrie) trie;
    }).reduce((l, r) -> l.add(r)).get();
  }

  @NotNull
  public static Function<CharSequence, IntStream> getCursorInit(boolean words) {
    Function<CharSequence, IntStream> cursorSeeds;
    if (words) {
      cursorSeeds = doc -> IntStream.range(0, 1);
    } else {
      cursorSeeds = doc -> IntStream.range(0, doc.length() + 1);
    }
    return cursorSeeds;
  }

  @Nonnull
  public CharTrie truncate() {
    return new CharTrie(this);
  }

  @Nonnull
  public CharTrieIndex index() {
    return index(Integer.MAX_VALUE);
  }

  @Nonnull
  public CharTrieIndex index(int maxLevels) {
    return index(maxLevels, 0);
  }

  @Nonnull
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
    return addDocument(document, IntStream.range(0, 1));
  }

  public int addDocument(@Nonnull CharSequence document) {
    return addDocument(document, IntStream.range(0, document.length() + 1));
  }

  public int addDocument(@Nonnull CharSequence document, IntStream cursorSeeds) {
    if (root().getNumberOfChildren() >= 0) {
      throw new IllegalStateException("Tree sorting has begun");
    }
    final int index;
    synchronized (this) {
      index = documents.size();
      documents.add(document);
    }
    cursors.addAll(cursorSeeds.mapToObj(i -> new CursorData(index, i))
        .collect(Collectors.toList()));
    nodes.update(0, node -> node.setCursorCount(cursors.length()));
    return index;
  }

  @Nonnull
  public CharTrie addAlphabet(@Nonnull CharSequence document) {
    document.chars().mapToObj(i -> new String(Character.toChars(i))).forEach(s -> addDocument(s));
    return this;
  }

  @Nonnull
  public CharTrieIndex copy() {
    return new CharTrieIndex(this);
  }

  @Nullable
  @Override
  public IndexNode root() {
    return new IndexNode(this, 0, null);
  }

  @Nonnull
  @Override
  public IndexNode traverse(@Nonnull String search) {
    return root().traverse(search);
  }

  @Nonnull
  @Override
  CharTrieIndex recomputeCursorDetails() {
    return (CharTrieIndex) super.recomputeCursorDetails();
  }

}