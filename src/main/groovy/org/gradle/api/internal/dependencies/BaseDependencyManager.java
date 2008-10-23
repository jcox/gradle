/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.DependencyManager;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.ExcludeRuleContainer;
import org.gradle.api.dependencies.GradleArtifact;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.api.dependencies.MavenPomGenerator;
import org.gradle.api.dependencies.UnknownConfigurationException;
import org.gradle.api.dependencies.Configuration;
import org.gradle.util.GUtil;
import org.gradle.util.ConfigureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class BaseDependencyManager extends DefaultDependencyContainer implements DependencyManagerInternal {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager.class);

    /**
     * A map where the key is the name of the configuration and the values are Ivy configuration objects.
     */
    private Map<String, DefaultConfiguration> configurations = new HashMap<String, DefaultConfiguration>();

    /**
     * A map where the key is the name of the configuration and the value are Gradles Artifact objects.
     */
    private Map<String, List<GradleArtifact>> artifacts = new HashMap<String, List<GradleArtifact>>();

    /**
     * A list for passing directly instances of Ivy Artifact objects.
     */
    private Map<String, List<Artifact>> artifactDescriptors = new HashMap<String, List<Artifact>>();

    /**
     * Ivy patterns to tell Ivy where to look for artifacts when publishing the module.
     */
    private List<String> absoluteArtifactPatterns = new ArrayList<String>();

    private Set<File> artifactParentDirs = new HashSet<File>();

    private String defaultArtifactPattern = DependencyManager.DEFAULT_ARTIFACT_PATTERN;

    private ArtifactFactory artifactFactory;

    private IIvyFactory ivyFactory;

    private SettingsConverter settingsConverter;

    private ModuleDescriptorConverter moduleDescriptorConverter;

    private IDependencyResolver dependencyResolver;

    private IDependencyPublisher dependencyPublisher;

    private MavenPomGenerator mavenPomGenerator;

    private LocalReposCacheHandler localReposCacheHandler = new LocalReposCacheHandler();

    private BuildResolverHandler buildResolverHandler = new BuildResolverHandler(localReposCacheHandler);

    private ResolverContainer classpathResolvers = new ResolverContainer(localReposCacheHandler);

    /**
     * The name of the task which produces the artifacts of this project. This is needed by other projects,
     * which have a dependency on a project.
     */
    private String artifactProductionTaskName;

    /**
     * A map where the key is the name of the configuration and the value is the name of a task. This is needed
     * to deal with project dependencies. In case of a project dependency, we need to establish a dependsOn relationship,
     * between a task of the project and the task of the dependsOn project, which builds the artifacts. The default is,
     * that the project task is used, which has the same name as the configuration. If this is not what is wanted,
     * the mapping can be specified via this map.
     */
    private Map<String, Set<String>> confs4Task = new HashMap<String, Set<String>>();
    private Map<String, Set<String>> tasks4Conf = new HashMap<String, Set<String>>();

    /**
     * All the classpath resolvers are contained in an Ivy chain resolver. With this closure you can configure the
     * chain resolver if necessary.
     */
    private Closure chainConfigurer;

    private boolean failForMissingDependencies = true;

    private ExcludeRuleContainer excludeRules;

    public BaseDependencyManager() {

    }

    public BaseDependencyManager(IIvyFactory ivyFactory, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                                 SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                                 IDependencyResolver dependencyResolver, IDependencyPublisher dependencyPublisher,
                                 MavenPomGenerator mavenPomGenerator, File buildResolverDir, ExcludeRuleContainer excludeRuleContainer) {
        super(dependencyFactory, new ArrayList());
        assert buildResolverDir != null;
        this.ivyFactory = ivyFactory;
        this.artifactFactory = artifactFactory;
        this.settingsConverter = settingsConverter;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.dependencyResolver = dependencyResolver;
        this.dependencyPublisher = dependencyPublisher;
        this.mavenPomGenerator = mavenPomGenerator;
        this.localReposCacheHandler.setBuildResolverDir(buildResolverDir);
        this.buildResolverHandler.setBuildResolverDir(buildResolverDir);
        this.excludeRules = excludeRuleContainer;
    }

    public List<File> resolve(String conf, boolean failForMissingDependencies, boolean includeProjectDependencies) {
        return dependencyResolver.resolve(conf, getIvy(), moduleDescriptorConverter.convert(this, false),
                failForMissingDependencies);
    }

    public List<File> resolve(String conf) {
        return dependencyResolver.resolve(conf, getIvy(), moduleDescriptorConverter.convert(this, true),
                this.failForMissingDependencies);
    }

    public List<File> resolveTask(String taskName) {
        Set<String> confs = confs4Task.get(taskName);
        if (!GUtil.isTrue(confs)) {
            throw new InvalidUserDataException("Task $taskName is not mapped to any conf!");
        }
        List<File> paths = new ArrayList<File>();
        for (String conf : confs) {
            paths.addAll(resolve(conf));
        }
        return paths;
    }

    public String antpath(String conf) {
        return GUtil.join(resolve(conf), ":");
    }

    public void publish(List<String> configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor) {
        dependencyPublisher.publish(
                configurations,
                resolvers,
                moduleDescriptorConverter.convert(this, true),
                uploadModuleDescriptor,
                getProject().getBuildDir(),
                this,
                ivy(resolvers.getResolverList()).getPublishEngine()
        );
    }

    public ModuleRevisionId createModuleRevisionId() {
        Object group = DependencyManager.DEFAULT_GROUP;
        Object version = DependencyManager.DEFAULT_VERSION;
        if (getProject().hasProperty("group") && GUtil.isTrue(getProject().property("group"))) {
            group = getProject().property("group");
        }
        if (getProject().hasProperty("version") && GUtil.isTrue(getProject().property("version"))) {
            version = getProject().property("version");
        }
        return new ModuleRevisionId(new ModuleId(group.toString(), getProject().getName()), version.toString());
    }

    Ivy getIvy() {
        return ivy(new ArrayList<DependencyResolver>());
    }

    Ivy ivy(List<DependencyResolver> resolvers) {
        return ivyFactory.createIvy(settingsConverter.convert(classpathResolvers.getResolverList(),
                resolvers,
                new File(getProject().getGradleUserHome()), getBuildResolver(), getClientModuleRegistry(), chainConfigurer));
    }

    public DependencyManager linkConfWithTask(String conf, String task) {
        if (!GUtil.isTrue(conf) || !GUtil.isTrue(task)) {
            throw new InvalidUserDataException("Conf and tasks must be specified!");
        }
        if (tasks4Conf.get(conf) == null) {
            tasks4Conf.put(conf, new HashSet<String>());
        }
        if (confs4Task.get(task) == null) {
            confs4Task.put(task, new HashSet<String>());
        }
        tasks4Conf.get(conf).add(task);
        confs4Task.get(task).add(conf);
        return this;
    }

    public DependencyManager unlinkConfWithTask(String conf, String task) {
        if (!GUtil.isTrue(conf) || !GUtil.isTrue(task)) {
            throw new InvalidUserDataException("Conf and tasks must be specified!");
        }
        if (tasks4Conf.get(conf) == null || !tasks4Conf.get(conf).contains(task)) {
            throw new InvalidUserDataException("Can not unlink Conf= $conf and Task=$task because they are not linked!");
        }
        tasks4Conf.get(conf).remove(task);
        assert confs4Task.get(task) != null;
        confs4Task.get(task).remove(conf);
        return this;
    }

    public void addArtifacts(String configurationName, Object... artifacts) {
        if (this.artifacts.get(configurationName) == null) {
            this.artifacts.put(configurationName, new ArrayList<GradleArtifact>());
        }
        for (Object artifact : GUtil.flatten(Arrays.asList(artifacts))) {
            GradleArtifact gradleArtifact = artifactFactory.createGradleArtifact(artifact.toString());
            logger.debug("Adding {} to configuration={}", gradleArtifact, configurationName);
            this.artifacts.get(configurationName).add(gradleArtifact);
        }
    }

    public Configuration addConfiguration(org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration) {
        return addConfiguration(ivyConfiguration.getName(), null, ivyConfiguration);
    }

    public Configuration addConfiguration(String configuration) {
        return addConfiguration(configuration, null, null);
    }

    public Configuration addConfiguration(String configuration, Closure configureClosure)
            throws InvalidUserDataException {
        return addConfiguration(configuration, configureClosure, null);
    }

    private Configuration addConfiguration(String name, Closure configureClosure,
                                           org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration) {
        if (configurations.containsKey(name)) {
            throw new InvalidUserDataException(String.format("Cannot add configuration '%s' as a configuration with that name already exists.",
                    name));
        }
        DefaultConfiguration configuration = new DefaultConfiguration(name, this, ivyConfiguration);
        configurations.put(name, configuration);
        ConfigureUtil.configure(configureClosure, configuration);
        return configuration;
    }

    public RepositoryResolver getBuildResolver() {
        return buildResolverHandler.getBuildResolver();
    }

    public File getBuildResolverDir() {
        return buildResolverHandler.getBuildResolverDir();
    }

    public FileSystemResolver addFlatDirResolver(String name, Object... dirs) {
        List<File> dirFiles = new ArrayList<File>();
        for (Object dir : dirs) {
            dirFiles.add(new File(dir.toString()));
        }
        FileSystemResolver resolver = classpathResolvers.createFlatDirResolver(name, dirFiles.toArray(new File[dirFiles.size()]));
        return (FileSystemResolver) classpathResolvers.add(resolver);
    }

    public DependencyResolver addMavenRepo(String... jarRepoUrls) {
        return classpathResolvers.add(classpathResolvers.createMavenRepoResolver(DependencyManager.DEFAULT_MAVEN_REPO_NAME,
                DependencyManager.MAVEN_REPO_URL, jarRepoUrls));
    }

    public DependencyResolver addMavenStyleRepo(String name, String root, String... jarRepoUrls) {
        return classpathResolvers.add(classpathResolvers.createMavenRepoResolver(name, root, jarRepoUrls));
    }

    public Map<String, Configuration> getConfigurations() {
        return new HashMap<String, Configuration>(configurations);
    }

    public void setConfigurations(Map<String, DefaultConfiguration> configurations) {
        this.configurations = configurations;
    }

    public boolean isFailForMissingDependencies() {
        return failForMissingDependencies;
    }

    public void setFailForMissingDependencies(boolean failForMissingDependencies) {
        this.failForMissingDependencies = failForMissingDependencies;
    }

    public Map<String, List<GradleArtifact>> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Map<String, List<GradleArtifact>> artifacts) {
        this.artifacts = artifacts;
    }

    public Map<String, List<Artifact>> getArtifactDescriptors() {
        return artifactDescriptors;
    }

    public void setArtifactDescriptors(Map<String, List<Artifact>> artifactDescriptors) {
        this.artifactDescriptors = artifactDescriptors;
    }

    public List<String> getAbsoluteArtifactPatterns() {
        return absoluteArtifactPatterns;
    }

    public void setAbsoluteArtifactPatterns(List<String> absoluteArtifactPatterns) {
        this.absoluteArtifactPatterns = absoluteArtifactPatterns;
    }

    public Set<File> getArtifactParentDirs() {
        return artifactParentDirs;
    }

    public void setArtifactParentDirs(Set<File> artifactParentDirs) {
        this.artifactParentDirs = artifactParentDirs;
    }

    public String getDefaultArtifactPattern() {
        return defaultArtifactPattern;
    }

    public void setDefaultArtifactPattern(String defaultArtifactPattern) {
        this.defaultArtifactPattern = defaultArtifactPattern;
    }

    public ArtifactFactory getArtifactFactory() {
        return artifactFactory;
    }

    public void setArtifactFactory(ArtifactFactory artifactFactory) {
        this.artifactFactory = artifactFactory;
    }

    public IIvyFactory getIvyFactory() {
        return ivyFactory;
    }

    public void setIvyFactory(IIvyFactory ivyFactory) {
        this.ivyFactory = ivyFactory;
    }

    public SettingsConverter getSettingsConverter() {
        return settingsConverter;
    }

    public void setSettingsConverter(SettingsConverter settingsConverter) {
        this.settingsConverter = settingsConverter;
    }

    public ModuleDescriptorConverter getModuleDescriptorConverter() {
        return moduleDescriptorConverter;
    }

    public void setModuleDescriptorConverter(ModuleDescriptorConverter moduleDescriptorConverter) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
    }

    public IDependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    public void setDependencyResolver(IDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public IDependencyPublisher getDependencyPublisher() {
        return dependencyPublisher;
    }

    public void setDependencyPublisher(IDependencyPublisher dependencyPublisher) {
        this.dependencyPublisher = dependencyPublisher;
    }

    public LocalReposCacheHandler getLocalReposCacheHandler() {
        return localReposCacheHandler;
    }

    public void setLocalReposCacheHandler(LocalReposCacheHandler localReposCacheHandler) {
        this.localReposCacheHandler = localReposCacheHandler;
    }

    public BuildResolverHandler getBuildResolverHandler() {
        return buildResolverHandler;
    }

    public void setBuildResolverHandler(BuildResolverHandler buildResolverHandler) {
        this.buildResolverHandler = buildResolverHandler;
    }

    public ResolverContainer getClasspathResolvers() {
        return classpathResolvers;
    }

    public void setClasspathResolvers(ResolverContainer classpathResolvers) {
        this.classpathResolvers = classpathResolvers;
    }

    public String getArtifactProductionTaskName() {
        return artifactProductionTaskName;
    }

    public void setArtifactProductionTaskName(String artifactProductionTaskName) {
        this.artifactProductionTaskName = artifactProductionTaskName;
    }

    public Map<String, Set<String>> getConfs4Task() {
        return confs4Task;
    }

    public void setConfs4Task(Map<String, Set<String>> confs4Task) {
        this.confs4Task = confs4Task;
    }

    public Map<String, Set<String>> getTasks4Conf() {
        return tasks4Conf;
    }

    public void setTasks4Conf(Map<String, Set<String>> tasks4Conf) {
        this.tasks4Conf = tasks4Conf;
    }

    public Closure getChainConfigurer() {
        return chainConfigurer;
    }

    public void setChainConfigurer(Closure chainConfigurer) {
        this.chainConfigurer = chainConfigurer;
    }

    public ExcludeRuleContainer getExcludeRules() {
        return excludeRules;
    }

    public MavenPomGenerator getMaven() {
        return mavenPomGenerator;
    }

    public void setExcludeRules(ExcludeRuleContainer excludeRules) {
        this.excludeRules = excludeRules;
    }

    public Configuration findConfiguration(String name) {
        return configurations.get(name);
    }

    public Configuration configuration(String name) throws UnknownConfigurationException {
        Configuration configuration = findConfiguration(name);
        if (configuration == null) {
            throw new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
        }
        return configuration;
    }

    public Configuration configuration(String name, Closure configureClosure) throws UnknownConfigurationException {
        Configuration configuration = configuration(name);
        ConfigureUtil.configure(configureClosure, configuration);
        return configuration;
    }
}