import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = 'Produces no cacheable output')
abstract class UpgradeWrapper extends DefaultTask {

    @Inject
    abstract ObjectFactory getObjects()

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract DirectoryProperty getGradleProject()

    @Input
    @Optional
    abstract Property<String> getSubfolder()

    @Input
    @Optional
    final abstract Property<Boolean> noBuild = objects.property(Boolean).convention(false)

    @Input
    abstract Property<String> getGradleVersion()

    @TaskAction
    def upgradeWrapper() {
        def workingDir = subfolder.isPresent() ? gradleProject.get().dir(subfolder.get()) : gradleProject.get()
        if (noBuild.get()) {
            ant.replaceregexp(match: 'distributions/gradle-(.*)-bin.zip', replace: "distributions/gradle-${gradleVersion.get()}-bin.zip") {
                fileset(dir: workingDir, includes: '**/gradle-wrapper.properties')
            }
        } else {
            execOperations.exec { execSpec ->
                def args = ['./gradlew', 'wrapper', '--gradle-version', gradleVersion.get()]
                execSpec.workingDir(workingDir)
                execSpec.commandLine(args)
            }
            execOperations.exec { execSpec ->
                def args = ['./gradlew', 'wrapper', '--gradle-version', gradleVersion.get()]
                execSpec.workingDir(workingDir)
                execSpec.commandLine(args)
            }
        }
    }
}
