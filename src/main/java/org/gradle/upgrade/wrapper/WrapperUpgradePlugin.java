package org.gradle.upgrade.wrapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("unused")
public abstract class WrapperUpgradePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        WrapperUpgradeExtension wrapperUpgrades = project.getExtensions().create("wrapperUpgrade", WrapperUpgradeExtension.class);

        var upgradeGradleWrapperAllTask = project.getTasks().register("upgradeGradleWrapperAll",
            t -> {
                t.setGroup("Wrapper Upgrades");
                t.setDescription("Updates the Gradle Wrapper on all configured projects.");
            });

        wrapperUpgrades.getGradle().configureEach(upgrade -> {
            var taskNameSuffix = upgrade.name.substring(0, 1).toUpperCase() + upgrade.name.substring(1);
            var upgradeTask = project.getTasks().register("upgradeGradleWrapper" + taskNameSuffix, UpgradeWrapper.class, upgrade, BuildToolStrategy.GRADLE);
            upgradeGradleWrapperAllTask.configure(task -> task.dependsOn(upgradeTask));
        });

        var upgradeMavenWrapperAllTask = project.getTasks().register("upgradeMavenWrapperAll",
            t -> {
                t.setGroup("Wrapper Upgrades");
                t.setDescription("Updates the Maven Wrapper on all configured projects.");
            });

        wrapperUpgrades.getMaven().configureEach(upgrade -> {
            var taskNameSuffix = upgrade.name.substring(0, 1).toUpperCase() + upgrade.name.substring(1);
            var upgradeTask = project.getTasks().register("upgradeMavenWrapper" + taskNameSuffix, UpgradeWrapper.class, upgrade, BuildToolStrategy.MAVEN);
            upgradeMavenWrapperAllTask.configure(task -> task.dependsOn(upgradeTask));
        });
    }

}
