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
class EnglishWords extends TestDocument {
  private static final com.simiacryptus.ref.wrappers.RefArrayList<EnglishWords> queue = new com.simiacryptus.ref.wrappers.RefArrayList<>();
  public static String url = "https://raw.githubusercontent.com/first20hours/google-10000-english/master/20k.txt";
  public static String file = "20k.txt";
  private static volatile Thread thread;

  public EnglishWords(CharSequence text) {
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

  public static com.simiacryptus.ref.wrappers.RefStream<EnglishWords> load() {
    if (thread == null) {
      synchronized (WikiArticle.class) {
        if (thread == null) {
          thread = new Thread(EnglishWords::read);
          thread.setDaemon(true);
          thread.start();
        }
      }
    }
    com.simiacryptus.ref.wrappers.RefIteratorBase<EnglishWords> iterator = new AsyncListIterator<>(queue, thread);
    return com.simiacryptus.ref.wrappers.RefStreamSupport
        .stream(com.simiacryptus.ref.wrappers.RefSpliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT),
            false)
        .filter(x -> x != null);
  }

  private static void read() {
    try {
      InputStream in = Util.cacheLocal(file, new URI(url));
      String txt = new String(IOUtils.toByteArray(in), "UTF-8").replaceAll("\r", "");
      com.simiacryptus.ref.wrappers.RefList<CharSequence> list = com.simiacryptus.ref.wrappers.RefArrays
          .stream(txt.split("\n")).map(x -> x.replaceAll("[^\\w]", ""))
          .collect(com.simiacryptus.ref.wrappers.RefCollectors.toList());
      com.simiacryptus.ref.wrappers.RefCollections.shuffle(list);
      for (CharSequence paragraph : list) {
        queue.add(new EnglishWords(paragraph));
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
