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

import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import com.davidehrmann.vcdiff.VCDiffEncoder;
import com.davidehrmann.vcdiff.VCDiffEncoderBuilder;
import com.simiacryptus.ref.wrappers.RefArrays;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressionUtil {
  public static final Random random = new Random();

  @Nonnull
  public static byte[] encodeLZ(CharSequence data, String dictionary) {
    byte[] asBytes = new byte[0];
    try {
      asBytes = String.valueOf(data).getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return encodeLZ(asBytes, dictionary);
  }

  @Nonnull
  public static byte[] encodeLZ(@Nonnull byte[] bytes) {
    return encodeLZ(bytes, "");
  }

  @Nonnull
  public static byte[] encodeLZ(@Nonnull byte[] bytes, @Nullable String dictionary) {
    byte[] output = new byte[(int) (bytes.length * 1.05 + 32)];
    Deflater compresser = new Deflater();
    try {
      compresser.setInput(bytes);
      if (null != dictionary && !dictionary.isEmpty()) {
        byte[] bytes2 = dictionary.getBytes("UTF-8");
        compresser.setDictionary(bytes2);
      }
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    compresser.finish();
    int compressedDataLength = compresser.deflate(output);
    compresser.end();
    return RefArrays.copyOf(output, compressedDataLength);
  }

  @Nonnull
  public static byte[] decodeLZ(@Nonnull byte[] data, @Nonnull String dictionary) {
    try {
      Inflater decompresser = new Inflater();
      decompresser.setInput(data, 0, data.length);
      byte[] result = new byte[data.length * 32];
      int resultLength = 0;
      if (!dictionary.isEmpty()) {
        resultLength = decompresser.inflate(result);
        assert (0 == resultLength);
        if (decompresser.needsDictionary()) {
          byte[] bytes = dictionary.getBytes("UTF-8");
          decompresser.setDictionary(bytes);
        }
      }
      resultLength = decompresser.inflate(result);
      decompresser.end();
      return RefArrays.copyOfRange(result, 0, resultLength);
    } catch (@Nonnull DataFormatException | UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] encodeLZ(CharSequence data) {
    return encodeLZ(data, "");
  }

  @Nonnull
  public static CharSequence decodeLZToString(@Nonnull byte[] data, CharSequence dictionary) {
    try {
      return new String(decodeLZ(data), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static String decodeLZToString(@Nonnull byte[] data) {
    try {
      return new String(decodeLZ(data), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] decodeLZ(@Nonnull byte[] data) {
    return decodeLZ(data, "");
  }

  @Nonnull
  public static CharSequence decodeBZ(@Nonnull byte[] data) {
    try {
      return new String(decodeBZRaw(data), "UTF-8");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] decodeBZRaw(@Nonnull byte[] data) {
    try {
      ByteArrayInputStream output = new ByteArrayInputStream(data);
      BZip2CompressorInputStream compresser = new BZip2CompressorInputStream(output);
      return IOUtils.toByteArray(compresser);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] encodeBZ(@Nonnull String data) {
    try {
      byte[] bytes = encodeBZ(data.getBytes("UTF-8"));
      //assert(data.equals(decodeBZ(bytes)));
      return bytes;
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] encodeBZ(@Nonnull byte[] data) {
    try {
      int blockSize = 4;
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BZip2CompressorOutputStream compresser = new BZip2CompressorOutputStream(output, blockSize);
      compresser.write(data);
      compresser.close();
      byte[] bytes = output.toByteArray();
      return bytes;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static CharSequence decodeBZ(@Nonnull byte[] data, @Nonnull String dictionary) {
    try {
      byte[] dictBytes = dictionary.getBytes("UTF-8");
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      VCDiffDecoderBuilder.builder().buildSimple().decode(dictBytes, decodeBZRaw(data), buffer);
      return new String(buffer.toByteArray(), "UTF-8");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] encodeBZ(@Nonnull String data, @Nonnull String dictionary) {
    try {
      byte[] bytes = encodeBZ(data.getBytes("UTF-8"), dictionary);
      //assert(data.equals(decodeBZ(bytes, dictionary)));
      return bytes;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static byte[] encodeBZ(@Nonnull byte[] asBytes, @Nonnull String dictionary) {
    try {
      byte[] dictBytes = dictionary.getBytes("UTF-8");
      VCDiffEncoder<OutputStream> encoder = VCDiffEncoderBuilder.builder().withDictionary(dictBytes).buildSimple();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      encoder.encode(asBytes, buffer);
      return encodeBZ(buffer.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  public static CharSequence displayStr(@Nonnull String str) {
    return str.replaceAll("\\\\", "\\\\").replaceAll("\n", "\\n").replaceAll("\0", "\\\\0");
  }
}
