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

package com.simiacryptus.util.binary;

import com.simiacryptus.util.test.TestCategories;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * The type Bits test.
 */
public class BitsTest {
  /**
   * The Random.
   */
  Random random = new Random();
  
  private long randomLong() {
    return this.random.nextLong() >> this.random.nextInt(62);
  }
  
  /**
   * Test concatenate.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testConcatenate() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testConcatenate(this.randomLong(), this.randomLong());
    }
  }
  
  /**
   * Test divide.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testDivide() throws JSONException {
    Assert.assertEquals("1", Bits.divide(2, 2, 10).toBitString());
    Assert.assertEquals("0", Bits.divide(0, 2, 10).toBitString());
    Assert.assertEquals("01", Bits.divide(1, 2, 10).toBitString());
    Assert.assertEquals("0011001100", Bits.divide(2, 5, 10).toBitString());
    Assert.assertEquals("01", Bits.divide(2, 4, 10).toBitString());
    Assert.assertEquals("0001", Bits.divide(171, 1368, 15).toBitString());
    Assert.assertEquals("000010001000001", Bits.divide(91, 1368, 15).toBitString());
  }
  
  /**
   * Test bit stream.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
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
    Assert.assertEquals("0101", totalBits.toBitString());
  }
  
  /**
   * Test interval.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testInterval() throws JSONException {
    Assert.assertEquals("1", new Interval(1, 2, 3).toBits().toBitString());
    
    Assert.assertEquals("01", new Interval(0, 1, 2).toBits().toBitString());
    Assert.assertEquals("11", new Interval(1, 1, 2).toBits().toBitString());
    
    Assert.assertEquals("001", new Interval(0, 1, 3).toBits().toBitString());
    Assert.assertEquals("011", new Interval(1, 1, 3).toBits().toBitString());
    Assert.assertEquals("11", new Interval(2, 1, 3).toBits().toBitString());
    
    Assert.assertEquals("0001", new Interval(0, 1, 5).toBits().toBitString());
    Assert.assertEquals("010", new Interval(1, 1, 5).toBits().toBitString());
    Assert.assertEquals("0111", new Interval(2, 1, 5).toBits().toBitString());
    Assert.assertEquals("101", new Interval(3, 1, 5).toBits().toBitString());
    Assert.assertEquals("111", new Interval(4, 1, 5).toBits().toBitString());
    
    Assert.assertEquals("001", new Interval(0, 2, 5).toBits().toBitString());
    Assert.assertEquals("00011", new Interval(91, 80, 1368).toBits().toBitString());
  }
  
  private void testConcatenate(final long a, final long b) {
    final CharSequence asStringA = 0 == a ? "" : Long.toBinaryString(a);
    final CharSequence asStringB = 0 == b ? "" : Long.toBinaryString(b);
    final CharSequence asString = asStringA + asStringB;
    final Bits bitsA = new Bits(a);
    final Bits bitsB = new Bits(b);
    final Bits bits = bitsA.concatenate(bitsB);
    Assert.assertEquals(String.format("Concatenate %s and %s", a, b), asString,
      bits.toBitString());
  }
  
  /**
   * Test fixed length.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testFixedLength() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testFixedLength(this.randomLong());
    }
  }
  
  /**
   * Test var longs.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testVarLongs() throws JSONException, IOException {
    for (int i = 0; i < 1000; i++) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (BitOutputStream out = new BitOutputStream(buffer)) {
        out.writeVarLong(i);
      }
      BitInputStream in = new BitInputStream(new ByteArrayInputStream(buffer.toByteArray()));
      Assert.assertEquals(i, in.readVarLong());
    }
  }
  
  private void testFixedLength(final long value) {
    String asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value, 64);
    while (asString.length() < 64) {
      asString = "0" + asString;
    }
    Assert.assertEquals("toLong for " + value, value, bits.toLong());
    Assert.assertEquals("toString for " + value, asString, bits.toBitString());
  }
  
  /**
   * Test hardcoded.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testHardcoded() throws JSONException {
    Assert.assertEquals(new Bits(0), new Bits(0));
    Assert.assertEquals("", new Bits(0).toBitString());
    Assert.assertEquals("1", new Bits(1).toBitString());
    Assert.assertEquals("100", new Bits(4).toBitString());
    Assert.assertEquals("10001", new Bits(17).toBitString());
    Assert.assertEquals("100", new Bits(17).range(0, 3).toBitString());
    Assert.assertEquals("01", new Bits(17).range(3).toBitString());
    Assert.assertEquals("111", new Bits(7).toBitString());
    Assert.assertEquals("10111", new Bits(2).concatenate(new Bits(7))
      .toBitString());
    Assert.assertEquals("00110", new Bits(6l, 5).toBitString());
    Assert.assertEquals("111000000", new Bits(7l).leftShift(6).toBitString());
    Assert.assertEquals("1110", new Bits(7l).leftShift(6).range(0, 4)
      .toBitString());
    Assert.assertEquals("00000", new Bits(7l).leftShift(6).range(4)
      .toBitString());
    Assert.assertEquals("110", new Bits(6l).toBitString());
    Assert.assertEquals("11100", new Bits(7l).leftShift(2).toBitString());
    Assert.assertEquals("11000",
      new Bits(7l).leftShift(2).bitwiseAnd(new Bits(6l)).toBitString());
    Assert.assertEquals("11100",
      new Bits(7l).leftShift(2).bitwiseOr(new Bits(6l)).toBitString());
    Assert.assertEquals("00100",
      new Bits(7l).leftShift(2).bitwiseXor(new Bits(6l)).toBitString());
    Assert.assertEquals(2, new Bits(7l, 16).getBytes().length);
  }
  
  /**
   * Test subrange.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testSubrange() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      final long value = this.random.nextLong();
      final Bits bits = new Bits(value);
      this.testSubrange(bits);
    }
  }
  
  private void testSubrange(final Bits bits) {
    final String asString = bits.toBitString();
    for (int j = 0; j < 10; j++) {
      final int from = this.random.nextInt(asString.length());
      final int to = from + this.random.nextInt(asString.length() - from);
      this.testSubrange(bits, asString, from, to);
    }
  }
  
  private void testSubrange(final Bits bits, final String asString,
    final int from, final int to) {
    final CharSequence subStr = asString.substring(from, to);
    final Bits subBits = bits.range(from, to - from);
    Assert.assertEquals(
      String.format("Substring (%s,%s) of %s", from, to, bits), subStr,
      subBits.toBitString());
  }
  
  /**
   * Test to string.
   *
   * @throws JSONException the json exception
   * @throws IOException   the io exception
   */
  @Test
  @Category(TestCategories.UnitTest.class)
  public void testToString() throws JSONException {
    for (int i = 0; i < 1000; i++) {
      this.testToString(this.randomLong());
    }
  }
  
  private void testToString(final long value) {
    final CharSequence asString = 0 == value ? "" : Long.toBinaryString(value);
    final Bits bits = new Bits(value);
    Assert.assertEquals("toLong for " + value, value, bits.toLong());
    Assert.assertEquals("toString for " + value, asString, bits.toBitString());
  }
  
}