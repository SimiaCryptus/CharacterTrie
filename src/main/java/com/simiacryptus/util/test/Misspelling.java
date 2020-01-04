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

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public @com.simiacryptus.ref.lang.RefAware
class Misspelling extends TestDocument {

  public static Loader BIRKBECK = new Loader(URI.create("http://www.dcs.bbk.ac.uk/~ROGER/missp.dat"), 10000);

  public Misspelling(String correct, CharSequence misspelling) {
    super(correct, misspelling);
  }

  public static @com.simiacryptus.ref.lang.RefAware
  class Loader {
    private final String url;
    private final String file;
    private final int articleLimit;
    private final List<Misspelling> queue = Collections.synchronizedList(new ArrayList<>());
    private volatile Thread thread;

    public Loader(URI uri, int articleLimit) {
      url = uri.toString();
      this.articleLimit = articleLimit;
      String path = uri.getPath();
      String[] split = path.split("/");
      file = split[split.length - 1];
    }

    public void clear() throws InterruptedException {
      if (thread != null) {
        synchronized (Misspelling.class) {
          if (thread != null) {
            thread.interrupt();
            thread.join();
            thread = null;
            queue.clear();
          }
        }
      }
    }

    public Stream<Misspelling> load() {
      if (thread == null) {
        synchronized (Misspelling.class) {
          if (thread == null) {
            thread = new Thread(this::read);
            thread.setDaemon(true);
            thread.start();
          }
        }
      }
      return StreamSupport
          .stream(Spliterators.spliteratorUnknownSize(queue.iterator(), Spliterator.DISTINCT),
              false)
          .filter(x -> x != null);
    }

    private void read() {
      try {
        try (final InputStream in = Util.cacheLocal(file, new URI(url))) {
          String txt = new String(IOUtils.toByteArray(in), "UTF-8").replaceAll("\r", "");
          CharSequence[] list = txt.split("\n");
          String activeItem = "";
          for (CharSequence item : list) {
            if (item.toString().startsWith("$")) {
              activeItem = item.toString().substring(1);
            } else {
              queue.add(new Misspelling(activeItem, item));
            }
          }

        }
      } catch (final RuntimeException e) {
        if (!(e.getCause() instanceof InterruptedException))
          e.printStackTrace();
      } catch (final Exception e) {
        e.printStackTrace();
      } finally {
        System.err.println("Read thread exit");
      }
    }

  }

}
