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

import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public @com.simiacryptus.ref.lang.RefAware
class ClassificationTree {

  private final double minLeafWeight = 10;
  private final int maxLevels = 8;
  private final int minWeight = 5;
  private final double depthBias = 0.0005;
  private final int smoothing = 3;
  private PrintStream verbose = null;

  public PrintStream getVerbose() {
    return verbose;
  }

  public ClassificationTree setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }

  public Function<CharSequence, com.simiacryptus.ref.wrappers.RefMap<CharSequence, Double>> categorizationTree(
      com.simiacryptus.ref.wrappers.RefMap<CharSequence, com.simiacryptus.ref.wrappers.RefList<CharSequence>> categories,
      int depth) {
    return categorizationTree(categories, depth, "");
  }

  private Function<CharSequence, com.simiacryptus.ref.wrappers.RefMap<CharSequence, Double>> categorizationTree(
      com.simiacryptus.ref.wrappers.RefMap<CharSequence, com.simiacryptus.ref.wrappers.RefList<CharSequence>> categories,
      int depth, CharSequence indent) {
    if (0 == depth) {
      return str -> {
        int sum = categories.values().stream().mapToInt(x -> x.size()).sum();
        return categories.entrySet().stream().collect(
            com.simiacryptus.ref.wrappers.RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size() * 1.0 / sum));
      };
    } else {
      if (1 >= categories.values().stream().filter(x -> !x.isEmpty()).count()) {
        return categorizationTree(categories, 0, indent);
      }
      Optional<NodeInfo> info = categorizationSubstring(categories.values());
      if (!info.isPresent())
        return categorizationTree(categories, 0, indent);
      CharSequence split = info.get().node.getString();
      com.simiacryptus.ref.wrappers.RefMap<CharSequence, com.simiacryptus.ref.wrappers.RefList<CharSequence>> lSet = categories
          .entrySet().stream()
          .collect(com.simiacryptus.ref.wrappers.RefCollectors.toMap(e -> e.getKey(),
              e -> e.getValue().stream().filter(str -> str.toString().contains(split))
                  .collect(com.simiacryptus.ref.wrappers.RefCollectors.toList())));
      com.simiacryptus.ref.wrappers.RefMap<CharSequence, com.simiacryptus.ref.wrappers.RefList<CharSequence>> rSet = categories
          .entrySet().stream()
          .collect(com.simiacryptus.ref.wrappers.RefCollectors.toMap(e -> e.getKey(),
              e -> e.getValue().stream().filter(str -> !str.toString().contains(split))
                  .collect(com.simiacryptus.ref.wrappers.RefCollectors.toList())));
      int lSum = lSet.values().stream().mapToInt(x -> x.size()).sum();
      int rSum = rSet.values().stream().mapToInt(x -> x.size()).sum();
      if (0 == lSum || 0 == rSum) {
        return categorizationTree(categories, 0, indent);
      }
      if (null != verbose) {
        verbose.println(String.format(indent + "\"%s\" -> Contains=%s\tAbsent=%s\tEntropy=%5f", split,
            lSet.entrySet().stream()
                .collect(com.simiacryptus.ref.wrappers.RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size())),
            rSet.entrySet().stream()
                .collect(com.simiacryptus.ref.wrappers.RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size())),
            info.get().entropy));
      }
      Function<CharSequence, com.simiacryptus.ref.wrappers.RefMap<CharSequence, Double>> l = categorizationTree(lSet,
          depth - 1, indent + "  ");
      Function<CharSequence, com.simiacryptus.ref.wrappers.RefMap<CharSequence, Double>> r = categorizationTree(rSet,
          depth - 1, indent + "  ");
      return str -> {
        if (str.toString().contains(split)) {
          return l.apply(str);
        } else {
          return r.apply(str);
        }
      };
    }
  }

  private double entropy(com.simiacryptus.ref.wrappers.RefMap<Integer, Long> sum,
                         com.simiacryptus.ref.wrappers.RefMap<Integer, Long> left) {
    double sumSum = sum.values().stream().mapToDouble(x -> x).sum();
    double leftSum = left.values().stream().mapToDouble(x -> x).sum();
    double rightSum = sumSum - leftSum;
    //System.err.println(String.format("%s & %s", sum, left));
    if (rightSum < minLeafWeight)
      return Double.NEGATIVE_INFINITY;
    if (leftSum < minLeafWeight)
      return Double.NEGATIVE_INFINITY;
    return (sum.keySet().stream().mapToDouble(category -> {
      Long leftCnt = left.getOrDefault(category, 0l);
      return leftCnt * Math.log((leftCnt + smoothing) * 1.0 / (leftSum + smoothing * sum.size()));
    }).sum() + sum.keySet().stream().mapToDouble(category -> {
      Long rightCnt = sum.getOrDefault(category, 0l) - left.getOrDefault(category, 0l);
      return rightCnt * Math.log((rightCnt + smoothing) * 1.0 / (rightSum + smoothing * sum.size()));
    }).sum()) / (sumSum * Math.log(2));
  }

  private Optional<NodeInfo> categorizationSubstring(
      com.simiacryptus.ref.wrappers.RefCollection<com.simiacryptus.ref.wrappers.RefList<CharSequence>> categories) {
    CharTrieIndex trie = new CharTrieIndex();
    com.simiacryptus.ref.wrappers.RefMap<Integer, Integer> categoryMap = new com.simiacryptus.ref.wrappers.RefTreeMap<>();
    int categoryNumber = 0;
    com.simiacryptus.ref.wrappers.RefMap<Integer, Long> sum = new com.simiacryptus.ref.wrappers.RefHashMap<>();
    for (com.simiacryptus.ref.wrappers.RefList<CharSequence> category : categories) {
      categoryNumber += 1;
      for (CharSequence text : category) {
        sum.put(categoryNumber, sum.getOrDefault(categoryNumber, 0l) + text.length() + 1);
        categoryMap.put(trie.addDocument(text), categoryNumber);
      }
    }
    trie.index(maxLevels, minWeight);
    sum = summarize(trie.root(), categoryMap);
    return categorizationSubstring(trie.root(), categoryMap, sum);
  }

  private NodeInfo info(IndexNode node, com.simiacryptus.ref.wrappers.RefMap<Integer, Long> sum,
                        com.simiacryptus.ref.wrappers.RefMap<Integer, Integer> categoryMap) {
    com.simiacryptus.ref.wrappers.RefMap<Integer, Long> summary = summarize(node, categoryMap);
    return new NodeInfo(node, summary, entropy(sum, summary));
  }

  private com.simiacryptus.ref.wrappers.RefMap<Integer, Long> summarize(IndexNode node,
                                                                        com.simiacryptus.ref.wrappers.RefMap<Integer, Integer> categoryMap) {
    return node.getCursors().map(x -> x.getDocumentId()).distinct().map(x -> categoryMap.get(x))
        .collect(com.simiacryptus.ref.wrappers.RefCollectors.toList()).stream()
        .collect(com.simiacryptus.ref.wrappers.RefCollectors.groupingBy(x -> x,
            com.simiacryptus.ref.wrappers.RefCollectors.counting()));
  }

  private Optional<NodeInfo> categorizationSubstring(IndexNode node,
                                                     com.simiacryptus.ref.wrappers.RefMap<Integer, Integer> categoryMap,
                                                     com.simiacryptus.ref.wrappers.RefMap<Integer, Long> sum) {
    com.simiacryptus.ref.wrappers.RefList<NodeInfo> childrenInfo = node.getChildren()
        .map(n -> categorizationSubstring(n, categoryMap, sum)).filter(x -> x.isPresent()).map(x -> x.get())
        .collect(com.simiacryptus.ref.wrappers.RefCollectors.toList());
    NodeInfo info = info(node, sum, categoryMap);
    if (info.node.getString().isEmpty() || !Double.isFinite(info.entropy))
      info = null;
    Optional<NodeInfo> max = com.simiacryptus.ref.wrappers.RefStream
        .concat(null == info ? com.simiacryptus.ref.wrappers.RefStream.empty()
            : com.simiacryptus.ref.wrappers.RefStream.of(info), childrenInfo.stream())
        .max(com.simiacryptus.ref.wrappers.RefComparator.comparing(x -> x.entropy));
    return max;
  }

  private @com.simiacryptus.ref.lang.RefAware
  class NodeInfo {
    IndexNode node;
    Map<Integer, Long> categoryWeights;
    double entropy;

    public NodeInfo(IndexNode node, Map<Integer, Long> categoryWeights,
                    double entropy) {
      this.node = node;
      this.categoryWeights = categoryWeights;
      this.entropy = entropy + depthBias * node.getDepth();
    }
  }

}
