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
import com.simiacryptus.ref.lang.RefAware;
import com.simiacryptus.ref.wrappers.RefCollectors;
import com.simiacryptus.ref.wrappers.RefLinkedHashMap;
import com.simiacryptus.ref.wrappers.RefMap;
import com.simiacryptus.ref.wrappers.RefStream;
import com.simiacryptus.ref.wrappers.RefString;
import com.simiacryptus.util.test.TestDocument;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public @RefAware
interface Compressor {
  static <T> TableOutput evalCompressor(RefStream<? extends TestDocument> data,
                                        RefMap<CharSequence, Compressor> compressors, boolean wide) {
    TableOutput wideTable = new TableOutput();
    TableOutput tallTable = new TableOutput();
    AtomicInteger index = new AtomicInteger(0);
    data.parallel().forEach(item -> {
      RefMap<CharSequence, Object> rowWide = new RefLinkedHashMap<>();
      String title;
      title = item.getTitle().toString().replaceAll("\0", "").replaceAll("\n", "\\n");
      rowWide.put("title", title);
      compressors.entrySet().parallelStream().forEach((e) -> {
        try {
          CharSequence name = e.getKey();
          Compressor compressor = e.getValue();
          RefMap<CharSequence, Object> rowTall = new RefLinkedHashMap<>();
          rowTall.put("title", title);
          rowTall.put("compressor", name);

          rowWide.put(name + ".uncompressed", item.getText().length());
          rowTall.put("uncompressed", item.getText().length());
          TimedResult<byte[]> compress = TimedResult.time(() -> compressor.compress(item.getText()));
          rowWide.put(name + ".compressed", compress.result.length);
          rowTall.put("compressed", compress.result.length);
          double ONE_MILLION = 1000000.0;
          rowWide.put(name + ".compressMs", compress.timeNanos / ONE_MILLION);
          rowTall.put("compressMs", compress.timeNanos / ONE_MILLION);
          TimedResult<CharSequence> uncompress = TimedResult.time(() -> compressor.uncompress(compress.result));
          rowWide.put(name + ".uncompressMs", uncompress.timeNanos / ONE_MILLION);
          rowTall.put("uncompressMs", uncompress.timeNanos / ONE_MILLION);
          rowWide.put(name + ".verified", uncompress.result.equals(item.getText()));
          rowTall.put("verified", uncompress.result.equals(item.getText()));
          tallTable.putRow(rowTall);
          //com.simiacryptus.ref.wrappers.RefSystem.p.println(String.format("Evaluated #%s: %s apply %s - %s chars -> %s bytes in %s sec", index.incrementAndGet(), name, title, item.text.length(), compress.obj.length, compress.timeNanos / 1000000000.0));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      wideTable.putRow(rowWide);
    });
    return wide ? wideTable : tallTable;
  }

  static <T> TableOutput evalCompressorCluster(RefStream<? extends TestDocument> data,
                                               RefMap<CharSequence, Compressor> compressors, boolean wide) {
    RefStream<Map.Entry<CharSequence, Compressor>> stream = compressors.entrySet()
        .stream();
    RefCollectors.RefCollector<Map.Entry<CharSequence, Compressor>, ?, RefMap<CharSequence, Function<TestDocument, Double>>> collector = RefCollectors
        .toMap(e -> e.getKey(), e -> {
          Compressor value = e.getValue();
          return x -> (value.compress(x.getText()).length * 1.0 / x.getText().length());
        });
    return evalCluster(data, stream.collect(collector), wide);
  }

  static <T> TableOutput evalCluster(RefStream<? extends TestDocument> data,
                                     RefMap<CharSequence, Function<TestDocument, Double>> compressors, boolean wide) {
    TableOutput wideTable = new TableOutput();
    TableOutput tallTable = new TableOutput();
    AtomicInteger index = new AtomicInteger(0);
    data.parallel().forEach(item -> {
      RefMap<CharSequence, Object> rowWide = new RefLinkedHashMap<>();
      String title;
      title = item.getTitle().toString().replaceAll("\0", "").replaceAll("\n", "\\n");
      rowWide.put("title", title);
      compressors.entrySet().parallelStream().forEach((e) -> {
        try {
          CharSequence name = e.getKey();
          Function<TestDocument, Double> compressor = e.getValue();
          RefMap<CharSequence, Object> rowTall = new RefLinkedHashMap<>();
          rowTall.put("title", title);
          rowTall.put("compressor", name);

          TimedResult<Double> compress = TimedResult.time(() -> compressor.apply(item));
          rowWide.put(name + ".value", compress.result);
          rowTall.put("value", compress.result);
          //          double ONE_MILLION = 1000000.0;
          //          rowWide.put(name + ".compressMs", compress.timeNanos / ONE_MILLION);
          //          rowTall.put("compressMs", compress.timeNanos / ONE_MILLION);
          tallTable.putRow(rowTall);
          com.simiacryptus.ref.wrappers.RefSystem.out
              .println(RefString.format("Evaluated #%s: %s apply %s - %s chars -> %s in %s sec", index.incrementAndGet(),
                  name, title, item.getText().length(), compress.result, compress.timeNanos / 1000000000.0));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      });
      wideTable.putRow(rowWide);
    });
    return wide ? wideTable : tallTable;
  }

  static void addGenericCompressors(RefMap<CharSequence, Compressor> compressors) {
    compressors.put("BZ0", new Compressor() {
      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeBZ(text);
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return CompressionUtil.decodeBZ(data);
      }
    });
    compressors.put("LZ0", new Compressor() {
      @Override
      public byte[] compress(String text) {
        return CompressionUtil.encodeLZ(text);
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return CompressionUtil.decodeLZToString(data);
      }
    });
  }

  static Compressor buildPPMCompressor(CharTrie baseTree, final int encodingContext) {
    NodewalkerCodec codec = baseTree.getCodec();
    com.simiacryptus.ref.wrappers.RefSystem.out.println(RefString.format("Encoding Tree Memory Size = %s KB", codec.inner.getMemorySize() / 1024));
    return new Compressor() {
      @Override
      public byte[] compress(String text) {
        return codec.encodePPM(text, encodingContext).getBytes();
      }

      @Override
      public CharSequence uncompress(byte[] data) {
        return codec.decodePPM(data, encodingContext);
      }
    };
  }

  byte[] compress(String text);

  CharSequence uncompress(byte[] compress);
}
