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

package com.simiacryptus.util.binary.codes;

import com.simiacryptus.util.binary.BitInputStream;
import com.simiacryptus.util.binary.BitOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GaussianTest {
  @Test
  @Tag("UnitTest")
  public void testBinomialRandom() throws IOException {
    final Random random = new Random();
    for (int i = 0; i < 100; i++) {
      double probability = 0;
      while (0 >= probability || 1 <= probability) {
        probability = random.nextDouble();
      }
      for (int max = 1; max < 255; max += 1) {
        this.test(Gaussian.fromBinomial(probability, max), max);
      }
    }
  }

  @Test
  @Tag("UnitTest")
  public void testBinomialScan() throws IOException {
    for (double probability = 0.01; probability <= 0.99; probability += .01) {
      for (int max = 1; max < 255; max += 1) {
        @SuppressWarnings("unused") final double result = this.test(Gaussian.fromBinomial(probability, max), max);
        // com.simiacryptus.ref.wrappers.System.p.println(String.format("P=%s,N=%s: %s", probability, max, result));
      }
    }
  }

  @Test
  @Tag("UnitTest")
  public void testHardcodedGaussians() throws IOException {
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(100, 3), 255)));
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(100, 10), 255)));
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(100, 200), 255)));
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(100, 500), 255)));
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(500, 10), 255)));
    System.out
        .println(String.format("T: %s", this.test(new Gaussian(-100, 10), 255)));
    System.out
        .println(String.format("T: %s", this.test(Gaussian.fromBinomial(0.7, 3), 3)));
    System.out
        .println(String.format("T: %s", this.test(Gaussian.fromBinomial(0.5, 1), 1)));
  }

  @Test
  @Tag("UnitTest")
  public void testZeros() throws IOException {
    for (int value = 0; value <= 0; value++) {
      final Gaussian gaussian = new Gaussian(100, 10);
      final byte[] serializedData = this.encode(gaussian, 0, 0);
      assertEquals(0, serializedData.length);
      final long decoded = this.decode(gaussian, 0, serializedData);
      assertEquals(value, decoded);
    }
  }

  private long decode(@Nonnull final Gaussian gaussian, final int max, @Nonnull final byte[] serializedData) throws IOException {
    final ByteArrayInputStream inBuffer = new ByteArrayInputStream(serializedData);
    final BitInputStream in = new BitInputStream(inBuffer);
    return gaussian.decode(in, max);
  }

  @Nonnull
  private byte[] encode(@Nonnull final Gaussian gaussian, final int max, final int i) throws IOException {
    final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    final BitOutputStream out = new BitOutputStream(outBuffer);
    gaussian.encode(out, i, max);
    out.flush();
    return outBuffer.toByteArray();
  }

  private double test(@Nonnull final Gaussian gaussian, final int max) throws IOException {
    int total = 0;
    for (int value = 0; value <= max; value++) {
      total += this.test(gaussian, max, value);
    }
    return (double) total / max;
  }

  private int test(@Nonnull final Gaussian gaussian, final int max, final int value) throws IOException {
    final byte[] serializedData = this.encode(gaussian, max, value);
    final long decoded = this.decode(gaussian, max, serializedData);
    assertEquals(value, decoded);
    return serializedData.length;
  }
}
