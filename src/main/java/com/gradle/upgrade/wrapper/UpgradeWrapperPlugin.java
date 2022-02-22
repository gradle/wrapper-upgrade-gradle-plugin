package com.gradle.upgrade.wrapper;

import groovy.transform.CompileStatic;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

@CompileStatic
public class UpgradeWrapperPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var objects = project.getObjects();
        var upgradeContainer =
            objects.domainObjectContainer(Upgrade.class, name -> objects.newInstance(Upgrade.class, name));
        project.getExtensions().add("wrapperUpgrades", upgradeContainer);

        var upgradeAllTask = project.getTasks().register("upgradeWrapperAll");

        upgradeContainer.configureEach(upgrade -> {
            var taskNameSuffix = upgrade.name.substring(0, 1).toUpperCase() + upgrade.name.substring(1);
            var upgradeTask = project.getTasks().register("upgrade" + taskNameSuffix, UpgradeWrapper.class, task -> task.getUpgrade().set(upgrade));
            upgradeAllTask.configure(task -> task.dependsOn(upgradeTask));
        });
    }

    public abstract static class Upgrade {

        String name;

        abstract Property<String> getRepo();

        @Optional
        Property<String> subfolder;

        @Optional
        Property<String> baseBranch;

        @Optional
        Property<Boolean> noBuild;

        @Inject
        public Upgrade(final String name, ObjectFactory objects) {
            this.name = name;
            this.subfolder = objects.property(String.class).convention(".");
            this.baseBranch = objects.property(String.class).convention("main");
            this.noBuild = objects.property(Boolean.class).convention(false);
        }

        public Property<Boolean> getNoBuild() {
            return noBuild;
        }

        public Property<String> getBaseBranch() {
            return baseBranch;
        }

        public Property<String> getSubfolder() {
            return subfolder;
        }

    }

}




