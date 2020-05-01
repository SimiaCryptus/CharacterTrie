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

import com.simiacryptus.lang.TimedResult;
import com.simiacryptus.notebook.TableOutput;
import com.simiacryptus.ref.lang.RefUtil;
import com.simiacryptus.util.test.TestDocument;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Compressor {
  @Nonnull
  static <T> TableOutput evalCompressor(@Nonnull Stream<? extends TestDocument> data,
                                        @Nonnull Map<CharSequence, Compressor> compressors, boolean wide) {
    TableOutput wideTable = new TableOutput();
    TableOutput tallTable = new TableOutput();
    AtomicInteger index = new AtomicInteger(0);
    data.parallel().forEach(item -> {
      Map<CharSequence, Object> rowWide = new LinkedHashMap<>();
      String title = item.getTitle().toString().replaceAll("\0", "").replaceAll("\n", "\\n");
      rowWide.put("title", title);
      compressors.entrySet().parallelStream().forEach(e -> {
        try {
          CharSequence name = e.getKey();
          Compressor compressor = e.getValue();
          RefUtil.freeRef(e);
          Map<CharSequence, Object> rowTall = new LinkedHashMap<>();
          rowTall.put("title", title);
          rowTall.put("compressor", name);

          rowWide.put(name + ".uncompressed", item.getText().length());
          rowTall.put("uncompressed", item.getText().length());
          TimedResult<byte[]> compress = TimedResult.time(() -> compressor.compress(item.getText()));
          byte[] result = compress.getResult();
          rowWide.put(name + ".compressed", result.length);
          rowTall.put("compressed", result.length);
          double ONE_MILLION = 1000000.0;
          rowWide.put(name + ".compressMs", compress.timeNanos / ONE_MILLION);
          rowTall.put("compressMs", compress.timeNanos / ONE_MILLION);
          compress.freeRef();
          TimedResult<CharSequence> uncompress = TimedResult.time(() -> compressor.uncompress(result));
          rowWide.put(name + ".uncompressMs", uncompress.timeNanos / ONE_MILLION);
          rowTall.put("uncompressMs", uncompress.timeNanos / ONE_MILLION);
          CharSequence uncompressResult = uncompress.getResult();
          rowWide.put(name + ".verified", uncompressResult.equals(item.getText()));
          rowTall.put("verified", uncompressResult.equals(item.getText()));
          uncompress.freeRef();
          tallTable.putRow(rowTall);
          //com.simiacryptus.ref.wrappers.System.p.println(String.format("Evaluated #%s: %s apply %s - %s chars -> %s bytes in %s sec", index.incrementAndGet(), name, title, item.text.length(), compress.obj.length, compress.timeNanos / 1000000000.0));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      wideTable.putRow(rowWide);
    });
    return wide ? wideTable : tallTable;
  }

  @Nonnull
  static <T> TableOutput evalCompressorCluster(@Nonnull Stream<? extends TestDocument> data,
                                               @Nonnull Map<CharSequence, Compressor> compressors, boolean wide) {
    Stream<Map.Entry<CharSequence, Compressor>> stream = compressors.entrySet().stream();
    Collector<Map.Entry<CharSequence, Compressor>, ?, Map<CharSequence, Function<TestDocument, Double>>> collector = Collectors
        .toMap(Map.Entry::getKey, e -> {
          Compressor value = e.getValue();
          RefUtil.freeRef(e);
          return x -> value.compress(x.getText()).length * 1.0 / x.getText().length();
        });
    return evalCluster(data, stream.collect(collector), wide);
  }

  @Nonnull
  static <T> TableOutput evalCluster(@Nonnull Stream<? extends TestDocument> data,
                                     @Nonnull Map<CharSequence, Function<TestDocument, Double>> compressors, boolean wide) {
    TableOutput wideTable = new TableOutput();
    TableOutput tallTable = new TableOutput();
    AtomicInteger index = new AtomicInteger(0);
    data.parallel().forEach(item -> {
      Map<CharSequence, Object> rowWide = new LinkedHashMap<>();
      String title = item.getTitle().toString().replaceAll("\0", "").replaceAll("\n", "\\n");
      rowWide.put("title", title);
      compressors.entrySet().parallelStream().forEach(e -> {
        try {
          CharSequence name = e.getKey();
          Function<TestDocument, Double> compressor = e.getValue();
          RefUtil.freeRef(e);
          Map<CharSequence, Object> rowTall = new LinkedHashMap<>();
          rowTall.put("title", title);
          rowTall.put("compressor", name);

          TimedResult<Double> compress = TimedResult.time(() -> compressor.apply(item));
          Double result = compress.getResult();
          long timeNanos = compress.timeNanos;
          compress.freeRef();
          rowWide.put(name + ".value", result);
          rowTall.put("value", result);
          //          double ONE_MILLION = 1000000.0;
          //          rowWide.put(name + ".compressMs", compress.timeNanos / ONE_MILLION);
          //          rowTall.put("compressMs", compress.timeNanos / ONE_MILLION);
          tallTable.putRow(rowTall);
          System.out.println(
              String.format("Evaluated #%s: %s apply %s - %s chars -> %s in %s sec", index.incrementAndGet(), name,
                  title, item.getText().length(), result, timeNanos / 1000000000.0));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      wideTable.putRow(rowWide);
    });
    return wide ? wideTable : tallTable;
  }

  static void addGenericCompressors(@Nonnull Map<CharSequence, Compressor> compressors) {
    compressors.put("BZ0", new Compressor() {
      @Nonnull
      @Override
      public byte[] compress(@Nonnull CharSequence text) {
        return CompressionUtil.encodeBZ(text.toString());
      }

      @Nonnull
      @Override
      public CharSequence uncompress(@Nonnull byte[] data) {
        return CompressionUtil.decodeBZ(data);
      }
    });
    compressors.put("LZ0", new Compressor() {
      @Nonnull
      @Override
      public byte[] compress(CharSequence text) {
        return CompressionUtil.encodeLZ(text);
      }

      @Nonnull
      @Override
      public CharSequence uncompress(@Nonnull byte[] data) {
        return CompressionUtil.decodeLZToString(data);
      }
    });
  }

  @Nonnull
  static Compressor buildPPMCompressor(@Nonnull CharTrie baseTree, final int encodingContext) {
    NodewalkerCodec codec = baseTree.getCodec();
    System.out
        .println(String.format("Encoding Tree Memory Size = %s KB", codec.inner.getMemorySize() / 1024));
    return new Compressor() {
      @Nonnull
      @Override
      public byte[] compress(@Nonnull CharSequence text) {
        return codec.encodePPM(text, encodingContext).getBytes();
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return codec.decodePPM(data, encodingContext);
      }
    };
  }

  @Nonnull
  byte[] compress(CharSequence text);

  CharSequence uncompress(byte[] compress);
}
