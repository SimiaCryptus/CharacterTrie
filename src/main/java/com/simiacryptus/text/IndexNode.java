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

import com.simiacryptus.ref.lang.RefAware;
import com.simiacryptus.ref.wrappers.*;
import com.simiacryptus.util.data.SerialArrayList;

import java.util.Map;
import java.util.Optional;

public @RefAware
class IndexNode extends TrieNode {

  public IndexNode(CharTrie trie, short depth, int index, TrieNode parent) {
    super(trie, index, parent);
  }

  public IndexNode(CharTrie trie, int index) {
    super(trie, index);
  }

  @Override
  public RefStream<? extends IndexNode> getChildren() {
    if (getData().firstChildIndex >= 0) {
      return RefIntStream.range(0, getData().numberOfChildren)
          .mapToObj(i -> new IndexNode(this.trie, (short) (getDepth() + 1), getData().firstChildIndex + i, this));
    } else {
      return RefStream.empty();
    }
  }

  public RefStream<Cursor> getCursors() {
    return RefLongStream.range(0, getData().cursorCount).mapToObj(i -> {
      return new Cursor((CharTrieIndex) this.trie,
          ((CharTrieIndex) this.trie).cursors.get((int) (i + getData().firstCursorIndex)), getDepth());
    });
  }

  public RefMap<CharSequence, RefList<Cursor>> getCursorsByDocument() {
    return this.getCursors()
        .collect(RefCollectors.groupingBy((Cursor x) -> x.getDocument()));
  }

  public TrieNode split() {
    if (getData().firstChildIndex < 0) {
      RefTreeMap<Character, SerialArrayList<CursorData>> sortedChildren = new RefTreeMap<>(
          getCursors().parallel()
              .collect(RefCollectors.groupingBy(y -> y.next().getToken(),
                  RefCollectors.reducing(new SerialArrayList<>(CursorType.INSTANCE, 0),
                      cursor -> new SerialArrayList<>(CursorType.INSTANCE, cursor.data),
                      (left, right) -> left.add(right)))));
      long cursorWriteIndex = getData().firstCursorIndex;
      //System.err.println(String.format("Splitting %s into children: %s", getDebugString(), sortedChildren.keySet()));
      RefArrayList<NodeData> childNodes = new RefArrayList<>(
          sortedChildren.size());
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
      return new IndexNode(this.trie, getDepth(), index, getParent());
    } else {
      return this;
    }
  }

  @Override
  public IndexNode godparent() {
    return (IndexNode) super.godparent();
  }

  @Override
  public IndexNode refresh() {
    return (IndexNode) super.refresh();
  }

  public IndexNode visitFirstIndex(RefConsumer<? super IndexNode> visitor) {
    visitor.accept(this);
    IndexNode refresh = refresh();
    refresh.getChildren().forEach(n -> n.visitFirstIndex(visitor));
    return refresh;
  }

  public IndexNode visitLastIndex(RefConsumer<? super IndexNode> visitor) {
    getChildren().forEach(n -> n.visitLastIndex(visitor));
    visitor.accept(this);
    return refresh();
  }

  @Override
  public Optional<? extends IndexNode> getChild(char token) {
    NodeData data = getData();
    int min = data.firstChildIndex;
    int max = data.firstChildIndex + data.numberOfChildren - 1;
    while (min <= max) {
      int i = (min + max) / 2;
      IndexNode node = new IndexNode(this.trie, (short) (getDepth() + 1), i, this);
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

  @Override
  public IndexNode traverse(String str) {
    return (IndexNode) super.traverse(str);
  }

  @Override
  public IndexNode traverse(long cursorId) {
    return (IndexNode) super.traverse(cursorId);
  }

  @Override
  protected TrieNode newNode(int index) {
    return new IndexNode(trie, index);
  }
}