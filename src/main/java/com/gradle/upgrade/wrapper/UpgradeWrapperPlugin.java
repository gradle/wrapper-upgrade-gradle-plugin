package com.gradle.upgrade.wrapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class UpgradeWrapperPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var objects = project.getObjects();
        var upgradeContainer =
            objects.domainObjectContainer(UpgradeWrapperDomainObject.class, name -> objects.newInstance(UpgradeWrapperDomainObject.class, name));
        project.getExtensions().add("wrapperUpgrades", upgradeContainer);

        var upgradeAllTask = project.getTasks().register("upgradeWrapperAll");

        upgradeContainer.configureEach(upgrade -> {
            var taskNameSuffix = upgrade.name.substring(0, 1).toUpperCase() + upgrade.name.substring(1);
            var upgradeTask = project.getTasks().register("upgradeWrapper" + taskNameSuffix,
                UpgradeWrapper.class, upgrade);
            upgradeAllTask.configure(task -> task.dependsOn(upgradeTask));
        });
    }

}
