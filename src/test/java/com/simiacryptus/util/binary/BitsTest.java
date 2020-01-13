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

import com.simiacryptus.ref.lang.RefAware;
import com.simiacryptus.ref.wrappers.RefAssert;
import com.simiacryptus.ref.wrappers.RefString;
import com.simiacryptus.util.test.TestCategories;
import org.json.JSONException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class BitsTest {
  Random random = new Random();

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testConcatenate() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testConcatenate(this.randomLong(), this.randomLong());
    }
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testDivide() throws JSONException {
    RefAssert.assertEquals("1", Bits.divide(2, 2, 10).toBitString());
    RefAssert.assertEquals("0", Bits.divide(0, 2, 10).toBitString());
    RefAssert.assertEquals("01", Bits.divide(1, 2, 10).toBitString());
    RefAssert.assertEquals("0011001100", Bits.divide(2, 5, 10).toBitString());
    RefAssert.assertEquals("01", Bits.divide(2, 4, 10).toBitString());
    RefAssert.assertEquals("0001", Bits.divide(171, 1368, 15).toBitString());
    RefAssert.assertEquals("000010001000001", Bits.divide(91, 1368, 15).toBitString());
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testBitStream() throws JSONException {
    Bits totalBits = BitOutputStream.toBits(out -> {
      try {
        out.write(Bits.divide(1, 2, 10));
        out.write(Bits.divide(1, 2, 10));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    RefAssert.assertEquals("0101", totalBits.toBitString());
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testInterval() throws JSONException {
    RefAssert.assertEquals("1", new Interval(1, 2, 3).toBits().toBitString());

    RefAssert.assertEquals("01", new Interval(0, 1, 2).toBits().toBitString());
    RefAssert.assertEquals("11", new Interval(1, 1, 2).toBits().toBitString());

    RefAssert.assertEquals("001", new Interval(0, 1, 3).toBits().toBitString());
    RefAssert.assertEquals("011", new Interval(1, 1, 3).toBits().toBitString());
    RefAssert.assertEquals("11", new Interval(2, 1, 3).toBits().toBitString());

    RefAssert.assertEquals("0001", new Interval(0, 1, 5).toBits().toBitString());
    RefAssert.assertEquals("010", new Interval(1, 1, 5).toBits().toBitString());
    RefAssert.assertEquals("0111", new Interval(2, 1, 5).toBits().toBitString());
    RefAssert.assertEquals("101", new Interval(3, 1, 5).toBits().toBitString());
    RefAssert.assertEquals("111", new Interval(4, 1, 5).toBits().toBitString());

    RefAssert.assertEquals("001", new Interval(0, 2, 5).toBits().toBitString());
    RefAssert.assertEquals("00011", new Interval(91, 80, 1368).toBits().toBitString());
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testFixedLength() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testFixedLength(this.randomLong());
    }
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testVarLongs() throws JSONException, IOException {
    for (int i = 0; i < 1000; i++) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (BitOutputStream out = new BitOutputStream(buffer)) {
        out.writeVarLong(i);
      }
      BitInputStream in = new BitInputStream(new ByteArrayInputStream(buffer.toByteArray()));
      RefAssert.assertEquals(i, in.readVarLong());
    }
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testHardcoded() throws JSONException {
    RefAssert.assertEquals(new Bits(0), new Bits(0));
    RefAssert.assertEquals("", new Bits(0).toBitString());
    RefAssert.assertEquals("1", new Bits(1).toBitString());
    RefAssert.assertEquals("100", new Bits(4).toBitString());
    RefAssert.assertEquals("10001", new Bits(17).toBitString());
    RefAssert.assertEquals("100", new Bits(17).range(0, 3).toBitString());
    RefAssert.assertEquals("01", new Bits(17).range(3).toBitString());
    RefAssert.assertEquals("111", new Bits(7).toBitString());
    RefAssert.assertEquals("10111", new Bits(2).concatenate(new Bits(7)).toBitString());
    RefAssert.assertEquals("00110", new Bits(6l, 5).toBitString());
    RefAssert.assertEquals("111000000", new Bits(7l).leftShift(6).toBitString());
    RefAssert.assertEquals("1110", new Bits(7l).leftShift(6).range(0, 4).toBitString());
    RefAssert.assertEquals("00000", new Bits(7l).leftShift(6).range(4).toBitString());
    RefAssert.assertEquals("110", new Bits(6l).toBitString());
    RefAssert.assertEquals("11100", new Bits(7l).leftShift(2).toBitString());
    RefAssert.assertEquals("11000", new Bits(7l).leftShift(2).bitwiseAnd(new Bits(6l)).toBitString());
    RefAssert.assertEquals("11100", new Bits(7l).leftShift(2).bitwiseOr(new Bits(6l)).toBitString());
    RefAssert.assertEquals("00100", new Bits(7l).leftShift(2).bitwiseXor(new Bits(6l)).toBitString());
    RefAssert.assertEquals(2, new Bits(7l, 16).getBytes().length);
  }

  @Test
  @Category(TestCategories.UnitTest.class)
  public void testSubrange() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      final long value = this.random.nextLong();
      final Bits bits = new Bits(value);
      this.testSubrange(bits);
    }
  }

  @Test
  @Category(TestCategories.UnitTest.class)
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
    RefAssert.assertEquals(RefString.format("Concatenate %s and %s", a, b), asString, bits.toBitString());
  }

  private void testFixedLength(final long value) {
    String asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value, 64);
    while (asString.length() < 64) {
      asString = "0" + asString;
    }
    RefAssert.assertEquals("toLong for " + value, value, bits.toLong());
    RefAssert.assertEquals("toString for " + value, asString, bits.toBitString());
  }

  private void testSubrange(final Bits bits) {
    final String asString = bits.toBitString();
    for (int j = 0; j < 10; j++) {
      final int from = this.random.nextInt(asString.length());
      final int to = from + this.random.nextInt(asString.length() - from);
      this.testSubrange(bits, asString, from, to);
    }
  }

  private void testSubrange(final Bits bits, final String asString, final int from, final int to) {
    final CharSequence subStr = asString.substring(from, to);
    final Bits subBits = bits.range(from, to - from);
    RefAssert.assertEquals(RefString.format("Substring (%s,%s) of %s", from, to, bits), subStr, subBits.toBitString());
  }

  private void testToString(final long value) {
    final CharSequence asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value);
    RefAssert.assertEquals("toLong for " + value, value, bits.toLong());
    RefAssert.assertEquals("toString for " + value, asString, bits.toBitString());
  }

}