package com.gradle.upgrade.wrapper;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class UpgradeWrapperExtension {

    private final NamedDomainObjectContainer<UpgradeWrapperDomainObject> gradle;
    private final NamedDomainObjectContainer<UpgradeWrapperDomainObject> maven;

    @Inject
    public UpgradeWrapperExtension(ObjectFactory objects) {
        this.gradle = objects.domainObjectContainer(UpgradeWrapperDomainObject.class, name -> objects.newInstance(UpgradeWrapperDomainObject.class, name));
        this.maven = objects.domainObjectContainer(UpgradeWrapperDomainObject.class, name -> objects.newInstance(UpgradeWrapperDomainObject.class, name));
    }

    public NamedDomainObjectContainer<UpgradeWrapperDomainObject> getGradle() {
        return gradle;
    }

    public NamedDomainObjectContainer<UpgradeWrapperDomainObject> getMaven() {
        return maven;
    }

}
