import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = 'Produces no cacheable output')
abstract class GitClone extends DefaultTask {

    @Inject
    abstract ObjectFactory getObjects()

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract Property<String> getGitUrl()

    @OutputDirectory
    final abstract DirectoryProperty outputDir = objects.directoryProperty().convention(project.layout.buildDirectory.dir('git-clone'))

    @TaskAction
    def clone() {
        execOperations.exec { execSpec ->
            def args = ['git', 'clone', '--depth', '1', gitUrl.get(), outputDir.get()]
            execSpec.commandLine(args)
        }
    }
}
