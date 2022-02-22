package com.gradle.upgrade.wrapper;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

public abstract class UpgradeWrapperDomainObject {

    final String name;

    public final Property<String> repo;

    @Optional
    public final Property<String> dir;

    @Optional
    public final Property<String> baseBranch;

    @Inject
    public UpgradeWrapperDomainObject(String name, ObjectFactory objects) {
        this.name = name;
        this.repo = objects.property(String.class);
        this.dir = objects.property(String.class).convention(".");
        this.baseBranch = objects.property(String.class).convention("main");
    }

    public Property<String> getRepo() {
        return repo;
    }

    public Property<String> getDir() {
        return dir;
    }

    public Property<String> getBaseBranch() {
        return baseBranch;
    }

}
