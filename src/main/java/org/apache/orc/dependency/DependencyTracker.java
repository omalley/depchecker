/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.orc.dependency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;

/**
 * DependencyTracker
 */
public class DependencyTracker {

  static boolean isRoot(String name) {
    return name.startsWith("org.apache.hadoop.hive.metastore.");
  }

  static boolean isSystem(String name) {
    return !(name.startsWith("org.apache.hadoop.hive") ||
        name.startsWith("org.apache.hive"));
  }

  static class ClassInfo {
    String name;
    int id;
    int depth = Integer.MAX_VALUE;
    BitSet depends = null;
    int dependsCount = 0;
    List<ClassInfo> prev = new ArrayList<ClassInfo>();
    List<ClassInfo> next = new ArrayList<ClassInfo>();

    ClassInfo(String name, int id) {
      this.name = name;
      this.id = id;
    }

    public String toString() {
      return name + " (" + dependsCount + ", " + depth + ")";
    }
  }

  static class ClassInfoComparator implements Comparator<ClassInfo> {
    public int compare(ClassInfo left, ClassInfo right) {
      if (left.depth < right.depth) {
        return -1;
      } else if (left.depth > right.depth) {
        return 1;
      } else if (left.dependsCount > right.dependsCount) {
        return -1;
      } else if (left.dependsCount < right.dependsCount) {
        return 1;
      } else {
        return left.name.compareTo(right.name);
      }
    }

    public boolean equals(Object obj) {
      return getClass() == obj.getClass();
    }
  }

  static Map<String, ClassInfo> info = new HashMap<String, ClassInfo>();

  static ClassInfo getClassInfo(String name) {
    ClassInfo result = info.get(name);
    if (result == null) {
      result = new ClassInfo(name.replace('/', '.'), info.size());
      info.put(name, result);
    }
    return result;
  }

  static void recursivelySetDepth(ClassInfo self,
                                  int depth) {
    if (self.depth > depth) {
      self.depth = depth;
      for(ClassInfo child: self.next) {
        recursivelySetDepth(child, depth + 1);
      }
    }
  }

  static void expandRecursively(DependencyVisitor v,
                                ClassInfo parent,
                                String name) {
    for(String dep: v.getDependencies(name)) {
      if (!isSystem(dep)) {
        boolean visited = info.containsKey(dep);
        ClassInfo child = getClassInfo(dep);
        child.prev.add(parent);
        parent.next.add(child);
        if (!visited) {
          expandRecursively(v, child, dep);
        }
      }
    }
  }

  public static void main(final String[] args) throws IOException {
    DependencyVisitor v = new DependencyVisitor();

    ZipFile f = new ZipFile(args[0]);
    Enumeration<? extends ZipEntry> en = f.entries();
    while (en.hasMoreElements()) {
      ZipEntry e = en.nextElement();
      String name = e.getName();
      if (name.endsWith(".class")) {
        new ClassReader(f.getInputStream(e)).accept(v, 0);
      }
    }
    System.out.println("Finished visiting " + v.getClasses().size() +
                       " classes.");

    List<ClassInfo> roots = new ArrayList<ClassInfo>();
    for(String cls: v.getClasses()) {
      if (isRoot(cls)) {
        boolean visited = info.containsKey(cls);
        ClassInfo myInfo = getClassInfo(cls);
        roots.add(myInfo);
        if (!visited) {
          expandRecursively(v, myInfo, cls);
        }
        recursivelySetDepth(myInfo, 0);
      }
    }
    v = null;

    int classCount = info.size();
    System.out.println("Restricted to " + classCount +
                       " classes reachable from " + roots.size() + " roots.");

    // build the complete list of classes and
    // populate the direct depends set
    ClassInfo[] universe = new ClassInfo[classCount];
    for(ClassInfo cls: info.values()) {
      universe[cls.id] = cls;
      BitSet depends = new BitSet(classCount);
      cls.depends = depends;
      for(ClassInfo child: cls.next) {
        depends.set(child.id);
      }
      cls.dependsCount = depends.cardinality();
    }

    // iterate until we get to stability of the transitive depends sets
    BitSet recheck = new BitSet(classCount);
    recheck.set(0, classCount);
    int next = recheck.nextSetBit(0);
    while (next != -1) {
      recheck.clear(next);
      for(ClassInfo prev: universe[next].prev) {
        int oldCount = prev.depends.cardinality();
        prev.depends.or(universe[next].depends);
        prev.depends.set(next);
        prev.dependsCount = prev.depends.cardinality();

        // if we added new values, then recheck it
        if (oldCount != prev.dependsCount) {
          recheck.set(prev.id);
        }
      }
      next = recheck.nextSetBit(0);
    }

    // sort the universe into the weight order
    ClassInfoComparator compare = new ClassInfoComparator();
    Arrays.sort(universe, compare);

    int currentDepth = -1;
    for(ClassInfo cls: universe) {
      if (cls.depth != currentDepth) {
        currentDepth = cls.depth;
        System.out.println();
        System.out.println("Depth: " + currentDepth);
      }
      System.out.println();
      System.out.println("  Class " + cls);
      System.out.println("    Forward:");
      ClassInfo[] sorted = cls.next.toArray(new ClassInfo[cls.next.size()]);
      Arrays.sort(sorted, compare);
      for(ClassInfo child: sorted) {
        System.out.println("      " + child);
      }
      System.out.println("    Backward:");
      sorted = cls.prev.toArray(new ClassInfo[cls.prev.size()]);
      Arrays.sort(sorted, compare);
      for(ClassInfo dep: sorted) {
        System.out.println("      " + dep);
      }
    }
  }

}
