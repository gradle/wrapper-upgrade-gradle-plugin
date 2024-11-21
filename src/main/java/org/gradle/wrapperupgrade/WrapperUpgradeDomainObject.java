package org.gradle.wrapperupgrade;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class WrapperUpgradeDomainObject {

    final String name;
    private final Property<String> repo;
    private final Property<String> dir;
    private final Property<String> baseBranch;
    private final Options options;

    @Inject
    public WrapperUpgradeDomainObject(String name, ObjectFactory objects) {
        this.name = name;
        this.repo = objects.property(String.class);
        this.dir = objects.property(String.class).convention(".");
        this.baseBranch = objects.property(String.class).convention("main");
        this.options = objects.newInstance(Options.class);
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

    public Options getOptions() {
        return options;
    }

    /**
     * Called by Gradle at runtime when configuring this object with the `options { }` closure.
     */
    public void options(Action<Options> action) {
        action.execute(options);
    }

    public static class Options {

        private final ListProperty<String> gitCommitExtraArgs;
        private final Property<Boolean> allowPreRelease;
        private final ListProperty<String> labels;

        @Inject
        public Options(ObjectFactory objects) {
            this.gitCommitExtraArgs = objects.listProperty(String.class);
            this.allowPreRelease = objects.property(Boolean.class);
            this.labels = objects.listProperty(String.class);
        }

        public ListProperty<String> getGitCommitExtraArgs() {
            return gitCommitExtraArgs;
        }

        public Property<Boolean> getAllowPreRelease() {
            return allowPreRelease;
        }

         public ListProperty<String> getLabels() {
            return labels;
        }
    }

}
