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

import com.simiacryptus.util.binary.BitInputStream;
import com.simiacryptus.util.binary.BitOutputStream;
import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.binary.Interval;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;

/**
 * The type Nodewalker codec.
 */
public class NodewalkerCodec {
  /**
   * The constant ESCAPE.
   */
  public static final Character ESCAPE = '\uFFFE';
  /**
   * The constant FALLBACK.
   */
  public static final char FALLBACK = Character.MAX_VALUE;
  /**
   * The constant END_OF_STRING.
   */
  public static final char END_OF_STRING = Character.MIN_VALUE;
  
  /**
   * The Inner.
   */
  protected final CharTrie inner;
  /**
   * The Verbose.
   */
  protected PrintStream verbose = null;
  
  /**
   * Instantiates a new Nodewalker codec.
   *
   * @param inner the inner
   */
  NodewalkerCodec(CharTrie inner) {
    super();
    this.inner = inner;
  }
  
  /**
   * Decode ppm string.
   *
   * @param data    the data
   * @param context the context
   * @return the string
   */
  public CharSequence decodePPM(byte[] data, int context) {
    return new Decoder(data, context).encodePPM();
  }
  
  /**
   * Encode ppm bits.
   *
   * @param text    the text
   * @param context the context
   * @return the bits
   */
  public Bits encodePPM(String text, int context) {
    return new Encoder(text, context).encodePPM();
  }
  
  /**
   * Sets verbose.
   *
   * @param verbose the verbose
   * @return the verbose
   */
  public NodewalkerCodec setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }
  
  /**
   * Write forward.
   *
   * @param encoder the encoder
   * @throws IOException the io exception
   */
  protected void writeForward(Encoder encoder) throws IOException {
    if (encoder.node.index != encoder.fromNode.index) {
      Bits bits = encoder.fromNode.bitsTo(encoder.node);
      short count = (short) (encoder.node.getDepth() - encoder.fromNode.getDepth());
      if (verbose != null) {
        verbose.println(String.format("Writing %s forward from %s to %s = %s", count, encoder.fromNode.getDebugString(), encoder.node.getDebugString(), bits));
      }
      encoder.out.writeVarShort(count, 3);
      encoder.out.write(bits);
    }
    else {
      assert (0 == encoder.node.index);
      encoder.out.writeVarShort((short) 0, 3);
    }
  }
  
  /**
   * Read forward.
   *
   * @param decoder the decoder
   * @throws IOException the io exception
   */
  protected void readForward(Decoder decoder) throws IOException {
    short numberOfTokens = decoder.in.readVarShort(3);
    if (0 < numberOfTokens) {
      long seek = decoder.in.peekLongCoord(decoder.node.getCursorCount());
      TrieNode toNode = decoder.node.traverse(seek + decoder.node.getCursorIndex());
      while (toNode.getDepth() > decoder.node.getDepth() + numberOfTokens) toNode = toNode.getParent();
      Interval interval = decoder.node.intervalTo(toNode);
      String str = toNode.getString(decoder.node);
      Bits bits = interval.toBits();
      if (verbose != null) {
        verbose.println(String.format("Read %s forward from %s to %s = %s", numberOfTokens, decoder.node.getDebugString(), toNode.getDebugString(), bits));
      }
      decoder.in.expect(bits);
      decoder.out.append(str);
      decoder.node = toNode;
    }
    else {
      assert (0 == decoder.node.index);
    }
  }
  
  /**
   * Write backup optional.
   *
   * @param encoder the encoder
   * @param token   the token
   * @return the optional
   * @throws IOException the io exception
   */
  protected Optional<TrieNode> writeBackup(Encoder encoder, char token) throws IOException {
    Optional<TrieNode> child = Optional.empty();
    while (!child.isPresent()) {
      encoder.node = encoder.node.godparent();
      if (encoder.node == null) break;
      child = (Optional<TrieNode>) encoder.node.getChild(token);
    }
    assert (null == encoder.node || child.isPresent());
    if (null != encoder.node) {
      for (int i = 0; i < 2; i++) {
        if (0 != encoder.node.index) encoder.node = encoder.node.godparent();
      }
      child = (Optional<TrieNode>) encoder.node.getChild(token);
      while (!child.isPresent()) {
        encoder.node = encoder.node.godparent();
        if (encoder.node == null) break;
        child = (Optional<TrieNode>) encoder.node.getChild(token);
      }
      assert (null == encoder.node || child.isPresent());
    }
    short backupSteps = (short) (encoder.fromNode.getDepth() - (null == encoder.node ? -1 : encoder.node.getDepth()));
    assert (backupSteps >= 0);
    if (verbose != null) {
      verbose.println(String.format("Backing up %s from from %s to %s", backupSteps, encoder.fromNode.getDebugString(), null == encoder.node ? null : encoder.node.getDebugString()));
    }
    encoder.out.writeVarShort(backupSteps, 3);
    return child;
  }
  
  /**
   * Read backup boolean.
   *
   * @param decoder the decoder
   * @return the boolean
   * @throws IOException the io exception
   */
  protected boolean readBackup(Decoder decoder) throws IOException {
    short numberOfBackupSteps = decoder.in.readVarShort(3);
    TrieNode fromNode = decoder.node;
    if (0 == numberOfBackupSteps) return true;
    for (int i = 0; i < numberOfBackupSteps; i++) {
      decoder.node = decoder.node.godparent();
    }
    if (verbose != null) {
      verbose.println(String.format("Backing up %s from from %s to %s", numberOfBackupSteps, fromNode.getDebugString(), decoder.node.getDebugString()));
    }
    return false;
  }
  
  /**
   * Write terminal.
   *
   * @param encoder the encoder
   * @throws IOException the io exception
   */
  protected void writeTerminal(Encoder encoder) throws IOException {
    if (verbose != null) {
      verbose.println(String.format("Writing forward to end from %s to %s", encoder.fromNode.getDebugString(), encoder.node.getDebugString()));
    }
    encoder.out.writeVarShort((short) (encoder.node.getDepth() - encoder.fromNode.getDepth()), 3);
    encoder.out.write(encoder.fromNode.bitsTo(encoder.node));
    encoder.out.writeVarShort((short) 0, 3);
  }
  
  /**
   * The type Decoder.
   */
  protected class Decoder {
    /**
     * The Data.
     */
    protected byte[] data;
    /**
     * The Context.
     */
    protected int context;
    /**
     * The In.
     */
    protected BitInputStream in = new BitInputStream(new ByteArrayInputStream(data));
    /**
     * The Out.
     */
    protected StringBuilder out = new StringBuilder();
    /**
     * The Node.
     */
    protected TrieNode node = inner.root();
    
    /**
     * Instantiates a new Decoder.
     *
     * @param data    the data
     * @param context the context
     */
    protected Decoder(byte[] data, int context) {
      this.data = data;
      this.context = context;
    }
    
    /**
     * Encode ppm string.
     *
     * @return the string
     */
    protected CharSequence encodePPM() {
      try {
        while (true) {
          if (null == node) {
            char c = in.readChar();
            out.append(c);
            if (verbose != null) verbose.println(String.format("Literal token %s", c));
            node = inner.root();
          }
          readForward(this);
          if (readBackup(this)) break;
        }
        return out.toString();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  /**
   * The type Encoder.
   */
  protected class Encoder {
    /**
     * The Text.
     */
    protected String text;
    /**
     * The Context.
     */
    protected int context;
    /**
     * The Buffer.
     */
    protected ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    /**
     * The Out.
     */
    protected BitOutputStream out = new BitOutputStream(buffer);
    /**
     * The Node.
     */
    protected TrieNode node = inner.root();
    /**
     * The From node.
     */
    protected TrieNode fromNode = inner.root();
    
    /**
     * Instantiates a new Encoder.
     *
     * @param text    the text
     * @param context the context
     */
    protected Encoder(String text, int context) {
      if (!text.endsWith("\u0000")) text += END_OF_STRING;
      this.text = text;
      this.context = context;
    }
    
    /**
     * Encode ppm bits.
     *
     * @return the bits
     */
    protected Bits encodePPM() {
      try {
        for (char token : text.toCharArray()) {
          Optional<TrieNode> child = (Optional<TrieNode>) node.getChild(token);
          if (!child.isPresent()) {
            writeForward(this);
            fromNode = node;
            child = writeBackup(this, token);
            if (null == node) {
              if (verbose != null) verbose.println(String.format("Literal token %s", token));
              out.write(token);
              fromNode = inner.root();
              node = fromNode;
            }
            else {
              fromNode = node;
              node = child.get();
            }
          }
          else {
            node = child.get();
          }
        }
        writeTerminal(this);
        out.flush();
        Bits bits = new Bits(buffer.toByteArray(), out.getTotalBitsWritten());
        return bits;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
  }
  
}
