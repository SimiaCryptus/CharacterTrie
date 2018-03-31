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

import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.binary.Interval;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * The type Trie node.
 */
public class TrieNode {
  /**
   * The Trie.
   */
  protected final CharTrie trie;
  /**
   * The Index.
   */
  protected final int index;
  private transient short depth = -1;
  private transient TrieNode parent = null;
  private transient NodeData data;
  
  /**
   * Instantiates a new Trie node.
   *
   * @param trie  the trie
   * @param index the index
   */
  public TrieNode(CharTrie trie, int index) {
    assert (0 <= index);
    assert (0 == index || trie.parentIndex[index] >= 0);
    this.trie = trie;
    this.index = index;
  }
  
  /**
   * Instantiates a new Trie node.
   *
   * @param trie   the trie
   * @param index  the index
   * @param parent the parent
   */
  public TrieNode(CharTrie trie, int index, TrieNode parent) {
    assert (0 <= index);
    this.trie = trie;
    this.index = index;
    this.parent = parent;
    //assert(null == trie.parentIndex || 0 == index || trie.parentIndex[index]>=0);
  }
  
  /**
   * Gets data.
   *
   * @return the data
   */
  NodeData getData() {
    if (null == data) {
      synchronized (this) {
        if (null == data) {
          this.data = this.trie.nodes.get(index);
        }
      }
    }
    return data;
  }
  
  /**
   * Godparent trie node.
   *
   * @return the trie node
   */
  public TrieNode godparent() {
    if (0 == getDepth()) return null;
    TrieNode root = trie.root();
    if (1 == getDepth()) return root;
    if (null != trie.godparentIndex && trie.godparentIndex.length > index) {
      int godparentIndex = trie.godparentIndex[this.index];
      if (godparentIndex >= 0) {
        return newNode(godparentIndex);
      }
    }
    TrieNode parent = this.getParent();
    TrieNode godparent;
    if (null == parent) {
      godparent = root;
    }
    else {
      TrieNode greatgodparent = parent.godparent();
      if (null == greatgodparent) {
        godparent = root;
      }
      else {
        godparent = greatgodparent.getChild(getChar())
          .map(x -> (TrieNode) x).orElseGet(() -> root);
      }
      //assert(getString().isEmpty() || getString().substring(1).equals(godparent.getString()));
    }
    if (null != godparent && null != trie.godparentIndex && trie.godparentIndex.length > index) {
      trie.godparentIndex[this.index] = godparent.index;
    }
    return godparent;
  }
  
  /**
   * New node trie node.
   *
   * @param index the index
   * @return the trie node
   */
  protected TrieNode newNode(int index) {
    return new TrieNode(trie, index);
  }
  
  /**
   * Refresh trie node.
   *
   * @return the trie node
   */
  public TrieNode refresh() {
    this.data = null;
    return this;
  }
  
  /**
   * Gets string.
   *
   * @param root the root
   * @return the string
   */
  public String getString(TrieNode root) {
    if (this == root) return "";
    CharSequence parentStr = null == getParent() ? "" : getParent().getString(root);
    return parentStr + getToken();
  }
  
  /**
   * Gets raw string.
   *
   * @return the raw string
   */
  public String getRawString() {
    return (0 == getDepth() ? "" : (getParent().getRawString() + new String(new char[]{getChar()})));
  }
  
  /**
   * Gets string.
   *
   * @return the string
   */
  public String getString() {
    return (null == getParent() ? "" : getParent().getString()) + (0 == getDepth() ? "" : getToken());
  }
  
  /**
   * Gets debug string.
   *
   * @return the debug string
   */
  public String getDebugString() {
    return getDebugString(getTrie().root());
  }
  
  /**
   * Gets debug string.
   *
   * @param root the root
   * @return the debug string
   */
  public String getDebugString(TrieNode root) {
    if (this == root) return "";
    CharSequence parentStr = null == getParent() ? "" : getParent().getDebugString(root);
    return parentStr + getDebugToken();
  }
  
  /**
   * Gets debug token.
   *
   * @return the debug token
   */
  public CharSequence getDebugToken() {
    char asChar = getChar();
    if (asChar == NodewalkerCodec.FALLBACK) return "<STOP>";
    if (asChar == NodewalkerCodec.END_OF_STRING) return "<NULL>";
    if (asChar == NodewalkerCodec.ESCAPE) return "<ESC>";
    if (asChar == '\\') return "\\\\";
    if (asChar == '\n') return "\\n";
    return new String(new char[]{asChar});
  }
  
  /**
   * Gets token.
   *
   * @return the token
   */
  public String getToken() {
    char asChar = getChar();
    if (asChar == NodewalkerCodec.FALLBACK) return "";
    if (asChar == NodewalkerCodec.END_OF_STRING) return "";
    if (asChar == NodewalkerCodec.ESCAPE) return "";
    return new String(new char[]{asChar});
  }
  
  /**
   * Gets char.
   *
   * @return the char
   */
  public char getChar() {
    return getData().token;
  }
  
  /**
   * Gets number of children.
   *
   * @return the number of children
   */
  public short getNumberOfChildren() {
    return getData().numberOfChildren;
  }
  
  /**
   * Gets depth.
   *
   * @return the depth
   */
  public short getDepth() {
    if (0 == index) return 0;
    if (-1 == depth) {
      synchronized (this) {
        if (-1 == depth) {
          TrieNode parent = getParent();
          assert (null == parent || parent.index < index);
          depth = (short) (null == parent ? 0 : (parent.getDepth() + 1));
        }
      }
    }
    return depth;
  }
  
  /**
   * Gets cursor index.
   *
   * @return the cursor index
   */
  public long getCursorIndex() {
    return getData().firstCursorIndex;
  }
  
  /**
   * Gets cursor count.
   *
   * @return the cursor count
   */
  public long getCursorCount() {
    return getData().cursorCount;
  }
  
  /**
   * Visit first trie node.
   *
   * @param visitor the visitor
   * @return the trie node
   */
  public TrieNode visitFirst(Consumer<? super TrieNode> visitor) {
    visitor.accept(this);
    TrieNode refresh = refresh();
    refresh.getChildren().forEach(n -> n.visitFirst(visitor));
    return refresh;
  }
  
  /**
   * Visit last trie node.
   *
   * @param visitor the visitor
   * @return the trie node
   */
  public TrieNode visitLast(Consumer<? super TrieNode> visitor) {
    getChildren().forEach(n -> n.visitLast(visitor));
    visitor.accept(this);
    return refresh();
  }
  
  /**
   * Gets children.
   *
   * @return the children
   */
  public Stream<? extends TrieNode> getChildren() {
    if (getData().firstChildIndex >= 0) {
      return IntStream.range(0, getData().numberOfChildren)
        .mapToObj(i -> new TrieNode(this.trie, getData().firstChildIndex + i, TrieNode.this));
    }
    else {
      return Stream.empty();
    }
  }
  
  /**
   * Gets child.
   *
   * @param token the token
   * @return the child
   */
  public Optional<? extends TrieNode> getChild(char token) {
    NodeData data = getData();
    int min = data.firstChildIndex;
    int max = data.firstChildIndex + data.numberOfChildren - 1;
    while (min <= max) {
      int i = (min + max) / 2;
      TrieNode node = new TrieNode(this.trie, i, TrieNode.this);
      char c = node.getChar();
      int compare = Character.compare(c, token);
      if (c < token) {
        // node.getChar() < token
        min = i + 1;
      }
      else if (c > token) {
        // node.getChar() > token
        max = i - 1;
      }
      else {
        return Optional.of(node);
      }
    }
    //assert !getChildren().keywords(x -> x.getChar() == token).findFirst().isPresent();
    return Optional.empty();
  }
  
  /**
   * Decrement cursor count.
   *
   * @param count the count
   */
  protected void decrementCursorCount(long count) {
    this.trie.nodes.update(index, data -> data.setCursorCount(Math.max(data.cursorCount - count, 0)));
    if (null != getParent()) {
      getParent().decrementCursorCount(count);
    }
  }
  
  /**
   * Traverse trie node.
   *
   * @param str the str
   * @return the trie node
   */
  public TrieNode traverse(String str) {
    if (str.isEmpty()) {
      return this;
    }
    return getChild(str.charAt(0)).map(n -> n.traverse(str.substring(1))).orElse(this);
  }
  
  /**
   * Contains cursor boolean.
   *
   * @param cursorId the cursor id
   * @return the boolean
   */
  public boolean containsCursor(long cursorId) {
    if (cursorId < getData().firstCursorIndex) {
      return false;
    }
    return cursorId < (getData().firstCursorIndex + getData().cursorCount);
  }
  
  /**
   * Traverse trie node.
   *
   * @param cursorId the cursor id
   * @return the trie node
   */
  public TrieNode traverse(long cursorId) {
    if (!containsCursor(cursorId)) {
      throw new IllegalArgumentException();
    }
    return getChildren().filter(n -> n.containsCursor(cursorId)).findFirst().map(n -> n.traverse(cursorId))
      .orElse(this);
  }
  
  /**
   * Remove cursor count.
   */
  public void removeCursorCount() {
    decrementCursorCount(getCursorCount());
  }
  
  /**
   * Bits to bits.
   *
   * @param toNode the to node
   * @return the bits
   */
  public Bits bitsTo(TrieNode toNode) {
    if (index == toNode.index) return Bits.NULL;
    return intervalTo(toNode).toBits();
  }
  
  /**
   * Interval to interval.
   *
   * @param toNode the to node
   * @return the interval
   */
  public Interval intervalTo(TrieNode toNode) {
    return new Interval(toNode.getCursorIndex() - this.getCursorIndex(),
      toNode.getCursorCount(), this.getCursorCount());
  }
  
  /**
   * Has children boolean.
   *
   * @return the boolean
   */
  public boolean hasChildren() {
    return 0 < getNumberOfChildren();
  }
  
  /**
   * Update node data.
   *
   * @param update the update
   * @return the node data
   */
  NodeData update(Function<NodeData, NodeData> update) {
    data = trie.nodes.update(index, update);
    return data;
  }
  
  /**
   * Gets trie.
   *
   * @return the trie
   */
  public CharTrie getTrie() {
    return trie;
  }
  
  /**
   * Is string terminal boolean.
   *
   * @return the boolean
   */
  public boolean isStringTerminal() {
    if (getChar() == NodewalkerCodec.END_OF_STRING) return true;
    if (getChar() == NodewalkerCodec.FALLBACK && null != getParent()) return getParent().isStringTerminal();
    return false;
  }
  
  /**
   * Stream decendents stream.
   *
   * @param level the level
   * @return the stream
   */
  public Stream<? extends TrieNode> streamDecendents(int level) {
    assert (level > 0);
    if (level == 1) {
      return getChildren();
    }
    else {
      return getChildren().flatMap(child -> child.streamDecendents(level - 1));
    }
  }
  
  /**
   * Write children.
   *
   * @param counts the counts
   */
  void writeChildren(TreeMap<Character, Long> counts) {
    int firstIndex = trie.nodes.length();
    counts.forEach((k, v) -> {
      if (v > 0) trie.nodes.add(new NodeData(k, (short) -1, -1, v, -1));
    });
    short length = (short) (trie.nodes.length() - firstIndex);
    trie.ensureParentIndexCapacity(firstIndex, length, index);
    update(n -> n.setFirstChildIndex(firstIndex).setNumberOfChildren(length));
    data = null;
  }
  
  /**
   * Gets children map.
   *
   * @return the children map
   */
  public TreeMap<Character, ? extends TrieNode> getChildrenMap() {
    TreeMap<Character, TrieNode> map = new TreeMap<>();
    getChildren().forEach(x -> map.put(x.getChar(), x));
    return map;
  }
  
  /**
   * Gets god children.
   *
   * @return the god children
   */
  public Map<Character, TrieNode> getGodChildren() {
    CharSequence postContext = this.getString().substring(1);
    return trie.tokens().stream().collect(Collectors.toMap(x -> x, token -> {
      TrieNode traverse = trie.traverse(token + postContext);
      return traverse.getString().equals(token + postContext) ? traverse : null;
    })).entrySet().stream().filter(e -> null != e.getValue()).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    
    TrieNode trieNode = (TrieNode) o;
    if (getCursorCount() != ((TrieNode) o).getCursorCount()) return false;
    return getChildrenMap().equals(trieNode.getChildrenMap());
  }
  
  @Override
  public int hashCode() {
    return getChildrenMap().hashCode() ^ Long.hashCode(getCursorCount());
  }
  
  /**
   * Gets parent.
   *
   * @return the parent
   */
  public TrieNode getParent() {
    if (0 == index) return null;
    if (null == parent && -1 == depth) {
      synchronized (this) {
        if (null == parent) {
          parent = newNode(trie.parentIndex[index]);
          assert (parent.index < index);
        }
      }
    }
    return parent;
  }
  
  /**
   * Gets continuation.
   *
   * @param c the c
   * @return the continuation
   */
  public TrieNode getContinuation(char c) {
    return ((Optional<TrieNode>) getChild(c)).orElseGet(() -> {
      TrieNode godparent = godparent();
      if (null == godparent) return null;
      return godparent.getContinuation(c);
    });
  }
}
