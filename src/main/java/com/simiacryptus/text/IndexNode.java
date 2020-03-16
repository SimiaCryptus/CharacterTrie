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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class IndexNode extends TrieNode {

  public IndexNode(CharTrie trie, int index, TrieNode parent) {
    super(trie, index, parent);
  }

  public IndexNode(@Nonnull CharTrie trie, int index) {
    super(trie, index);
  }

  @Nonnull
  @Override
  public Stream<? extends IndexNode> getChildren() {
    if (getData().firstChildIndex >= 0) {
      return IntStream.range(0, getData().numberOfChildren)
          .mapToObj(i -> new IndexNode(this.trie, getData().firstChildIndex + i, this));
    } else {
      return Stream.empty();
    }
  }

  @Nonnull
  public Stream<Cursor> getCursors() {
    return LongStream.range(0, getData().cursorCount).mapToObj(i -> {
      return new Cursor((CharTrieIndex) this.trie,
          ((CharTrieIndex) this.trie).cursors.get((int) (i + getData().firstCursorIndex)), getDepth());
    });
  }

  public Map<CharSequence, List<Cursor>> getCursorsByDocument() {
    return this.getCursors().collect(Collectors.groupingBy((Cursor x) -> x.getDocument()));
  }

  @Nullable
  public TrieNode split() {
    if (getData().firstChildIndex < 0) {
      TreeMap<Character, SerialArrayList<CursorData>> sortedChildren = new TreeMap<>(getCursors().parallel()
          .collect(Collectors.groupingBy(y -> y.next().getToken(),
              Collectors.reducing(new SerialArrayList<>(CursorType.INSTANCE, 0),
                  cursor -> new SerialArrayList<>(CursorType.INSTANCE, cursor.data),
                  (left, right) -> left.add(right)))));
      long cursorWriteIndex = getData().firstCursorIndex;
      //com.simiacryptus.ref.wrappers.System.err.println(String.format("Splitting %s into children: %s", getDebugString(), sortedChildren.keySet()));
      ArrayList<NodeData> childNodes = new ArrayList<>(sortedChildren.size());
      for (Map.Entry<Character, SerialArrayList<CursorData>> e : sortedChildren.entrySet()) {
        int length = e.getValue().length();
        ((CharTrieIndex) this.trie).cursors.putAll(e.getValue(), (int) cursorWriteIndex);
        childNodes.add(new NodeData(e.getKey(), (short) -1, -1, length, cursorWriteIndex));
        cursorWriteIndex += length;
      }
      int firstChildIndex = this.trie.nodes.addAll(childNodes);
      short size = (short) childNodes.size();
      trie.ensureParentIndexCapacity(firstChildIndex, size, index);
      this.trie.nodes.update(index, data -> {
        return data.setFirstChildIndex(firstChildIndex).setNumberOfChildren(size);
      });
      return new IndexNode(this.trie, index, getParent());
    } else {
      return this;
    }
  }

  @Nullable
  @Override
  public IndexNode godparent() {
    return (IndexNode) super.godparent();
  }

  @Nonnull
  @Override
  public IndexNode refresh() {
    return (IndexNode) super.refresh();
  }

  @Nonnull
  public IndexNode visitFirstIndex(@Nonnull Consumer<? super IndexNode> visitor) {
    visitor.accept(this);
    IndexNode refresh = refresh();
    refresh.getChildren().forEach(n -> n.visitFirstIndex(visitor));
    return refresh;
  }

  @Nonnull
  public IndexNode visitLastIndex(@Nonnull Consumer<? super IndexNode> visitor) {
    getChildren().forEach(n -> n.visitLastIndex(visitor));
    visitor.accept(this);
    return refresh();
  }

  @Override
  public Optional<? extends IndexNode> getChild(char token) {
    NodeData data = getData();
    assert data != null;
    int min = data.firstChildIndex;
    int max = data.firstChildIndex + data.numberOfChildren - 1;
    while (min <= max) {
      int i = (min + max) / 2;
      IndexNode node = new IndexNode(this.trie, i, this);
      char c = node.getChar();
      int compare = Character.compare(c, token);
      if (c < token) {
        // node.getChar() < token
        min = i + 1;
      } else if (c > token) {
        // node.getChar() > token
        max = i - 1;
      } else {
        return Optional.of(node);
      }
    }
    //assert !getChildren().keywords(x -> x.getChar() == token).findFirst().isPresent();
    return Optional.empty();
  }

  @Nonnull
  @Override
  public IndexNode traverse(@Nonnull String str) {
    return (IndexNode) super.traverse(str);
  }

  @Nonnull
  @Override
  public IndexNode traverse(long cursorId) {
    return (IndexNode) super.traverse(cursorId);
  }

  @Nonnull
  @Override
  protected TrieNode newNode(int index) {
    return new IndexNode(trie, index);
  }
}