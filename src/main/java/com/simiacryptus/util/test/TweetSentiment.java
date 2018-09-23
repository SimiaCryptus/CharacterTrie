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

package com.simiacryptus.util.test;

import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.AsyncListIterator;

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

/**
 * The type Tweet sentiment.
 */
public class TweetSentiment extends TestDocument {
  private static final ArrayList<TweetSentiment> queue = new ArrayList<>();
  /**
   * The constant url.
   */
  public static String url = "http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip";
  /**
   * The constant file.
   */
  public static String file = "Sentiment-Analysis-Dataset.zip";
  private static volatile Thread thread;
  /**
   * The Category.
   */
  public final int category;

  /**
   * Instantiates a new Tweet sentiment.
   *
   * @param text     the text
   * @param category the category
   */
  public TweetSentiment(String text, int category) {
    super(text, text);
    this.category = category;
  }

  /**
   * Clear.
   *
   * @throws InterruptedException the interrupted exception
   */
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

  /**
   * Load stream.
   *
   * @return the stream
   */
  public static Stream<TweetSentiment> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(TweetSentiment::read);
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    Iterator<TweetSentiment> iterator = new AsyncListIterator<>(queue, thread);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT), false).filter(x -> x != null);
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
    } catch (final IOException e) {
      // Ignore... end of stream
    } catch (final RuntimeException e) {
      if (!(e.getCause() instanceof InterruptedException)) throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
