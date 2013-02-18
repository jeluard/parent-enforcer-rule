/**
 *
 *                                Parent enforcer rule
 *                             =========================
 *
 * Please visit the Remote Process web site for more information:
 *
 *   * http://jeluard.github.com/remote-process
 *
 * Copyright 2012 Julien Eluard
 *
 * Julien Eluard licenses this product to you under the Apache License, version 2.0 (the
 * "License"); you may not use this product except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Also, please refer to each LICENSE.<component>.txt file, which is located in
 * the 'licenses' directory of the distribution file, for the license terms of the
 * components that this product depends on.
 */
package com.github.jeluard.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * {@link EnforcerRule} implementation checking parent artifacts.
 */
public class ParentEnforcerRule implements EnforcerRule {

  private static final String POM_ARTIFACT_TYPE = "pom";

  @Override
  public void execute(final EnforcerRuleHelper helper) throws EnforcerRuleException {
    final MavenProject project;
    try {
      project = (MavenProject) helper.evaluate("${project}");
    } catch (ExpressionEvaluationException e) {
      throw new EnforcerRuleException("Failed to access ${project} variable", e);
    }

    final String type = project.getArtifact().getType();
    if (!ParentEnforcerRule.POM_ARTIFACT_TYPE.equals(type)) {
      helper.getLog().debug("Skipping non "+ParentEnforcerRule.POM_ARTIFACT_TYPE+" artifact.");

      return;
    }

    final Parent expectedParent = new Parent();
    expectedParent.setGroupId(project.getGroupId());
    expectedParent.setArtifactId(project.getArtifactId());
    expectedParent.setVersion(project.getVersion());
    try {
      validateSubModules(extractRootFolder(project), project.getModel(), expectedParent);
    } catch (IOException e) {
      e.printStackTrace();
      throw new EnforcerRuleException("Failed to process one of project's module", e);
    }
  }

  protected final File extractRootFolder(final MavenProject project) {
    return new File(project.getFile().getParentFile().getPath());
  }

  protected final void validateModel(final File rootFolder, final Model model, final Parent expectedParent) throws IOException {
    if (!isParentValid(model.getParent(), expectedParent)) {
        throw new IllegalArgumentException("Parent for <"+model+"> is <"+model.getParent()+"> but must be <"+expectedParent+">");
    }

    validateSubModules(rootFolder, model, expectedParent);
  }

  protected final File parentFolder(final File rootFolder, final Parent parent) throws IOException {
    final String relativePath = parent.getRelativePath();
    if (relativePath != null) {
      return new File(rootFolder, relativePath.substring(0, relativePath.length()-7));
    }
    return new File(rootFolder, "..");
  }

  protected final String getGroupId(final File rootFolder, final Model model) throws IOException {
    final String groupId = model.getGroupId();
    if (groupId != null) {
      return groupId;
    }

    File folder = parentFolder(rootFolder, model.getParent());
    Model parentModel = loadModel(folder);
    while (parentModel != null) {
      final String parentGroupId = parentModel.getGroupId();
      if (parentGroupId != null) {
        return parentGroupId;
      }

      parentModel = loadModel(folder);
      folder = parentFolder(folder, parentModel.getParent());
    }
    throw new IllegalStateException("Failed to access groupId for <"+model+">");
  }

  protected final String getVersion(final File rootFolder, final Model model) throws IOException {
    final String version = model.getVersion();
    if (version != null) {
      return version;
    }

    File folder = parentFolder(rootFolder, model.getParent());
    Model parentModel = loadModel(folder);
    while (parentModel != null) {
      final String parentVersion = parentModel.getVersion();
      if (parentVersion != null) {
        return parentVersion;
      }

      parentModel = loadModel(folder);
      folder = parentFolder(folder, parentModel.getParent());
    }
    throw new IllegalStateException("Failed to access version for <"+model+">");
  }

  protected final void validateSubModules(final File rootFolder, final Model model, final Parent expectedParent) throws IOException {
    //Validate all modules of pom type modules.
    if (ParentEnforcerRule.POM_ARTIFACT_TYPE.equals(model.getPackaging())) {
      final Parent newExpectedParent = new Parent();
      newExpectedParent.setGroupId(getGroupId(rootFolder, model));
      newExpectedParent.setArtifactId(model.getArtifactId());
      newExpectedParent.setVersion(getVersion(rootFolder, model));//Model version might be inherited from Parent (thus not set at the model level). Rely on Parent#getVersion().
      System.out.println("Parent module: "+newExpectedParent);
      for (final String module : model.getModules()) {
        final Model moduleModel = loadModel(rootFolder, module+"/pom.xml");
        System.out.println("  Validating against module: "+moduleModel);
        validateModel(new File(rootFolder, module), moduleModel, newExpectedParent);
      }
    }
  }

  protected final boolean isParentValid(final Parent modelParent, final Parent expectedParent) {
    return expectedParent.getArtifactId().equals(modelParent.getArtifactId())
            /*&& parent.getGroupId() != null */&& expectedParent.getGroupId().equals(modelParent.getGroupId())
            && expectedParent.getVersion().equals(modelParent.getVersion());
  }

  protected final Model loadModel(final File rootFolder) throws IOException {
    return loadModel(rootFolder, "pom.xml");
  }

  protected final Model loadModel(final File rootFolder, final String moduleFileName) throws IOException {
    FileReader fileReader = null;
    BufferedReader bufferedReader = null;
    try
    {
      System.out.println("loading: "+new File(rootFolder, moduleFileName));
      fileReader = new FileReader(new File(rootFolder, moduleFileName));
      bufferedReader = new BufferedReader(fileReader);
      final MavenXpp3Reader reader = new MavenXpp3Reader();
      return reader.read(bufferedReader);
    } catch (XmlPullParserException e) {
        final IOException ioe = new IOException(e);
        throw ioe;
    } finally {
      if (bufferedReader != null) {
        bufferedReader.close();
      }
      if (fileReader != null) {
        fileReader.close();
      }
    }
  }

  @Override
  public boolean isCacheable() {
    return false;
  }

  @Override
  public boolean isResultValid(final EnforcerRule cachedRule) {
    return false;
  }

  @Override
  public String getCacheId() {
    return "0";
  }

}
