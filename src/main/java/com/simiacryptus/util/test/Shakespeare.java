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

import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.AsyncListIterator;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Spliterator;

public @com.simiacryptus.ref.lang.RefAware
class Shakespeare extends TestDocument {
  private static final com.simiacryptus.ref.wrappers.RefArrayList<Shakespeare> queue = new com.simiacryptus.ref.wrappers.RefArrayList<>();
  public static String url = "http://www.gutenberg.org/cacheLocal/epub/100/pg100.txt";
  public static String file = "Shakespeare.txt";
  private static volatile Thread thread;

  public Shakespeare(String text) {
    super(text, text);
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

  public static com.simiacryptus.ref.wrappers.RefStream<Shakespeare> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(Shakespeare::read);
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    com.simiacryptus.ref.wrappers.RefIteratorBase<Shakespeare> iterator = new AsyncListIterator<>(queue, thread);
    return com.simiacryptus.ref.wrappers.RefStreamSupport
        .stream(com.simiacryptus.ref.wrappers.RefSpliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT),
            false)
        .filter(x -> x != null);
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
      if (!(e.getCause() instanceof InterruptedException))
        throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
