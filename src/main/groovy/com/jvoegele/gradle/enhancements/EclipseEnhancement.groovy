package com.jvoegele.gradle.enhancements

import org.gradle.api.Project 
import org.gradle.plugins.eclipse.model.BuildCommand 
import org.gradle.plugins.eclipse.model.Link;

class EclipseEnhancement extends GradlePluginEnhancement {
  public EclipseEnhancement(Project project) {
    super(project)
  }

  public void apply() {
    project.gradle.taskGraph.whenReady { taskGraph ->

      if (!project.plugins.hasPlugin('eclipse'))
        return;

      def androidLibraryProjectSrcPaths = project.ant.references['project.libraries.src']?.list()

      project.configure(project.eclipseProject) {
        beforeConfigured {
          natures 'com.android.ide.eclipse.adt.AndroidNature'
          def builders = new LinkedList(buildCommands)
          builders.addFirst(new BuildCommand('com.android.ide.eclipse.adt.PreCompilerBuilder'))
          builders.addFirst(new BuildCommand('com.android.ide.eclipse.adt.ResourceManagerBuilder'))
          builders.addLast(new BuildCommand('com.android.ide.eclipse.adt.ApkBuilder'))
          buildCommands = new ArrayList(builders)

          if (androidLibraryProjectSrcPaths?.any()) {
            androidLibraryProjectSrcPaths.each {
              link name: "lib1", type: "2", location: it, locationUri: null
            }
          }

        }
      }

      project.configure(project.eclipseClasspath) {

        beforeConfigured {
          containers.removeAll { it == 'org.eclipse.jdt.launching.JRE_CONTAINER' }
          containers 'com.android.ide.eclipse.adt.ANDROID_FRAMEWORK'
          sourceSets = project.sourceSets
          sourceSets.main.java.srcDir 'gen'
          if (androidLibraryProjectSrcPaths?.any()) {
            androidLibraryProjectSrcPaths.each {
              sourceSets.main.java.srcDir it
            }
          }
        }

        whenConfigured { classpath ->
          if (androidLibraryProjectSrcPaths?.any()) {
            excludeAndroidLibraryProjectJars(androidLibraryProjectSrcPaths, classpath)
          }
        }
      }
    }
  }

  private void excludeAndroidLibraryProjectJars(androidLibraryProjectSrcPaths, classpath) {
    def libraryArtifacts = project.configurations.compile.resolvedConfiguration.resolvedArtifacts.findAll { artifact ->
      androidLibraryProjectSrcPaths.any { it ==~ /.*\/${artifact.name}\/src$/ }
    }

    if (libraryArtifacts?.any()) {
      def classpathEntries = new LinkedList(classpath.entries)

      libraryArtifacts.each { artifact ->
        println "Will exclude artifact $artifact.resolvedDependency from classpath, since it seems to be an Android Library project JAR"
        classpathEntries.removeAll { entry -> entry.path == artifact.file.absolutePath }
      }

      classpath.entries = classpathEntries
    }
  }
}
