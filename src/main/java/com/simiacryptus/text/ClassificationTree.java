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

import com.simiacryptus.ref.lang.RefUtil;
import com.simiacryptus.ref.wrappers.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ClassificationTree {

  private final double minLeafWeight = 10;
  private final int maxLevels = 8;
  private final int minWeight = 5;
  private final double depthBias = 0.0005;
  private final int smoothing = 3;
  @Nullable
  private PrintStream verbose = null;

  @Nullable
  public PrintStream getVerbose() {
    return verbose;
  }

  @Nonnull
  public ClassificationTree setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }

  @Nonnull
  public Function<CharSequence, RefMap<CharSequence, Double>> categorizationTree(
      @Nonnull RefMap<CharSequence, RefList<CharSequence>> categories, int depth) {
    return categorizationTree(categories, depth, "");
  }

  @Nonnull
  private Function<CharSequence, RefMap<CharSequence, Double>> categorizationTree(
      @Nonnull RefMap<CharSequence, RefList<CharSequence>> categories, int depth, CharSequence indent) {
    if (0 == depth) {
      return str -> {
        int sum = categories.values().stream().mapToInt(x -> x.size()).sum();
        return categories.entrySet().stream()
            .collect(RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size() * 1.0 / sum));
      };
    } else {
      if (1 >= categories.values().stream().filter(x -> !x.isEmpty()).count()) {
        return categorizationTree(categories, 0, indent);
      }
      Optional<NodeInfo> info = categorizationSubstring(categories.values());
      if (!info.isPresent())
        return categorizationTree(categories, 0, indent);
      CharSequence split = RefUtil.get(info).node.getString();
      RefMap<CharSequence, RefList<CharSequence>> lSet = categories.entrySet().stream().collect(RefCollectors.toMap(
          e -> e.getKey(),
          e -> e.getValue().stream().filter(str -> str.toString().contains(split)).collect(RefCollectors.toList())));
      RefMap<CharSequence, RefList<CharSequence>> rSet = categories.entrySet().stream().collect(RefCollectors.toMap(
          e -> e.getKey(),
          e -> e.getValue().stream().filter(str -> !str.toString().contains(split)).collect(RefCollectors.toList())));
      int lSum = lSet.values().stream().mapToInt(x -> x.size()).sum();
      int rSum = rSet.values().stream().mapToInt(x -> x.size()).sum();
      if (0 == lSum || 0 == rSum) {
        return categorizationTree(categories, 0, indent);
      }
      if (null != verbose) {
        verbose.println(RefString.format(indent + "\"%s\" -> Contains=%s\tAbsent=%s\tEntropy=%5f", split,
            lSet.entrySet().stream().collect(RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size())),
            rSet.entrySet().stream().collect(RefCollectors.toMap(e -> e.getKey(), e -> e.getValue().size())),
            RefUtil.get(info).entropy));
      }
      Function<CharSequence, RefMap<CharSequence, Double>> l = categorizationTree(lSet, depth - 1, indent + "  ");
      Function<CharSequence, RefMap<CharSequence, Double>> r = categorizationTree(rSet, depth - 1, indent + "  ");
      return str -> {
        if (str.toString().contains(split)) {
          return l.apply(str);
        } else {
          return r.apply(str);
        }
      };
    }
  }

  private double entropy(@Nonnull RefMap<Integer, Long> sum, @Nonnull RefMap<Integer, Long> left) {
    double sumSum = sum.values().stream().mapToDouble(x -> x).sum();
    double leftSum = left.values().stream().mapToDouble(x -> x).sum();
    double rightSum = sumSum - leftSum;
    //com.simiacryptus.ref.wrappers.RefSystem.err.println(String.format("%s & %s", sum, left));
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

  @Nonnull
  private Optional<NodeInfo> categorizationSubstring(@Nonnull RefCollection<RefList<CharSequence>> categories) {
    CharTrieIndex trie = new CharTrieIndex();
    RefMap<Integer, Integer> categoryMap = new RefTreeMap<>();
    int categoryNumber = 0;
    RefMap<Integer, Long> sum = new RefHashMap<>();
    for (RefList<CharSequence> category : categories) {
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

  @Nonnull
  private NodeInfo info(@Nonnull IndexNode node, @Nonnull RefMap<Integer, Long> sum, @Nonnull RefMap<Integer, Integer> categoryMap) {
    RefMap<Integer, Long> summary = summarize(node, categoryMap);
    return new NodeInfo(node, summary, entropy(sum, summary));
  }

  private RefMap<Integer, Long> summarize(@Nonnull IndexNode node, @Nonnull RefMap<Integer, Integer> categoryMap) {
    return node.getCursors().map(x -> x.getDocumentId()).distinct().map(x -> categoryMap.get(x))
        .collect(RefCollectors.toList()).stream().collect(RefCollectors.groupingBy(x -> x, RefCollectors.counting()));
  }

  @Nonnull
  private Optional<NodeInfo> categorizationSubstring(@Nonnull IndexNode node, @Nonnull RefMap<Integer, Integer> categoryMap,
                                                     @Nonnull RefMap<Integer, Long> sum) {
    RefList<NodeInfo> childrenInfo = node.getChildren().map(n -> categorizationSubstring(n, categoryMap, sum))
        .filter(x -> x.isPresent()).map(RefUtil::get).collect(RefCollectors.toList());
    NodeInfo info = info(node, sum, categoryMap);
    if (info.node.getString().isEmpty() || !Double.isFinite(info.entropy))
      info = null;
    Optional<NodeInfo> max = RefStream
        .concat(null == info ? RefStream.empty() : RefStream.of(info), childrenInfo.stream())
        .max(RefComparator.comparing(x -> x.entropy));
    return max;
  }

  private class NodeInfo {
    IndexNode node;
    Map<Integer, Long> categoryWeights;
    double entropy;

    public NodeInfo(@Nonnull IndexNode node, Map<Integer, Long> categoryWeights, double entropy) {
      this.node = node;
      this.categoryWeights = categoryWeights;
      this.entropy = entropy + depthBias * node.getDepth();
    }
  }

}
