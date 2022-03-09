# Wrapper Upgrade Gradle Plugin

[![Verify Build](https://github.com/gradle/wrapper-upgrade-gradle-plugin/actions/workflows/build-verification.yml/badge.svg?branch=main)](https://github.com/gradle/wrapper-upgrade-gradle-plugin/actions/workflows/build-verification.yml)
[![Plugin Portal](https://img.shields.io/maven-metadata/v?metadataUrl=https://plugins.gradle.org/m2/com/gradle/gradle/wrapper-upgrade-gradle-plugin/maven-metadata.xml&label=Plugin%20Portal)](https://plugins.gradle.org/plugin/com.gradle.gradle/wrapper-upgrade-gradle-plugin)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.solutions-team.gradle.com/scans)


The Wrapper Upgrade Gradle Plugin create tasks to upgrade Gradle wrappers for target projects hosted on Github.

## Usage 
You would apply the plugin to a dedicated project and configure which project needs to be upgraded. 
Example:

```build.gradle
plugins {
    id 'base'
    id 'org.gradle.wrapper-upgrade' version '1.0.0'
}

wrapperUpgrade {
    gradle {
        'some-gradle-project' {
            repo = 'my-org/some-gradle-project'
            baseBranch = 'release'
        }
        'some-samples-gradle-project' {
            repo = 'my-org/some-samples-gradle-project'
            dir = 'samples'
        }
    }

    maven {
        'some-maven-project' {
            repo = 'my-org/some-maven-project'
            baseBranch = 'release'
        }
        'some-samples-maven-project' {
            repo = 'my-org/some-samples-maven-project'
            dir = 'samples'
        }
    }
}
```

This will create one task per configured project and 2 aggregating tasks: `upgradeGradleWrapperAll` and `upgradeMavenWrapperAll` that will run all the specific tasks.

Running `/gradlew upgradeGradleWrapperXXX` will:
- clone the project XXX in  `build/git-clones`
- run in the cloned project `./gradlew wrapper --gradle-version=<latest_gradle_version>`
- run a second time `./gradlew wrapper --gradle-version=<latest_gradle_version>`
- If changes occured
  - create a specific branch
  - commit and push the branch
  - create a pull request on Github, it requires a Github access token, passed with `WRAPPER_UPGRADE_GIT_TOKEN` environment variable.
  
Note that a check is done first to make sure the branch does not exist yet. That way you can run `upgradeGradleWrapperAll` and `upgradeMavenWrapperAll` periodically with a cron, CI job... a bit like dependabot does for upgrading libs.

Running `upgradeMavenWrapperXXX` will do the same, executing `./mvnw wrapper:wrapper -Dmaven=<latest_maven_version>` instead.


### Task configuration

```
wrapperUpgrade {
    <gradle|maven> {
        name {
            repo = ...
            baseBranch = ...
            dir = ...
        }
    }
}
```

| Field        | description                                                                                                                                                      |
| :----------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `name`       | A name identifying the upgrade, it can be different from the project name, for example when you need to upgrade multiple gradle projects in the same git project |
| `repo`       | The Github repository to clone, format 'organization/project`                                                                                                    |
| `dir`        | The directory inside the project base directory to run the gradle or maven upgrade in                                                                            |
| `baseBranch` | The git branch to checkout and that the pull request will target                                                                                                 |


