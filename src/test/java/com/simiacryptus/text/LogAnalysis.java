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

package com.simiacryptus.text;

import com.simiacryptus.notebook.MarkdownNotebookOutput;
import com.simiacryptus.notebook.NotebookOutput;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogAnalysis {


  @Test
  @Tag("Report")
  public void analyzeFailedLogs() throws IOException {
    try (NotebookOutput log = MarkdownNotebookOutput.get("logAnalysis")) {

      Map<String, List<File>> byStatus = FileUtils.listFiles(new File("H:/gh_az_logs"), new String[]{"log"}, true)
          .stream().collect(Collectors.groupingBy(file -> file.getParentFile().getName().toUpperCase()));

      Map<File, CharSequence> failedLogs = byStatus.get("FAILED").stream().collect(Collectors.toMap(f -> f, f -> {
        try {
          return (CharSequence) FileUtils.readFileToString(f, "UTF-8");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }));
      System.out.println(String.format("Total Failed Log Bytes: %s", failedLogs.values().stream().map(x -> x.length()).reduce((a, b) -> a + b).get()));

      Map<File, CharSequence> successLogs = byStatus.get("SUCCEEDED").stream().collect(Collectors.toMap(f -> f, f -> {
        try {
          return (CharSequence) FileUtils.readFileToString(f, "UTF-8");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }));
      System.out.println(String.format("Total Succeeded Log Bytes: %s", failedLogs.values().stream().map(x -> x.length()).reduce((a, b) -> a + b).get()));

      Function<CharSequence, IntStream> cursorSeeds = doc -> IntStream.range(0, doc.length()).filter(n -> {
        if (n == 0) return true;
        char c = doc.charAt(n - 1);
        return c == ' ' || c == '\t' || c == '\n';
      });

//      int maxLevels = 24;
//      CharTrie failedIndex = log.eval(() -> {
//        return CharTrieIndex.create(failedLogs.values(), maxLevels, 1,
//            cursorSeeds).truncate().copy();
//      });
//      CharTrie successIndex = log.eval(() -> {
//        return CharTrieIndex.create(successLogs.values(), maxLevels, 1,
//            cursorSeeds).truncate().copy();
//      });
//      Pattern regex = Pattern.compile("^[\\w\\s]+$");
//      log.eval(() -> {
//        TreeMap<Double, String> indicators = new TreeMap<>();
//        failedIndex.root().visitFirst(node -> {
//          String string = node.getString();
//          if (regex.matcher(string).matches()) {
//            TrieNode traverse = successIndex.traverse(string);
//            if (!traverse.getString().equals(string)) {
//              NodeData nodeData = node.getData();
//              indicators.put((double) (nodeData.cursorCount * string.length()), string);
//              while (indicators.size() > 1000) indicators.pollFirstEntry();
//            }
//          }
//        });
//        indicators.forEach((k, v) -> {
//          System.out.println(String.format("Final %s - %s", k, v));
//        });
//        return JsonUtil.toJson(indicators);
//      });

//      failedLogs.forEach((file, content) -> {
//        int[] breaks = cursorSeeds.apply(content).toArray();
//        ArrayList<CharSequence> words = new ArrayList<>();
//        for (int i = 1; i < breaks.length; i++) {
//          words.add(content.subSequence(breaks[i-1], breaks[i]));
//        }
//        StringBuilder filteredContent = new StringBuilder();
//        while(!words.isEmpty()) {
//          StringBuilder stringBuilder = new StringBuilder();
//          while(stringBuilder.length() < 10 && !words.isEmpty())
//            stringBuilder.append(words.remove(0));
//          if(!successIndex.contains(stringBuilder.toString()))
//            filteredContent.append(stringBuilder);
//        }
//        File filterdFile = new File(file.getAbsolutePath().replace(".log", ".filtered"));
//        try {
//          FileUtils.write(filterdFile, filteredContent.toString(), "UTF-8");
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//        System.out.println(String.format("Filtered %s bytes to %s bytes in %s", content.length(), filteredContent.length(), filterdFile));
//      });


      Map<Pattern, String> patterns = new HashMap<>();
      patterns.put(Pattern.compile("One or more hosts failed"), "Hosts Failed");
      patterns.put(Pattern.compile("No entry for .* found in cluster management"), "No Cluster");
      patterns.put(Pattern.compile("Cannot load main class from JAR"), "Bad JAR");
      patterns.put(Pattern.compile("Failed to build job executor for job"), "Bad Job");
      failedLogs.forEach((file, content) -> {
        List<String> matchedPatterns = patterns.entrySet().stream().filter(entry -> {
          return entry.getKey().matcher(content).find();
        }).map(entry -> entry.getValue()).collect(Collectors.toList());
        if (matchedPatterns.isEmpty()) {
          System.out.println(String.format("No patterns matched for %s", file));
        } else if (matchedPatterns.size() == 1) {
          System.out.println(String.format("One pattern matched for %s - %s", file, matchedPatterns.get(0)));
        } else {
          System.out.println(String.format("Multiple patterns matched for %s - %s", file, matchedPatterns.stream().reduce((a, b) -> a + ", " + b).get()));
        }
      });
      successLogs.forEach((file, content) -> {
        List<String> matchedPatterns = patterns.entrySet().stream().filter(entry -> {
          return entry.getKey().matcher(content).find();
        }).map(entry -> entry.getValue()).collect(Collectors.toList());
        if (!matchedPatterns.isEmpty()) {
          System.out.println(String.format("Success log %s matched - %s", file, matchedPatterns.stream().reduce((a, b) -> a + ", " + b).get()));
        }
      });


    }
  }


}
