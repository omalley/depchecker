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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Information about a jar file.
 */
class JarModel {
  final long id;
  final String groupId;
  final String artifactId;
  final String classifier;
  final String version;
  final String scope;
  final List<JarModel> children = new ArrayList<>();
  final List<ClassModel> classes = new ArrayList<>();

  JarModel(long id, String groupId, String artifactId, String classifier,
           String version, String scope) {
    this.id = id;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.classifier = classifier;
    this.version = version;
    this.scope = scope;
  }

  @Override
  public String toString() {
    return groupId + ":" + artifactId + ":" + classifier + ":" + version
        + ":" + scope;
  }

  @Override
  public int hashCode() {
    return (int) id;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other.getClass() != getClass()) {
      return false;
    }
    return id == ((JarModel) other).id;
  }

  private final static String M2_REPOSITORY;
  static {
    String HOME = System.getenv("HOME");
    M2_REPOSITORY = HOME + "/.m2/repository/";
  }

  String getClassifierName() {
    if ("jar".equals(classifier)) {
      return "";
    } else {
      return "-" + classifier;
    }
  }

  public String getJarLocation() {
    return M2_REPOSITORY + groupId.replace(".", "/") + "/" + artifactId + "/" +
        version + "/" + artifactId + "-" + version + getClassifierName() +
        ".jar";
  }


}
