/*
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

import org.objectweb.asm.ClassReader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The model of the entire project
 */
class ProjectModel {

  private final List<JarModel> jars = new ArrayList<>(1000);
  private final Map<String, ClassModel> classModels = new HashMap<>(30000);

  ProjectModel(String tgfFilename) throws IOException {
    InputStream fis = new FileInputStream(tgfFilename);
    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
    BufferedReader br = new BufferedReader(isr);
    String line;
    Map<Long, JarModel> jarsById = new HashMap<>(1000);
    // read the nodes
    while ((line = br.readLine()) != null) {
      if ("#".equals(line)) {
        break;
      }
      String[] words = line.split(" ", 2);
      long id = Long.parseLong(words[0]);
      String[] parts = words[1].split(":", 5);
      JarModel info = new JarModel(id, parts[0], parts[1], parts[2], parts[3],
          parts.length < 5 ? "root" : parts[4]);
      jars.add(info);
      jarsById.put(id, info);
    }
    // read the edges
    while ((line = br.readLine()) != null) {
      String[] words = line.split(" ");
      long parent = Long.parseLong(words[0]);
      long child = Long.parseLong(words[1]);
      jarsById.get(parent).children.add(jarsById.get(child));
    }
    br.close();
    // read the jar files and build the dependency graph
    readJars();
    // set the classes in the root jar as depth 0
    for(ClassModel root: jars.get(0).classes) {
      setDepthFromRoot(root, 0);
    }
    buildTransitiveDepencencies();
  }

  static String getClassnameFromPath(String path) {
    if (path.endsWith(".class")) {
      path = path.substring(0, path.length() - ".class".length());
    }
    return path.replace("/", ".");
  }

  boolean isSystem(String name) {
    return name.startsWith("java.") || name.startsWith("javax.");
  }

  void readJars() throws IOException {
    DependencyVisitor classGraph = new DependencyVisitor();
    for(JarModel jar: jars) {
      ZipFile f = new ZipFile(jar.getJarLocation());
      Enumeration<? extends ZipEntry> en = f.entries();
      while (en.hasMoreElements()) {
        ZipEntry e = en.nextElement();
        String path = e.getName();
        if (path.endsWith(".class")) {
          String name = getClassnameFromPath(path);
          if (classModels.containsKey(name)) {
            ClassModel model = classModels.get(name);
            jar.classes.add(model);
            model.containingJars += 1;
            System.err.println("Duplicate class " + name + " found in " +
                jar.toString() + " and " + classModels.get(name).jar.toString());
          } else {
            ClassModel model = new ClassModel(jar, name, classModels.size());
            classModels.put(name, model);
            jar.classes.add(model);
            new ClassReader(f.getInputStream(e)).accept(classGraph, 0);
          }
        }
      }
      f.close();
    }
    System.out.println("Finished visiting " + jars.size() + " jars, with " +
        classModels.size() + " classes.");
    fillInDependencies(classGraph);
  }

  /**
   * Fills in the ClassModels with the next/prev links.
   * @param deps The mapping of which classes each class depends on.
   */
  void fillInDependencies(DependencyVisitor deps) {
    for(ClassModel parent: classModels.values()) {
      for(String childName: deps.getDependencies(parent.name)) {
        childName = getClassnameFromPath(childName);
        if (!isSystem(childName)) {
          ClassModel child = classModels.get(childName);
          if (child == null) {
            System.err.println("Can't find model for " + childName);
          } else {
            parent.next.add(child);
            child.prev.add(parent);
          }
        }
      }
    }
  }

  void setDepthFromRoot(ClassModel node, int depth) {
    if (depth < node.depth) {
      node.depth = depth;
      for(ClassModel next: node.next) {
        setDepthFromRoot(next, depth + 1);
      }
    }
  }

  void buildTransitiveDepencencies() {
    // build the complete list of classes and
    // populate the direct depends set
    ClassModel[] universe = new ClassModel[classModels.size()];
    for (ClassModel cls : classModels.values()) {
      universe[cls.id] = cls;
      cls.depends = new BitSet(universe.length);
      for (ClassModel child : cls.next) {
        cls.depends.set(child.id);
      }
      cls.dependsCount = cls.depends.cardinality();
    }

    // iterate until we get to stability of the transitive depends sets
    BitSet recheck = new BitSet(universe.length);
    recheck.set(0, universe.length);
    int next = recheck.nextSetBit(0);
    while (next != -1) {
      recheck.clear(next);
      for (ClassModel prev : universe[next].prev) {
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
  }

  public List<JarModel> getJars() {
    return jars;
  }
}
