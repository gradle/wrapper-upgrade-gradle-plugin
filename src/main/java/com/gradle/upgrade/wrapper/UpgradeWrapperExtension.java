package com.gradle.upgrade.wrapper;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public abstract class UpgradeWrapperExtension {

    private final NamedDomainObjectContainer<WrapperUpgradeDomainObject> gradle;
    private final NamedDomainObjectContainer<WrapperUpgradeDomainObject> maven;

    @Inject
    public UpgradeWrapperExtension(ObjectFactory objects) {
        this.gradle = objects.domainObjectContainer(WrapperUpgradeDomainObject.class, name -> objects.newInstance(WrapperUpgradeDomainObject.class, name));
        this.maven = objects.domainObjectContainer(WrapperUpgradeDomainObject.class, name -> objects.newInstance(WrapperUpgradeDomainObject.class, name));
    }

    public NamedDomainObjectContainer<WrapperUpgradeDomainObject> getGradle() {
        return gradle;
    }

    public NamedDomainObjectContainer<WrapperUpgradeDomainObject> getMaven() {
        return maven;
    }

}
