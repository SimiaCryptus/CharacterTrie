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

import com.simiacryptus.util.Util;
import com.simiacryptus.util.binary.BitInputStream;
import com.simiacryptus.util.binary.BitOutputStream;
import com.simiacryptus.util.binary.Bits;
import com.simiacryptus.util.binary.Interval;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

public class PPMCodec {
  public static final Character ESCAPE = '\uFFFE';
  public static final char FALLBACK = Character.MAX_VALUE;
  public static final char END_OF_STRING = Character.MIN_VALUE;
  final CharTrie inner;
  public boolean verbose = false;

  PPMCodec(CharTrie inner) {
    super();
    this.inner = inner;
  }

  public int getMemorySize() {
    return inner.getMemorySize();
  }

  @Nonnull
  private static String getRight(@Nonnull String str, int count) {
    int newLen = Math.min(count, str.length());
    int prefixFrom = Math.max(0, str.length() - newLen);
    return str.substring(prefixFrom, str.length());
  }

  public CharSequence decodePPM(@Nonnull byte[] data, int context) {
    try {
      BitInputStream in = new BitInputStream(new ByteArrayInputStream(data));
      StringBuilder out = new StringBuilder();
      String contextStr = "";
      while (true) {
        TrieNode fromNode = inner.matchPredictor(getRight(contextStr, context));
        assert fromNode != null;
        if (0 == fromNode.getNumberOfChildren())
          return "";
        long seek = in.peekLongCoord(fromNode.getCursorCount());
        TrieNode toNode = fromNode.traverse(seek + fromNode.getCursorIndex());
        String newSegment = toNode.getString(fromNode);
        Interval interval = fromNode.intervalTo(toNode);
        Bits bits = interval.toBits();
        if (verbose) {
          System.out.println(
              String.format("Using prefix \"%s\", seek to %s pos, path \"%s\" apply %s -> %s, input buffer = %s",
                  fromNode.getDebugString(), seek, toNode.getDebugString(fromNode), interval, bits, in.peek(24)));
        }
        in.expect(bits);
        if (toNode.isStringTerminal()) {
          if (verbose)
            System.out.println("Inserting null char to terminate string");
          newSegment += END_OF_STRING;
        }
        if (!newSegment.isEmpty()) {
          if (newSegment.endsWith("\u0000")) {
            out.append(newSegment, 0, newSegment.length() - 1);
            if (verbose)
              System.out.println(String.format("Null char reached"));
            break;
          } else {
            contextStr += newSegment;
            out.append(newSegment);
          }
        } else if (in.availible() == 0) {
          if (verbose)
            System.out.println(String.format("No More Data"));
          break;
        } else if (toNode.getChar() == END_OF_STRING) {
          if (verbose)
            System.out.println(String.format("End code"));
          break;
          //throw new RuntimeException("Cannot decode text");
        } else if (toNode.getChar() == FALLBACK) {
          contextStr = fromNode.getString().substring(1);
        } else if (toNode.getChar() == ESCAPE) {
          Bits charBits = in.read(16);
          char exotic = (char) charBits.toLong();
          out.append(new String(new char[]{exotic}));
          if (verbose) {
            System.out.println(
                String.format("Read exotic byte %s -> %s, input buffer = %s", exotic, charBits, in.peek(24)));
          }
        } else {
          if (verbose)
            System.out.println(String.format("Cannot decode text"));
          break;
          //throw new RuntimeException("Cannot decode text");
        }
      }
      return out.toString();
    } catch (IOException e) {
      throw Util.throwException(e);
    }
  }

  @Nonnull
  public Bits encodePPM(@Nonnull String text, int context) {
    final CharSequence original = text;
    //if(verbose) com.simiacryptus.ref.wrappers.System.p.println(String.format("Encoding %s apply %s chars of context", text, context));
    if (!text.endsWith("\u0000"))
      text += END_OF_STRING;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    BitOutputStream out = new BitOutputStream(buffer);
    String contextStr = "";
    try {
      while (!text.isEmpty()) {
        String right = getRight(contextStr, context);
        TrieNode fromNode = inner.matchPredictor(right); // inner.matchEnd(right).getString()
        assert fromNode != null;
        String prefix = fromNode.getString();
        // fromNode.getChildrenMap()
        TrieNode toNode = fromNode.traverse(text);
        int segmentChars = toNode.getDepth() - fromNode.getDepth();
        if (toNode.hasChildren()) {
          if (prefix.isEmpty() && 0 == segmentChars) {
            Optional<? extends TrieNode> child = toNode.getChild(ESCAPE);
            assert child.isPresent();
            toNode = child.get();
          } else {
            toNode = toNode.getChild(FALLBACK).get();
          }
        }

        Interval interval = fromNode.intervalTo(toNode);
        Bits segmentData = interval.toBits();
        if (verbose) {
          System.out
              .println(String.format("Using context \"%s\", encoded \"%s\" (%s chars) as %s -> %s",
                  fromNode.getDebugString(), toNode.getDebugString(fromNode), segmentChars, interval, segmentData));
        }
        out.write(segmentData);

        if (0 == segmentChars) {
          if (prefix.isEmpty()) {
            //throw new RuntimeException(String.format("Cannot encode %s in model", text.substring(0,1)));
            char exotic = text.charAt(0);
            out.write(exotic);
            if (verbose) {
              System.out
                  .println(String.format("Writing exotic character %s -> %s", exotic, new Bits(exotic, 16)));
            }
            text = text.substring(1);
          } else if (toNode.getChar() == FALLBACK) {
            contextStr = prefix.substring(1);
          } else {
            throw new RuntimeException("Cannot encode " + text.substring(0, 1));
          }
        } else {
          contextStr += text.substring(0, segmentChars);
          text = text.substring(segmentChars);
        }
      }
      out.flush();
      //if(verbose) com.simiacryptus.ref.wrappers.System.p.println(String.format("Encoded %s to %s", original, bits));
      return new Bits(buffer.toByteArray(), out.getTotalBitsWritten());
    } catch (IOException e) {
      throw Util.throwException(e);
    }
  }

  @Nonnull
  public PPMCodec copy() {
    return new PPMCodec(inner.copy());
  }
}
