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

package com.simiacryptus.util.test;

import com.simiacryptus.ref.wrappers.RefArrayList;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.AsyncListIterator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TweetSentiment extends TestDocument {
  private static final ArrayList<TweetSentiment> queue = new ArrayList<>();
  @Nonnull
  public static String url = "http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip";
  @Nonnull
  public static String file = "Sentiment-Analysis-Dataset.zip";
  @Nullable
  private static volatile Thread thread;
  public final int category;

  public TweetSentiment(String text, int category) {
    super(text, text);
    this.category = category;
  }

  public static void clear() throws InterruptedException {
    if (thread != null) {
      synchronized (WikiArticle.class) {
        if (thread != null) {
          thread.interrupt();
          thread.join();
          thread = null;
          queue.clear();
        }
      }
    }
  }

  @Nonnull
  public static Stream<TweetSentiment> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(() -> read());
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    Iterator<TweetSentiment> iterator = new AsyncListIterator<TweetSentiment>(new RefArrayList<>(queue), thread);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT), false)
        .filter(x -> x != null);
  }

  private static void read() {
    try {
      InputStream load = Util.cacheLocal(file, new URI(url));
      try (final ZipInputStream in = new ZipInputStream(load)) {
        ZipEntry entry = in.getNextEntry();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
          CharSequence[] header = reader.readLine().split(",");
          String read;
          while (null != (read = reader.readLine())) {
            String[] values = read.split(",");
            queue.add(new TweetSentiment(values[3].trim(), Integer.parseInt(values[1].trim())));
          }
        }
      }
    } catch (@Nonnull final IOException e) {
      // Ignore... end of stream
    } catch (@Nonnull final RuntimeException e) {
      if (!(e.getCause() instanceof InterruptedException))
        throw e;
    } catch (@Nonnull final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
