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

import com.simiacryptus.ref.lang.RefUtil;
import com.simiacryptus.ref.wrappers.RefString;
import com.simiacryptus.ref.wrappers.RefStringBuilder;
import com.simiacryptus.util.binary.BitInputStream;
import com.simiacryptus.util.binary.BitOutputStream;
import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.binary.Interval;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;

public class NodewalkerCodec {
  public static final Character ESCAPE = '\uFFFE';
  public static final char FALLBACK = Character.MAX_VALUE;
  public static final char END_OF_STRING = Character.MIN_VALUE;

  protected final CharTrie inner;
  @Nullable
  protected PrintStream verbose = null;

  NodewalkerCodec(CharTrie inner) {
    super();
    this.inner = inner;
  }

  @Nonnull
  public NodewalkerCodec setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }

  public CharSequence decodePPM(byte[] data, int context) {
    return new Decoder(data, context).encodePPM();
  }

  @Nonnull
  public Bits encodePPM(@Nonnull String text, int context) {
    return new Encoder(text, context).encodePPM();
  }

  protected void writeForward(@Nonnull Encoder encoder) throws IOException {
    assert encoder.fromNode != null;
    assert encoder.node != null;
    if (encoder.node.index != encoder.fromNode.index) {
      Bits bits = encoder.fromNode.bitsTo(encoder.node);
      short count = (short) (encoder.node.getDepth() - encoder.fromNode.getDepth());
      if (verbose != null) {
        verbose.println(RefString.format("Writing %s forward from %s to %s = %s", count,
            encoder.fromNode.getDebugString(), encoder.node.getDebugString(), bits));
      }
      encoder.out.writeVarShort(count, 3);
      encoder.out.write(bits);
    } else {
      assert 0 == encoder.node.index;
      encoder.out.writeVarShort((short) 0, 3);
    }
  }

  protected void readForward(@Nonnull Decoder decoder) throws IOException {
    short numberOfTokens = decoder.in.readVarShort(3);
    if (0 < numberOfTokens) {
      assert decoder.node != null;
      long seek = decoder.in.peekLongCoord(decoder.node.getCursorCount());
      TrieNode toNode = decoder.node.traverse(seek + decoder.node.getCursorIndex());
      assert toNode != null;
      assert toNode != null;
      while (toNode.getDepth() > decoder.node.getDepth() + numberOfTokens)
        toNode = toNode.getParent();
      Interval interval = decoder.node.intervalTo(toNode);
      String str = toNode.getString(decoder.node);
      Bits bits = interval.toBits();
      if (verbose != null) {
        verbose.println(RefString.format("Read %s forward from %s to %s = %s", numberOfTokens,
            decoder.node.getDebugString(), toNode.getDebugString(), bits));
      }
      decoder.in.expect(bits);
      decoder.out.append(str);
      decoder.node = toNode;
    } else {
      assert decoder.node != null;
      assert 0 == decoder.node.index;
    }
  }

  protected Optional<TrieNode> writeBackup(@Nonnull Encoder encoder, char token) throws IOException {
    Optional<TrieNode> child = Optional.empty();
    while (!child.isPresent()) {
      assert encoder.node != null;
      encoder.node = encoder.node.godparent();
      if (encoder.node == null)
        break;
      child = (Optional<TrieNode>) encoder.node.getChild(token);
    }
    if (null != encoder.node) {
      for (int i = 0; i < 2; i++) {
        assert encoder.node != null;
        if (0 != encoder.node.index)
          encoder.node = encoder.node.godparent();
      }
      assert encoder.node != null;
      child = (Optional<TrieNode>) encoder.node.getChild(token);
      while (!child.isPresent()) {
        encoder.node = encoder.node.godparent();
        if (encoder.node == null)
          break;
        child = (Optional<TrieNode>) encoder.node.getChild(token);
      }
    }
    assert encoder.fromNode != null;
    short backupSteps = (short) (encoder.fromNode.getDepth() - (null == encoder.node ? -1 : encoder.node.getDepth()));
    assert backupSteps >= 0;
    if (verbose != null) {
      verbose.println(RefString.format("Backing up %s from from %s to %s", backupSteps,
          encoder.fromNode.getDebugString(), null == encoder.node ? null : encoder.node.getDebugString()));
    }
    encoder.out.writeVarShort(backupSteps, 3);
    return child;
  }

  protected boolean readBackup(@Nonnull Decoder decoder) throws IOException {
    short numberOfBackupSteps = decoder.in.readVarShort(3);
    TrieNode fromNode = decoder.node;
    if (0 == numberOfBackupSteps)
      return true;
    for (int i = 0; i < numberOfBackupSteps; i++) {
      assert decoder.node != null;
      decoder.node = decoder.node.godparent();
    }
    if (verbose != null) {
      assert fromNode != null;
      verbose.println(RefString.format("Backing up %s from from %s to %s", numberOfBackupSteps,
          fromNode.getDebugString(), null == decoder.node ? null : decoder.node.getDebugString()));
    }
    return false;
  }

  protected void writeTerminal(@Nonnull Encoder encoder) throws IOException {
    if (verbose != null) {
      assert encoder.node != null;
      assert encoder.fromNode != null;
      verbose.println(RefString.format("Writing forward to end from %s to %s", encoder.fromNode.getDebugString(),
          encoder.node.getDebugString()));
    }
    assert encoder.fromNode != null;
    assert encoder.node != null;
    encoder.out.writeVarShort((short) (encoder.node.getDepth() - encoder.fromNode.getDepth()), 3);
    encoder.out.write(encoder.fromNode.bitsTo(encoder.node));
    encoder.out.writeVarShort((short) 0, 3);
  }

  protected class Decoder {
    protected byte[] data;
    protected int context;
    protected BitInputStream in;
    @Nonnull
    protected RefStringBuilder out = new RefStringBuilder();
    @Nullable
    protected TrieNode node = inner.root();

    protected Decoder(byte[] data, int context) {
      this.data = data;
      this.context = context;
      in = new BitInputStream(new ByteArrayInputStream(this.data));
    }

    protected CharSequence encodePPM() {
      try {
        while (true) {
          if (null == node) {
            char c = in.readChar();
            out.append(c);
            if (verbose != null)
              verbose.println(RefString.format("Literal token %s", c));
            node = inner.root();
          }
          readForward(this);
          if (readBackup(this))
            break;
        }
        return out.toString();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected class Encoder {
    protected String text;
    protected int context;
    @Nonnull
    protected ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    @Nonnull
    protected BitOutputStream out = new BitOutputStream(buffer);
    @Nullable
    protected TrieNode node = inner.root();
    @Nullable
    protected TrieNode fromNode = inner.root();

    protected Encoder(@Nonnull String text, int context) {
      if (!text.endsWith("\u0000"))
        text += END_OF_STRING;
      this.text = text;
      this.context = context;
    }

    @Nonnull
    protected Bits encodePPM() {
      try {
        for (char token : text.toCharArray()) {
          assert node != null;
          Optional<TrieNode> child = (Optional<TrieNode>) node.getChild(token);
          if (!child.isPresent()) {
            writeForward(this);
            fromNode = node;
            child = writeBackup(this, token);
            if (null == node) {
              if (verbose != null)
                verbose.println(RefString.format("Literal token %s", token));
              out.write(token);
              fromNode = inner.root();
              node = fromNode;
            } else {
              fromNode = node;
              node = RefUtil.get(child);
            }
          } else {
            node = RefUtil.get(child);
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
