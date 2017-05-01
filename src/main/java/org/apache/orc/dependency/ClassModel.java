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
import java.util.BitSet;
import java.util.List;

/**
 * Created by owen on 2/27/17.
 */
class ClassModel {
  final String name;
  final int id;
  final JarModel jar;
  int containingJars = 1;
  int depth = Integer.MAX_VALUE;
  BitSet depends = null;
  int dependsCount = 0;
  List<ClassModel> prev = new ArrayList<ClassModel>();
  List<ClassModel> next = new ArrayList<ClassModel>();

  ClassModel(JarModel jar, String name, int id) {
    this.jar = jar;
    this.name = name;
    this.id = id;
  }

  public String toString() {
    return name + " (" + dependsCount + ", " + depth + ")";
  }
}
