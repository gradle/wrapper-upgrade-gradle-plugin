package com.gradle.upgrade.wrapper;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

public abstract class UpgradeWrapperDomainObject {

    String name;

    abstract Property<String> getRepo();

    @Optional
    Property<String> dir;

    @Optional
    Property<String> baseBranch;

    @Inject
    public UpgradeWrapperDomainObject(final String name, ObjectFactory objects) {
        this.name = name;
        this.dir = objects.property(String.class).convention(".");
        this.baseBranch = objects.property(String.class).convention("main");
    }

    public Property<String> getBaseBranch() {
        return baseBranch;
    }

    public Property<String> getDir() {
        return dir;
    }

}
