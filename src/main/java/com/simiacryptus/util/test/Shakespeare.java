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
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The type Shakespeare.
 */
public class Shakespeare extends TestDocument {
  private static final ArrayList<Shakespeare> queue = new ArrayList<>();
  /**
   * The constant url.
   */
  public static String url = "http://www.gutenberg.org/cacheLocal/epub/100/pg100.txt";
  /**
   * The constant file.
   */
  public static String file = "Shakespeare.txt";
  private static volatile Thread thread;

  /**
   * Instantiates a new Shakespeare.
   *
   * @param text the text
   */
  public Shakespeare(String text) {
    super(text, text);
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
  public static Stream<Shakespeare> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(Shakespeare::read);
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    Iterator<Shakespeare> iterator = new AsyncListIterator<>(queue, thread);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT), false).filter(x -> x != null);
  }

  private static void read() {
    try {
      InputStream in = Util.cacheLocal(file, new URI(url));
      String txt = new String(IOUtils.toByteArray(in), "UTF-8").replaceAll("\r", "");
      for (String paragraph : txt.split("\n\\s*\n")) {
        queue.add(new Shakespeare(paragraph));
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
