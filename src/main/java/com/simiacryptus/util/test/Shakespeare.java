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
import org.apache.commons.compress.utils.IOUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Shakespeare extends TestDocument {
  private static final ArrayList<Shakespeare> queue = new ArrayList<>();
  @Nonnull
  public static String url = "http://www.gutenberg.org/cacheLocal/epub/100/pg100.txt";
  @Nonnull
  public static String file = "Shakespeare.txt";
  @Nullable
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

  @Nonnull
  public static Stream<Shakespeare> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(() -> read());
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    Iterator<Shakespeare> iterator = new AsyncListIterator<>(new RefArrayList<>(queue), thread);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT), false)
        .filter(x -> x != null);
  }

  private static void read() {
    try {
      InputStream in = Util.cacheLocal(file, new URI(url));
      String txt = new String(IOUtils.toByteArray(in), "UTF-8").replaceAll("\r", "");
      for (String paragraph : txt.split("\n\\s*\n")) {
        queue.add(new Shakespeare(paragraph));
      }
    } catch (@Nonnull final IOException e) {
      // Ignore... end of stream
    } catch (@Nonnull final RuntimeException e) {
      if (!(e.getCause() instanceof InterruptedException))
        throw e;
    } catch (@Nonnull final Exception e) {
      throw Util.throwException(e);
    }
  }
}
