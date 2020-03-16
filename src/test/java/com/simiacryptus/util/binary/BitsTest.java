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

package com.simiacryptus.util.binary;

import com.simiacryptus.util.Util;
import org.json.JSONException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BitsTest {
  @Nonnull
  Random random = new Random();

  @Test
  @Tag("UnitTest")
  public void testConcatenate() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testConcatenate(this.randomLong(), this.randomLong());
    }
  }

  @Test
  @Tag("UnitTest")
  public void testDivide() throws JSONException {
    assertEquals("1", Bits.divide(2, 2, 10).toBitString());
    assertEquals("0", Bits.divide(0, 2, 10).toBitString());
    assertEquals("01", Bits.divide(1, 2, 10).toBitString());
    assertEquals("0011001100", Bits.divide(2, 5, 10).toBitString());
    assertEquals("01", Bits.divide(2, 4, 10).toBitString());
    assertEquals("0001", Bits.divide(171, 1368, 15).toBitString());
    assertEquals("000010001000001", Bits.divide(91, 1368, 15).toBitString());
  }

  @Test
  @Tag("UnitTest")
  public void testBitStream() throws JSONException {
    Bits totalBits = BitOutputStream.toBits(out -> {
      try {
        out.write(Bits.divide(1, 2, 10));
        out.write(Bits.divide(1, 2, 10));
      } catch (IOException e) {
        throw Util.throwException(e);
      }
    });
    assertEquals("0101", totalBits.toBitString());
  }

  @Test
  @Tag("UnitTest")
  public void testInterval() throws JSONException {
    assertEquals("1", new Interval(1, 2, 3).toBits().toBitString());

    assertEquals("01", new Interval(0, 1, 2).toBits().toBitString());
    assertEquals("11", new Interval(1, 1, 2).toBits().toBitString());

    assertEquals("001", new Interval(0, 1, 3).toBits().toBitString());
    assertEquals("011", new Interval(1, 1, 3).toBits().toBitString());
    assertEquals("11", new Interval(2, 1, 3).toBits().toBitString());

    assertEquals("0001", new Interval(0, 1, 5).toBits().toBitString());
    assertEquals("010", new Interval(1, 1, 5).toBits().toBitString());
    assertEquals("0111", new Interval(2, 1, 5).toBits().toBitString());
    assertEquals("101", new Interval(3, 1, 5).toBits().toBitString());
    assertEquals("111", new Interval(4, 1, 5).toBits().toBitString());

    assertEquals("001", new Interval(0, 2, 5).toBits().toBitString());
    assertEquals("00011", new Interval(91, 80, 1368).toBits().toBitString());
  }

  @Test
  @Tag("UnitTest")
  public void testFixedLength() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testFixedLength(this.randomLong());
    }
  }

  @Test
  @Tag("UnitTest")
  public void testVarLongs() throws JSONException, IOException {
    for (int i = 0; i < 1000; i++) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (BitOutputStream out = new BitOutputStream(buffer)) {
        out.writeVarLong(i);
      }
      BitInputStream in = new BitInputStream(new ByteArrayInputStream(buffer.toByteArray()));
      assertEquals(i, in.readVarLong());
    }
  }

  @Test
  @Tag("UnitTest")
  public void testHardcoded() throws JSONException {
    assertEquals(new Bits(0), new Bits(0));
    assertEquals("", new Bits(0).toBitString());
    assertEquals("1", new Bits(1).toBitString());
    assertEquals("100", new Bits(4).toBitString());
    assertEquals("10001", new Bits(17).toBitString());
    assertEquals("100", new Bits(17).range(0, 3).toBitString());
    assertEquals("01", new Bits(17).range(3).toBitString());
    assertEquals("111", new Bits(7).toBitString());
    assertEquals("10111", new Bits(2).concatenate(new Bits(7)).toBitString());
    assertEquals("00110", new Bits(6l, 5).toBitString());
    assertEquals("111000000", new Bits(7l).leftShift(6).toBitString());
    assertEquals("1110", new Bits(7l).leftShift(6).range(0, 4).toBitString());
    assertEquals("00000", new Bits(7l).leftShift(6).range(4).toBitString());
    assertEquals("110", new Bits(6l).toBitString());
    assertEquals("11100", new Bits(7l).leftShift(2).toBitString());
    assertEquals("11000", new Bits(7l).leftShift(2).bitwiseAnd(new Bits(6l)).toBitString());
    assertEquals("11100", new Bits(7l).leftShift(2).bitwiseOr(new Bits(6l)).toBitString());
    assertEquals("00100", new Bits(7l).leftShift(2).bitwiseXor(new Bits(6l)).toBitString());
    assertEquals(2, new Bits(7l, 16).getBytes().length);
  }

  @Test
  @Tag("UnitTest")
  public void testSubrange() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      final long value = this.random.nextLong();
      final Bits bits = new Bits(value);
      this.testSubrange(bits);
    }
  }

  @Test
  @Tag("UnitTest")
  public void testToString() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testToString(this.randomLong());
    }
  }

  private long randomLong() {
    return this.random.nextLong() >> this.random.nextInt(62);
  }

  private void testConcatenate(final long a, final long b) {
    final CharSequence asStringA = 0 == a ? "" : Long.toBinaryString(a);
    final CharSequence asStringB = 0 == b ? "" : Long.toBinaryString(b);
    final CharSequence asString = asStringA.toString() + asStringB;
    final Bits bitsA = new Bits(a);
    final Bits bitsB = new Bits(b);
    final Bits bits = bitsA.concatenate(bitsB);
    assertEquals(String.format("Concatenate %s and %s", a, b), asString, bits.toBitString());
  }

  private void testFixedLength(final long value) {
    String asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value, 64);
    while (asString.length() < 64) {
      asString = "0" + asString;
    }
    assertEquals(value, bits.toLong(), "toLong for " + value);
    assertEquals("toString for " + value, asString, bits.toBitString());
  }

  private void testSubrange(@Nonnull final Bits bits) {
    final String asString = bits.toBitString();
    for (int j = 0; j < 10; j++) {
      final int from = this.random.nextInt(asString.length());
      final int to = from + this.random.nextInt(asString.length() - from);
      this.testSubrange(bits, asString, from, to);
    }
  }

  private void testSubrange(@Nonnull final Bits bits, @Nonnull final String asString, final int from, final int to) {
    final CharSequence subStr = asString.substring(from, to);
    final Bits subBits = bits.range(from, to - from);
    assertEquals(String.format("Substring (%s,%s) of %s", from, to, bits), subStr, subBits.toBitString());
  }

  private void testToString(final long value) {
    final CharSequence asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value);
    assertEquals(value, bits.toLong(), "toLong for " + value);
    assertEquals("toString for " + value, asString, bits.toBitString());
  }

}