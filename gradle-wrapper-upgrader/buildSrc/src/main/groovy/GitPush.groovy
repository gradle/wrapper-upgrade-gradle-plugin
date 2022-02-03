import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = 'Produces no cacheable output')
abstract class GitPush extends DefaultTask {

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract DirectoryProperty getGitDir()

    @Input
    abstract Property<FileCollection> getChanges()

    @Input
    abstract Property<String> getMessage()

    @TaskAction
    def push() {
        changes.get().each { change ->
            execOperations.exec { execSpec ->
                def args = ['git', 'add', "${change.toPath()}"]
                execSpec.workingDir(gitDir.get())
                execSpec.commandLine(args)
            }
        }
        execOperations.exec { execSpec ->
                def args = ['git', 'commit', '-m', message.get()]
                execSpec.workingDir(gitDir.get())
                execSpec.commandLine(args)
        }        
        execOperations.exec { execSpec ->
                def args = ['git', 'push']
                execSpec.workingDir(gitDir.get())
                execSpec.commandLine(args)
        }
    }
}
