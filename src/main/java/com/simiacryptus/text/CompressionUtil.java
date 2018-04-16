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

import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import com.davidehrmann.vcdiff.VCDiffEncoder;
import com.davidehrmann.vcdiff.VCDiffEncoderBuilder;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The type Compression util.
 */
public class CompressionUtil {
  /**
   * The constant random.
   */
  public static final Random random = new Random();
  
  /**
   * Encode lz byte [ ].
   *
   * @param data       the data
   * @param dictionary the dictionary
   * @return the byte [ ]
   */
  public static byte[] encodeLZ(CharSequence data, String dictionary) {
    byte[] asBytes = new byte[0];
    try {
      asBytes = String.valueOf(data).getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return encodeLZ(asBytes, dictionary);
  }
  
  /**
   * Encode lz byte [ ].
   *
   * @param bytes the bytes
   * @return the byte [ ]
   */
  public static byte[] encodeLZ(byte[] bytes) {
    return encodeLZ(bytes, "");
  }
  
  /**
   * Encode lz byte [ ].
   *
   * @param bytes      the bytes
   * @param dictionary the dictionary
   * @return the byte [ ]
   */
  public static byte[] encodeLZ(byte[] bytes, String dictionary) {
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
    return Arrays.copyOf(output, compressedDataLength);
  }
  
  /**
   * Decode lz byte [ ].
   *
   * @param data       the data
   * @param dictionary the dictionary
   * @return the byte [ ]
   */
  public static byte[] decodeLZ(byte[] data, String dictionary) {
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
      return Arrays.copyOfRange(result, 0, resultLength);
    } catch (DataFormatException | UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Encode lz byte [ ].
   *
   * @param data the data
   * @return the byte [ ]
   */
  public static byte[] encodeLZ(CharSequence data) {
    return encodeLZ(data, "");
  }
  
  /**
   * Decode lz to string string.
   *
   * @param data       the data
   * @param dictionary the dictionary
   * @return the string
   */
  public static CharSequence decodeLZToString(byte[] data, CharSequence dictionary) {
    try {
      return new String(decodeLZ(data), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Decode lz to string string.
   *
   * @param data the data
   * @return the string
   */
  public static String decodeLZToString(byte[] data) {
    try {
      return new String(decodeLZ(data), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Decode lz byte [ ].
   *
   * @param data the data
   * @return the byte [ ]
   */
  public static byte[] decodeLZ(byte[] data) {
    return decodeLZ(data, "");
  }
  
  /**
   * Decode bz string.
   *
   * @param data the data
   * @return the string
   */
  public static CharSequence decodeBZ(byte[] data) {
    try {
      return new String(decodeBZRaw(data), "UTF-8");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Decode bz raw byte [ ].
   *
   * @param data the data
   * @return the byte [ ]
   */
  public static byte[] decodeBZRaw(byte[] data) {
    try {
      ByteArrayInputStream output = new ByteArrayInputStream(data);
      BZip2CompressorInputStream compresser = new BZip2CompressorInputStream(output);
      return IOUtils.toByteArray(compresser);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Encode bz byte [ ].
   *
   * @param data the data
   * @return the byte [ ]
   */
  public static byte[] encodeBZ(String data) {
    try {
      byte[] bytes = encodeBZ(data.getBytes("UTF-8"));
      //assert(data.equals(decodeBZ(bytes)));
      return bytes;
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Encode bz byte [ ].
   *
   * @param data the data
   * @return the byte [ ]
   */
  public static byte[] encodeBZ(byte[] data) {
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
  
  /**
   * Decode bz string.
   *
   * @param data       the data
   * @param dictionary the dictionary
   * @return the string
   */
  public static CharSequence decodeBZ(byte[] data, String dictionary) {
    try {
      byte[] dictBytes = dictionary.getBytes("UTF-8");
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      VCDiffDecoderBuilder.builder().buildSimple().decode(dictBytes, decodeBZRaw(data), buffer);
      return new String(buffer.toByteArray(), "UTF-8");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Encode bz byte [ ].
   *
   * @param data       the data
   * @param dictionary the dictionary
   * @return the byte [ ]
   */
  public static byte[] encodeBZ(String data, String dictionary) {
    try {
      byte[] bytes = encodeBZ(data.getBytes("UTF-8"), dictionary);
      //assert(data.equals(decodeBZ(bytes, dictionary)));
      return bytes;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Encode bz byte [ ].
   *
   * @param asBytes    the as bytes
   * @param dictionary the dictionary
   * @return the byte [ ]
   */
  public static byte[] encodeBZ(byte[] asBytes, String dictionary) {
    try {
      byte[] dictBytes = dictionary.getBytes("UTF-8");
      VCDiffEncoder<OutputStream> encoder = VCDiffEncoderBuilder.builder()
        .withDictionary(dictBytes)
        .buildSimple();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      encoder.encode(asBytes, buffer);
      return encodeBZ(buffer.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Display str string.
   *
   * @param str the str
   * @return the string
   */
  public static CharSequence displayStr(String str) {
    return str.replaceAll("\\\\", "\\\\").replaceAll("\n", "\\n").replaceAll("\0", "\\\\0");
  }
}
