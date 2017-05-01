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

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DependencyTracker
 */
public class DependencyVacuum {

  public static void main(final String[] args) throws IOException {
    ProjectModel model = new ProjectModel(args[0]);
    for(JarModel jar: model.getJars()) {
      int unusedClasses = 0;
      int usedSingle = 0;
      int usedDuplicated = 0;
      for(ClassModel cls: jar.classes) {
        if (cls.depth == Integer.MAX_VALUE) {
          unusedClasses += 1;
        } else if (cls.containingJars == 1) {
          usedSingle += 1;
        } else {
          usedDuplicated += 1;
        }
      }
      if (usedSingle == 0 && usedDuplicated == 0) {
        System.out.println(jar + " used: " + usedSingle + ", used duplicate: " +
            usedDuplicated + ", unused: " + unusedClasses);
      }
    }
  }

}
