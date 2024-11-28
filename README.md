# Wrapper Upgrade Gradle Plugin

[![Verify Build](https://github.com/gradle/wrapper-upgrade-gradle-plugin/actions/workflows/build-verification.yml/badge.svg?branch=main)](https://github.com/gradle/wrapper-upgrade-gradle-plugin/actions/workflows/build-verification.yml)
[![Plugin Portal](https://img.shields.io/maven-metadata/v?metadataUrl=https://plugins.gradle.org/m2/org/gradle/wrapper-upgrade-gradle-plugin/maven-metadata.xml&label=Plugin%20Portal&color=blue)](https://plugins.gradle.org/plugin/org.gradle.wrapper-upgrade)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.solutions-team.gradle.com/scans)

The Wrapper Upgrade Gradle Plugin creates tasks to upgrade the Gradle Wrapper for target projects hosted on GitHub.

## Prerequisites

To run the upgrade tasks, you'll need:

* **Java 8 or later.**
* **Git.**  The plugin uses Git commands to commit, create branches, and push changes.

    * **Git author identity:** Make sure your Git author identity is configured. You can set this with:

      ```bash
      git config --global user.name "Your Name"
      git config --global user.email "your.email@example.com"
      ```

      **Using GPG Signing (Optional):** If you use GitHub Actions and want to sign your commits with GPG, you can use the [crazy-max/ghaction-import-gpg](https://github.com/crazy-max/ghaction-import-gpg) action. This action imports your GPG key **and** configures the Git author identity.

      ```yaml
      - name: Import GPG key
        uses: crazy-max/ghaction-import-gpg@cb9bde2e2525e640591a934b1fd28eef1dcaf5e5
        with:
          gpg_private_key: ${{ secrets.MY_GPG_PRIVATE_KEY }}
          passphrase: ${{ secrets.MY_GPG_PASSPHRASE }}
          git_user_signingkey: true
          git_commit_gpgsign: true
          git_config_global: true
      ```

    * **Git credentials:** If you use GitHub Actions, you can configure your Git credentials from a GitHub token with this trick:

      ```yaml
      - name: Set up Git credentials
        env:
          TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: git config --global url."https://unused-username:${TOKEN}@github.com/".insteadOf "https://github.com/"
      ```

## Usage
Apply the plugin to a dedicated project and configure which project needs to be upgraded. Example:

<details open>

<summary>Kotlin DSL</summary>

```build.gradle
plugins {
    id("base")
    id("org.gradle.wrapper-upgrade") version "0.11.1"
}

wrapperUpgrade {
    gradle {
        register("some-gradle-project") {
            repo.set("my-org/some-gradle-project")
            baseBranch.set("release")
        }
        register("some-samples-gradle-project") {
            repo.set("my-org/some-samples-gradle-project")
            dir.set("samples")
        }
    }

    maven {
        register("some-maven-project") {
            repo.set("my-org/some-maven-project")
            baseBranch.set("release")
        }
        register("some-samples-maven-project") {
            repo.set("my-org/some-samples-maven-project")
            baseBranch.set("samples")
        }
    }
}
```

</details>

<details>

<summary>Groovy DSL</summary>

```build.gradle
plugins {
    id 'base'
    id 'org.gradle.wrapper-upgrade' version '0.11.4'
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

</details>

This will create one task per configured project and 2 aggregating tasks: `upgradeGradleWrapperAll` and `upgradeMavenWrapperAll` that will run all the specific tasks.

Running `./gradlew upgradeGradleWrapperXXX` will:
- clone the project XXX in  `build/git-clones`
- run in the cloned project `./gradlew wrapper --gradle-version=<latest_gradle_version>`
- run a second time `./gradlew wrapper --gradle-version=<latest_gradle_version>`
- If changes occurred
  - create a specific branch
  - commit and push the branch
  - create a pull request on GitHub, it requires a GitHub personal access token (PAT), passed with `WRAPPER_UPGRADE_GIT_TOKEN` environment variable.
    This token is used to get the existing PRs on the repo and create one if needed, hence it requires:
      - the `repo` scope for a classic PAT
      - read-write permissions for "Pull requests" on the relevant repos for a fine-grained PAT

Note that a check is done first to make sure the branch does not exist yet. That way you can run `upgradeGradleWrapperAll` and `upgradeMavenWrapperAll` periodically with a cron, CI job... a bit like dependabot does for upgrading libs.

Running `upgradeMavenWrapperXXX` will do the same, executing `./mvnw wrapper:wrapper -Dmaven=<latest_maven_version>` instead.

### Configuration

```
wrapperUpgrade {
    <gradle|maven> {
        name {
            repo = ...
            baseBranch = ...
            dir = ...
            options {
                gitCommitExtraArgs = [...]
                allowPreRelease = true
                labels = ['dependencies']
                recreateClosedPullRequests = true
            }
        }
    }
}
```

| Field                                | description                                                                                                                                                               |
|:-------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                               | A name identifying the upgrade, it can be different from the project name, for example when you need to upgrade multiple gradle projects in the same git project          |
| `repo`                               | The GitHub repository to clone, format 'organization/project`                                                                                                             |
| `dir`                                | The directory inside the project base directory to run the gradle or maven upgrade in                                                                                     |
| `baseBranch`                         | The git branch to checkout and that the pull request will target                                                                                                          |
| `options.gitCommitExtraArgs`         | List of additional git commit arguments                                                                                                                                   |
| `options.allowPreRelease`            | Boolean: `true` will get the latest Maven/Gradle version even if it's a pre-release. Default is `false`.                                                                  |
| `options.labels`                     | Optional list of label (names) that will be added to the pull request.                                                                                                    |
| `options.recreateClosedPullRequests` | Boolean: `true` will recreate the pull request if a closed pull request with the same branch name exists. `false` will not recreate the pull request. Default is `false`. |

## License

The Wrapper Upgrade Gradle Plugin is open-source software released under the [Apache 2.0 License][apache-license].

[apache-license]: https://www.apache.org/licenses/LICENSE-2.0.html
