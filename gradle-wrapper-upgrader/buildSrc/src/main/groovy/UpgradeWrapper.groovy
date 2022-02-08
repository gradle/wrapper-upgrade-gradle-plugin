import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import org.gradle.work.DisableCachingByDefault
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

import javax.inject.Inject

@DisableCachingByDefault(because = 'Produces no cacheable output')
abstract class UpgradeWrapper extends DefaultTask {

    @Inject
    abstract ProviderFactory getProviders()

    @Inject
    abstract ObjectFactory getObjects()

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract ListProperty<Map> getChangeSet()

    @Input
    final abstract Property<String> gradleVersion = objects.property(String).convention(providers.provider({ latestGradleRelease() }))

    @TaskAction
    def upgrade() {
        changeSet.get().each { Map change ->
            def gitDir = project.layout.buildDirectory.dir("gitClones/${change.name}").get()
            try {
                // TODO: Check if PR exists first
                clone(gitDir, change.repo)
                upgradeWrapper(gitDir, change.subfolder, change.noBuild == true)
                def branch = "bot/upgrade-gw-${change.name}-to-${gradleVersion.get()}"
                gitCommit(gitDir, branch)
                createPr(branch, change.baseBranch, change.repo)
            } catch (GradleException e) {
                logger.error("Failed to update ${change}")
            }
        }
    }

    def clone(Directory gitDir, String repo) {
        execOperations.exec { execSpec ->
            def gitUrl = "https://github.com/${repo}.git"
            def args = ['git', 'clone', '--depth', '1', gitUrl, gitDir]
            execSpec.commandLine(args)
        }
    }

    def upgradeWrapper(Directory gitDir, String subfolder, boolean noBuild) {
        def workingDir = subfolder ? gitDir.dir(subfolder) : gitDir
        if (noBuild) {
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

    def gitCommit(Directory gitDir, String branch) {
        def changes = project.fileTree(dir: gitDir, includes: ['**/gradle/wrapper/**', '**/gradlew', '**/gradlew.bat'])
        def message = "Upgrade Gradle wrapper to ${gradleVersion.get()}"
        if (checkChanges(gitDir)) {
            changes.each { change ->
                execOperations.exec { execSpec ->
                    def args = ['git', 'add', "${change.toPath()}"]
                    execSpec.workingDir(gitDir)
                    execSpec.commandLine(args)
                }
            }
            execOperations.exec { execSpec ->
                def args = ['git', 'checkout', '-b', branch]
                execSpec.workingDir(gitDir)
                execSpec.commandLine(args)
            }
            execOperations.exec { execSpec ->
                def args = ['git', 'commit', '-m', message]
                execSpec.workingDir(gitDir)
                execSpec.commandLine(args)
            }
            execOperations.exec { execSpec ->
                def args = ['git', 'push', '-u', 'origin', branch]
                execSpec.workingDir(gitDir)
                execSpec.commandLine(args)
            }
        } else {
            logger.warn("No changes detected on ${changeSet}.")
        }
    }

    def checkChanges(Directory gitDir) {
        try {
            execOperations.exec { execSpec ->
                def args = ['git', 'diff', '--quiet', '--exit-code']
                execSpec.workingDir(gitDir)
                execSpec.commandLine(args)
            }
        } catch (ExecException e) {
            return true
        }
        return false
    }

    static String latestGradleRelease() {
        def versionJson = new JsonSlurper().parseText(new URL("https://services.gradle.org/versions/current").text)
        if (versionJson.empty) {
            throw new GradleException("Cannot determine latest Gradle release")
        }
        return versionJson.version
    }

    def createPr(String branch, String baseBranch, String repoName) {
        GitHub github = new GitHubBuilder().withOAuthToken(System.getenv('CCUD_GIT_TOKEN')).build()
        def pr = github.getRepository(repoName).createPullRequest(
            "Upgrading Gradle Wrapper to ${gradleVersion.get()}", branch, baseBranch ?: 'main', null)
        logger.lifecycle("Pull request created ${pr.htmlUrl}")
    }

}
