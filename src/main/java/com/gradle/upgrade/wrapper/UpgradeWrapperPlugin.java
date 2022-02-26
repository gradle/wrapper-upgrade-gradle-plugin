package com.gradle.upgrade.wrapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("unused")
public abstract class UpgradeWrapperPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        UpgradeWrapperExtension wrapperUpgrades = project.getExtensions().create("wrapperUpgrades", UpgradeWrapperExtension.class);

        var upgradeAllTask = project.getTasks().register("upgradeWrapperAll",
            t -> {
                t.setGroup("Gradle Wrapper Upgrade");
                t.setDescription("Updates the Gradle Wrapper on all configured projects.");
            });

        wrapperUpgrades.getGradle().configureEach(upgrade -> {
            var taskNameSuffix = upgrade.name.substring(0, 1).toUpperCase() + upgrade.name.substring(1);
            var upgradeTask = project.getTasks().register("upgradeWrapper" + taskNameSuffix, UpgradeWrapper.class, upgrade, BuildToolStrategy.GRADLE);
            upgradeAllTask.configure(task -> task.dependsOn(upgradeTask));
        });
    }

}
