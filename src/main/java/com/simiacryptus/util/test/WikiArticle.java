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
import com.simiacryptus.util.io.DataLoader;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

public class WikiArticle extends TestDocument {

  public static WikiDataLoader ENGLISH = new WikiDataLoader(URI.create(
      "https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles.xml.bz2"), 10000);
  public static WikiDataLoader GERMAN = new WikiDataLoader(URI.create(
      "https://dumps.wikimedia.org/dewiki/latest/dewiki-latest-pages-articles.xml.bz2"), 10000);
  public static WikiDataLoader FRENCH = new WikiDataLoader(URI.create(
      "https://dumps.wikimedia.org/frwiki/latest/frwiki-latest-pages-articles.xml.bz2"), 10000);

  public WikiArticle(String title, String text) {
    super(title, text);
  }

  public static class WikiDataLoader extends DataLoader<WikiArticle> {
    protected final String url;
    protected final String file;
    protected final int articleLimit;

    public WikiDataLoader(URI uri, int articleLimit) {
      super();
      this.url = uri.toString();
      this.articleLimit = articleLimit;
      String path = uri.getPath();
      String[] split = path.split("/");
      file = split[split.length - 1];
    }

    @Override
    protected void read(List<WikiArticle> queue) {
      try {
        try (final InputStream in = new BZip2CompressorInputStream(Util.cacheLocal(file, new URI(url)), true)) {
          final SAXParserFactory spf = SAXParserFactory.newInstance();
          spf.setNamespaceAware(false);
          final SAXParser saxParser = spf.newSAXParser();
          saxParser.parse(in, new DefaultHandler() {
            Stack<CharSequence> prefix = new Stack<CharSequence>();
            Stack<Map<CharSequence, AtomicInteger>> indexes = new Stack<Map<CharSequence, AtomicInteger>>();
            StringBuilder nodeString = new StringBuilder();
            private String title;

            @Override
            public void characters(final char[] ch, final int start,
                                   final int length) throws SAXException {
              if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException(new InterruptedException());
              }
              this.nodeString.append(ch, start, length);
              super.characters(ch, start, length);
            }

            @Override
            public void endDocument() throws SAXException {
              super.endDocument();
            }

            @Override
            public void endElement(final String uri, final String localName,
                                   final String qName) throws SAXException {
              if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException(new InterruptedException());
              }
              final CharSequence pop = this.prefix.pop();
              this.indexes.pop();

              final int length = this.nodeString.length();
              String text = this.nodeString.toString().trim();
              this.nodeString = new StringBuilder();

              if ("page".equals(qName)) {
                this.title = null;
              } else if ("title".equals(qName)) {
                this.title = text;
              } else if ("text".equals(qName)) {
                //System.p.println(String.format("Read #%s - %s", queue.size(), this.title));
                queue.add(new WikiArticle(this.title, text));
                if (queue.size() > articleLimit) {
                  throw new RuntimeException(new InterruptedException());
                }
              }
              super.endElement(uri, localName, qName);
            }

            @Override
            public void startDocument() throws SAXException {
              super.startDocument();
            }

            @Override
            public void startElement(final String uri, final String localName,
                                     final String qName, final Attributes attributes)
                throws SAXException {
              if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException(new InterruptedException());
              }
              int idx;
              if (0 < this.indexes.size()) {
                final Map<CharSequence, AtomicInteger> index = this.indexes.peek();
                AtomicInteger cnt = index.get(qName);
                if (null == cnt) {
                  cnt = new AtomicInteger(-1);
                  index.put(qName, cnt);
                }
                idx = cnt.incrementAndGet();
              } else {
                idx = 0;
              }
              String path = 0 == this.prefix.size() ? qName : this.prefix.peek() + "/" + qName;
              if (0 < idx) {
                path += "[" + idx + "]";
              }
              this.prefix.push(path);
              this.indexes.push(new HashMap<CharSequence, AtomicInteger>());
              super.startElement(uri, localName, qName, attributes);
            }

          }, null);
        }
      } catch (final RuntimeException e) {
        if (!(e.getCause() instanceof InterruptedException)) e.printStackTrace();
      } catch (final Exception e) {
        e.printStackTrace();
      } finally {
        System.err.println("Read thread exit");
      }
    }
  }

}
